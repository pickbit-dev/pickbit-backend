#!/usr/bin/env bash
#
# 카테고리별 시드 이미지를 Pexels 에서 받아온다.
#
# Commons 판(fetch-seed-images.sh)과 목적은 같지만 사진 성격이 다르다.
# Commons 는 백과사전이라 기록사진이 오고, Pexels 는 제품·라이프스타일 화보가 온다.
# 상품 목록 화면에 쓸 거라면 이쪽이 낫다.
#
#   사용법:  PEXELS_API_KEY=xxx ./scripts/fetch-seed-images-pexels.sh [장수] [출력_디렉터리]
#   기본값:  250장, C:/pickbit-seed-images-pexels
#
# 재개 가능하다. 이미 받은 파일은 건너뛴다.
#
# ── 출처 표기 의무 ──────────────────────────────────────────────
# Pexels API 가이드라인은 수동 다운로드와 조건이 다르다. API 로 받은 사진을 쓰려면
#   - 사이트에 Pexels 로 가는 링크를 눈에 띄게 둘 것
#   - 가능하면 사진마다 작가명을 표기할 것 ("Photo by OOO on Pexels")
# 이 필요하다. 그래서 manifest.csv 에 photographer 와 원본 페이지 URL 을 남긴다.
# 상품 화면을 만들 때 이 값을 써야 한다.
# ────────────────────────────────────────────────────────────────
set -uo pipefail

: "${PEXELS_API_KEY:?PEXELS_API_KEY 환경변수가 필요합니다. https://www.pexels.com/api/ 에서 발급}"

PER_CATEGORY="${1:-250}"
OUT_DIR="${2:-C:/pickbit-seed-images-pexels}"
MANIFEST="$OUT_DIR/manifest.csv"
PER_PAGE=80            # Pexels 최대치
PARALLEL=6             # CDN 동시 다운로드. API 가 아니라 이미지 서버라 리밋과 무관하다.

# 카테고리 | Pexels 검색어
# Pexels 는 영문 태그 기반이라 한글로 검색하면 거의 안 나온다.
read -r -d '' CATEGORIES <<'EOF'
01-전자기기|home appliances
02-컴퓨터-노트북|laptop computer
03-휴대폰-태블릿|smartphone
04-카메라-렌즈|camera
05-음향기기|headphones
06-게임-취미|video game console
07-패션의류|clothing fashion
08-패션잡화|handbag shoes
09-시계-주얼리|watch jewelry
10-명품|luxury bag
11-뷰티-미용|cosmetics
12-스포츠-레저|sports equipment
13-자동차-오토바이|car motorcycle
14-가구-인테리어|furniture
15-생활-주방|kitchenware
16-유아동-출산|baby stroller toys
17-반려동물|pet supplies
18-도서-음반|books vinyl records
19-티켓-쿠폰|concert ticket
20-수집품-아트|art collectibles
EOF

mkdir -p "$OUT_DIR"
[ -f "$MANIFEST" ] || echo "category,filename,photographer,photographer_url,pexels_page,alt" > "$MANIFEST"

# 한 줄에 사진 하나씩 오도록 JSON 을 쪼갠다. 중첩 JSON 을 grep 으로 다루면서
# id/작가/URL 을 짝지으려면 이렇게 경계를 먼저 만들어야 어긋나지 않는다.
split_photos() { sed 's/{"id":/\n{"id":/g' | grep '^{"id":'; }

field() { grep -o "\"$1\":\"[^\"]*\"" | head -1 | sed "s/^\"$1\":\"//;s/\"$//"; }

csv_safe() { tr -d '\r\n' | tr ',' ' ' | sed 's/\\u[0-9a-fA-F]\{4\}//g; s/\\\//\//g' | tr -s ' ' | cut -c1-100; }

total=0
declare -a SHORT=()

while IFS='|' read -r slug query; do
  [ -z "${slug:-}" ] && continue
  dir="$OUT_DIR/$slug"
  mkdir -p "$dir"

  have=$(find "$dir" -type f 2>/dev/null | wc -l | tr -d ' ')
  if [ "$have" -ge "$PER_CATEGORY" ]; then
    echo "[$slug] 이미 ${have}장 -> 건너뜀"
    continue
  fi

  echo "[$slug] 목표 ${PER_CATEGORY}장 (현재 ${have}장) :: $query"

  joblist="$dir/.download-jobs"
  : > "$joblist"
  queued=0
  page=1

  while [ $((have + queued)) -lt "$PER_CATEGORY" ] && [ "$page" -le 25 ]; do
    body=$(curl -s --max-time 40 -H "Authorization: $PEXELS_API_KEY" \
      "https://api.pexels.com/v1/search?query=$(printf '%s' "$query" | sed 's/ /%20/g')&per_page=$PER_PAGE&page=$page")

    lines=$(printf '%s' "$body" | split_photos)
    [ -z "$lines" ] && { echo "  (더 이상 결과 없음, page=$page)"; break; }

    while IFS= read -r photo; do
      [ $((have + queued)) -ge "$PER_CATEGORY" ] && break
      id=$(printf '%s' "$photo" | grep -o '^{"id":[0-9]*' | cut -d: -f2)
      [ -z "$id" ] && continue

      dest="$dir/pexels-${id}.jpg"
      [ -f "$dest" ] && continue

      # src 블록의 large 를 쓴다. original 은 수 MB 짜리라 목록 이미지로 과하다.
      url=$(printf '%s' "$photo" | grep -o '"large":"[^"]*"' | head -1 | sed 's/^"large":"//;s/"$//;s/\\u0026/\&/g;s/\\\//\//g')
      [ -z "$url" ] && continue

      photographer=$(printf '%s' "$photo" | field photographer | csv_safe)
      purl=$(printf '%s' "$photo" | field photographer_url | csv_safe)
      page_url=$(printf '%s' "$photo" | field url | csv_safe)
      alt=$(printf '%s' "$photo" | field alt | csv_safe)

      printf '%s\t%s\n' "$url" "$dest" >> "$joblist"
      printf '%s,%s,%s,%s,%s,%s\n' \
        "$slug" "pexels-${id}.jpg" "${photographer:-unknown}" "${purl:-}" "${page_url:-}" "${alt:-}" >> "$MANIFEST"
      queued=$((queued + 1))
    done <<< "$lines"

    page=$((page + 1))
  done

  # CDN 다운로드는 병렬로. API 레이트 리밋(시간당 요청)과는 별개다.
  if [ "$queued" -gt 0 ]; then
    tr '\t' '\n' < "$joblist" | paste - - | while IFS=$'\t' read -r u d; do
      printf '%s\t%s\n' "$u" "$d"
    done | xargs -P "$PARALLEL" -I{} bash -c '
      IFS=$'"'"'\t'"'"' read -r url dest <<< "{}"
      curl -s --max-time 60 -o "$dest" "$url" || rm -f "$dest"
      [ -s "$dest" ] || rm -f "$dest"
    ' 2>/dev/null
  fi
  rm -f "$joblist"

  got=$(find "$dir" -type f | wc -l | tr -d ' ')
  total=$((total + got))
  echo "[$slug] 완료: ${got}장"
  [ "$got" -lt "$PER_CATEGORY" ] && SHORT+=("$slug($got)")
done <<< "$CATEGORIES"

echo
echo "=========================================="
echo "총 ${total}장  ->  $OUT_DIR"
[ ${#SHORT[@]} -gt 0 ] && echo "목표 미달: ${SHORT[*]}"
echo "출처 표기: manifest.csv 의 photographer / pexels_page 를 화면에 써야 합니다."
echo "=========================================="
