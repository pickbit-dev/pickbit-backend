#!/usr/bin/env bash
#
# 카테고리별 시드 이미지를 Wikimedia Commons 에서 받아온다.
#
# 라이선스가 이 스크립트의 핵심 제약이다. 받은 이미지를 공개 서비스(pickbit.co.kr)에
# 올릴 것이므로 haslicense:unrestricted 로 PD/CC0 만 받는다. CC BY 계열을 섞으면
# 상품 상세 화면마다 저작자를 표기해야 해서 쓸 수 없다.
#
#   사용법:  ./scripts/fetch-seed-images.sh [카테고리당_장수] [출력_디렉터리]
#   기본값:  250장, C:/pickbit-seed-images
#
# 재개 가능하다. 이미 받은 파일은 건너뛰므로 중간에 끊겨도 다시 돌리면 이어진다.
set -uo pipefail

PER_CATEGORY="${1:-250}"
OUT_DIR="${2:-C:/pickbit-seed-images}"
MANIFEST="$OUT_DIR/manifest.csv"

API="https://commons.wikimedia.org/w/api.php"
# User-Agent 는 Wikimedia 정책상 필수다. 없으면 차단당한다.
UA="pickbit-seed/1.0 (https://pickbit.co.kr) curl"
# 요청 간 간격. 직렬로 받으면서 이 정도는 지켜준다.
SLEEP_SEARCH=1
SLEEP_DOWNLOAD=0.2

# 카테고리 | Commons 검색어(영문)
#
# 따옴표 구문을 쓰지 않는다 — 단어만 쓴다. 인용구가 하나라도 섞이면 OR 전체가 0건이
# 되는 것을 확인했다. 예: (dog OR cat OR aquarium) 은 6262건인데
# (dog OR cat OR aquarium OR "pet food") 는 0건이다. 위치를 바꿔도 마찬가지다.
#
# 아래 검색어는 전부 PD/CC0 한정으로 250장 이상 나오는 것을 실측했다(최소 반려동물 345).
read -r -d '' CATEGORIES <<'EOF'
01-전자기기|refrigerator OR television OR microwave OR appliance OR oven
02-컴퓨터-노트북|laptop OR computer OR keyboard OR monitor
03-휴대폰-태블릿|smartphone OR telephone OR tablet
04-카메라-렌즈|camera OR lens OR photography
05-음향기기|headphones OR loudspeaker OR amplifier OR earphones
06-게임-취미|chess OR puzzle OR toy OR figurine OR boardgame OR dice
07-패션의류|shirt OR jacket OR dress OR trousers
08-패션잡화|handbag OR shoes OR wallet OR hat
09-시계-주얼리|wristwatch OR jewellery OR necklace OR ring
10-명품|handbag OR luggage OR suitcase OR briefcase
11-뷰티-미용|cosmetics OR perfume OR lipstick OR soap
12-스포츠-레저|bicycle OR dumbbell OR tent OR ball OR skateboard
13-자동차-오토바이|car OR motorcycle OR scooter OR wheel
14-가구-인테리어|furniture OR chair OR sofa OR lamp OR table
15-생활-주방|cookware OR teapot OR tableware OR cup OR pottery
16-유아동-출산|stroller OR toy OR doll OR pram
17-반려동물|dog OR cat OR aquarium OR birdcage
18-도서-음반|book OR magazine OR gramophone OR phonograph
19-티켓-쿠폰|ticket OR voucher OR banknote OR poster
20-수집품-아트|coin OR painting OR sculpture OR postcard
EOF

mkdir -p "$OUT_DIR"
if [ ! -f "$MANIFEST" ]; then
  echo "category,filename,commons_page,license,author,width,height" > "$MANIFEST"
fi

# Commons API 호출. --data-urlencode 로 넘겨야 한다. 직접 인코딩하면
# 따옴표가 깨져서 멀쩡한 검색이 0건으로 나온다.
api() {
  local retry
  for retry in 1 2 3; do
    if curl -s --max-time 40 -G -H "User-Agent: $UA" "$API" "$@"; then
      return 0
    fi
    sleep $((retry * 3))
  done
  return 1
}

# JSON 에서 값 뽑기. jq 가 없는 환경이라 문자열 처리로 간다.
# 여러 줄로 쪼개 놓고 필요한 필드만 집는다.
extract_titles() {
  tr ',' '\n' | grep -o '"title":"[^"]*"' | sed 's/^"title":"//;s/"$//' | grep '^File:'
}

json_unescape() {
  sed 's/\\u[0-9a-fA-F]\{4\}//g; s/\\"/'"'"'/g; s/\\\\/\//g; s/\\n/ /g; s/\\t/ /g'
}

# HTML 태그를 걷어내고 CSV 에 안전한 한 줄로 만든다 (Artist 필드가 HTML 로 온다).
clean_field() {
  sed 's/<[^>]*>//g' | json_unescape | tr -d '\r\n' | tr ',' ' ' | tr -s ' ' | cut -c1-120
}

total_downloaded=0
total_skipped=0

while IFS='|' read -r slug query; do
  [ -z "${slug:-}" ] && continue
  dir="$OUT_DIR/$slug"
  mkdir -p "$dir"

  have=$(find "$dir" -type f 2>/dev/null | wc -l | tr -d ' ')
  if [ "$have" -ge "$PER_CATEGORY" ]; then
    echo "[$slug] 이미 ${have}장 -> 건너뜀"
    total_skipped=$((total_skipped + have))
    continue
  fi

  echo "[$slug] 목표 ${PER_CATEGORY}장 (현재 ${have}장) :: $query"

  offset=0
  got="$have"
  # 검색 결과 중 상당수는 이미 받았거나 다운로드에 실패할 수 있으니 넉넉히 돈다.
  while [ "$got" -lt "$PER_CATEGORY" ] && [ "$offset" -lt 2000 ]; do
    titles=$(api \
      --data-urlencode "action=query" \
      --data-urlencode "list=search" \
      --data-urlencode "srsearch=filetype:bitmap ($query) haslicense:unrestricted" \
      --data-urlencode "srnamespace=6" \
      --data-urlencode "srlimit=100" \
      --data-urlencode "sroffset=$offset" \
      --data-urlencode "format=json" | extract_titles)

    if [ -z "$titles" ]; then
      echo "  (검색 결과 없음, offset=$offset -> 이 카테고리 종료)"
      break
    fi

    while IFS= read -r title; do
      [ "$got" -ge "$PER_CATEGORY" ] && break
      [ -z "$title" ] && continue

      # 파일명을 안전하게: File: 접두어 제거, 공백/특수문자 정리
      base=$(printf '%s' "${title#File:}" | tr ' ' '_' | tr -cd 'A-Za-z0-9._-')
      [ -z "$base" ] && continue
      dest="$dir/$base"

      if [ -f "$dest" ]; then
        continue
      fi

      info=$(api \
        --data-urlencode "action=query" \
        --data-urlencode "titles=$title" \
        --data-urlencode "prop=imageinfo" \
        --data-urlencode "iiprop=url|extmetadata|size" \
        --data-urlencode "iiurlwidth=1024" \
        --data-urlencode "format=json")

      # 원본이 아니라 1024px 썸네일을 받는다. 원본에는 수십 MB 짜리가 섞여 있다.
      url=$(printf '%s' "$info" | tr ',' '\n' | grep -o '"thumburl":"[^"]*"' | head -1 | sed 's/^"thumburl":"//;s/"$//;s/\\\//\//g')
      [ -z "$url" ] && continue

      license=$(printf '%s' "$info" | grep -o '"LicenseShortName":{"value":"[^"]*"' | head -1 | sed 's/.*"value":"//;s/"$//' | clean_field)

      # haslicense:unrestricted 를 믿지 않는다. 스모크 테스트에서 CC BY-SA 4.0 한 장이
      # 그 필터를 뚫고 들어왔다. 배포 사이트에 올릴 이미지라 표기 의무가 있는 라이선스가
      # 한 장이라도 섞이면 안 되므로 받기 전에 여기서 한 번 더 거른다.
      case "$(printf '%s' "$license" | tr 'A-Z' 'a-z')" in
        *"public domain"*|cc0*|*"no restrictions"*|pd-*) ;;
        *) continue ;;
      esac

      author=$(printf '%s' "$info" | grep -o '"Artist":{"value":"[^"]*"' | head -1 | sed 's/.*"value":"//;s/"$//' | clean_field)
      width=$(printf '%s' "$info" | grep -o '"thumbwidth":[0-9]*' | head -1 | cut -d: -f2)
      height=$(printf '%s' "$info" | grep -o '"thumbheight":[0-9]*' | head -1 | cut -d: -f2)

      code=$(curl -s --max-time 60 -H "User-Agent: $UA" "$url" -o "$dest" -w '%{http_code}')
      if [ "$code" != "200" ] || [ ! -s "$dest" ]; then
        rm -f "$dest"
        continue
      fi

      page="https://commons.wikimedia.org/wiki/$(printf '%s' "$title" | tr ' ' '_')"
      printf '%s,%s,%s,%s,%s,%s,%s\n' \
        "$slug" "$base" "$page" "${license:-unknown}" "${author:-unknown}" "${width:-0}" "${height:-0}" >> "$MANIFEST"

      got=$((got + 1))
      total_downloaded=$((total_downloaded + 1))
      if [ $((got % 25)) -eq 0 ]; then
        echo "  ... $got/$PER_CATEGORY"
      fi
      sleep "$SLEEP_DOWNLOAD"
    done <<< "$titles"

    offset=$((offset + 100))
    sleep "$SLEEP_SEARCH"
  done

  echo "[$slug] 완료: ${got}장"
done <<< "$CATEGORIES"

echo
echo "=========================================="
echo "새로 받음: ${total_downloaded}장 (기존 건너뜀: ${total_skipped}장)"
echo "출력: $OUT_DIR"
echo "=========================================="
