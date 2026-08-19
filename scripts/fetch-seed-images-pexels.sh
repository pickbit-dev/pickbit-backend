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
# Commons 쪽 PD/CC0 에는 이 의무가 없다.
# ────────────────────────────────────────────────────────────────
set -uo pipefail

: "${PEXELS_API_KEY:?PEXELS_API_KEY 환경변수가 필요합니다. https://www.pexels.com/api/ 에서 발급}"

PER_CATEGORY="${1:-250}"
OUT_DIR="${2:-C:/pickbit-seed-images-pexels}"
MANIFEST="$OUT_DIR/manifest.csv"
AWK="$(dirname "$0")/pexels-parse.awk"
PER_PAGE=80            # Pexels 최대치
PARALLEL=8             # CDN 동시 다운로드. API 레이트 리밋(시간당 요청)과는 별개다.

# 디렉터리명(ASCII) | 카테고리(한글) | Pexels 검색어
#
# 디렉터리명이 ASCII 인 이유: Windows curl 은 -K 설정 파일로 넘긴 한글 경로에
# 파일을 못 쓴다(설정 파일은 UTF-8 인데 ANSI 코드페이지로 해석해서 exit 23).
# 병렬 다운로드에 설정 파일이 필요하므로 경로를 ASCII 로 맞춘다.
# 한글 카테고리명은 매니페스트에 남긴다.
#
# Pexels 는 영문 태그 기반이라 한글로 검색하면 거의 안 나온다.
read -r -d '' CATEGORIES <<'EOF'
01-electronics|전자기기|home appliances
02-computers|컴퓨터/노트북|laptop computer
03-mobile|휴대폰/태블릿|smartphone
04-cameras|카메라/렌즈|camera
05-audio|음향기기|headphones
06-games-hobby|게임/취미|video game console
07-clothing|패션의류|clothing fashion
08-accessories|패션잡화|handbag shoes
09-watches-jewelry|시계/주얼리|watch jewelry
10-luxury|명품|luxury bag
11-beauty|뷰티/미용|cosmetics
12-sports|스포츠/레저|sports equipment
13-vehicles|자동차/오토바이|car motorcycle
14-furniture|가구/인테리어|furniture
15-kitchen|생활/주방|kitchenware
16-baby-kids|유아동/출산|baby stroller toys
17-pets|반려동물|pet supplies
18-books-music|도서/음반|books vinyl records
19-tickets|티켓/쿠폰|concert ticket
20-collectibles|수집품/아트|art collectibles
EOF

mkdir -p "$OUT_DIR"
[ -f "$MANIFEST" ] || echo "dir,category_ko,filename,photographer,photographer_url,pexels_page,alt" > "$MANIFEST"

total=0
SHORT=""

while IFS='|' read -r slug ko query; do
  [ -z "${slug:-}" ] && continue
  dir="$OUT_DIR/$slug"
  mkdir -p "$dir"

  have=$(find "$dir" -type f -name '*.jpg' 2>/dev/null | wc -l | tr -d ' ')
  if [ "$have" -ge "$PER_CATEGORY" ]; then
    echo "[$slug] 이미 ${have}장 -> 건너뜀"
    total=$((total + have))
    continue
  fi

  echo "[$slug] 목표 ${PER_CATEGORY}장 (현재 ${have}장) :: $query"

  joblist="$dir/.download-jobs"
  : > "$joblist"
  queued=0
  page=1

  while [ $((have + queued)) -lt "$PER_CATEGORY" ] && [ "$page" -le 25 ]; do
    # 페이지 하나를 받아 awk 로 한 번에 파싱한다. 사진마다 grep 을 띄우던 버전은
    # Windows 에서 프로세스 생성이 병목이라 10분에 388장까지 떨어졌다.
    rows=$(curl -s --max-time 40 -H "Authorization: $PEXELS_API_KEY" \
      "https://api.pexels.com/v1/search?query=$(printf '%s' "$query" | sed 's/ /%20/g')&per_page=$PER_PAGE&page=$page" \
      | awk -f "$AWK" | sed 's/[\]u0026/\x26/g; s|[\]/|/|g')

    if [ -z "$rows" ]; then
      echo "  (더 이상 결과 없음, page=$page)"
      break
    fi

    # 이 루프 안에서는 외부 명령을 부르지 않는다. 순수 bash 문자열 처리만 쓴다.
    while IFS=$'\t' read -r id url photographer purl page_url alt; do
      [ $((have + queued)) -ge "$PER_CATEGORY" ] && break
      [ -z "$id" ] && continue
      dest="$dir/pexels-${id}.jpg"
      [ -f "$dest" ] && continue

      # curl 설정 파일 형식으로 쌓는다. URL 에 & ? = 가 들어 있어서 셸을 거쳐
      # 넘기면 인용 문제가 난다. curl 이 파일을 직접 읽으면 그 문제가 없다.
      printf 'url = "%s"\noutput = "%s"\n' "$url" "$dest" >> "$joblist"
      printf '%s,%s,%s,%s,%s,%s,%s\n' \
        "$slug" "$ko" "pexels-${id}.jpg" "${photographer:-unknown}" "$purl" "$page_url" "$alt" >> "$MANIFEST"
      queued=$((queued + 1))
    done <<< "$rows"

    page=$((page + 1))
  done

  if [ "$queued" -gt 0 ]; then
    curl -s --parallel --parallel-max "$PARALLEL" --max-time 120 -K "$joblist"
    find "$dir" -type f -name '*.jpg' -size 0 -delete 2>/dev/null
  fi
  rm -f "$joblist"

  got=$(find "$dir" -type f -name '*.jpg' | wc -l | tr -d ' ')
  total=$((total + got))
  echo "[$slug] 완료: ${got}장"
  [ "$got" -lt "$PER_CATEGORY" ] && SHORT="$SHORT $slug($got)"
done <<< "$CATEGORIES"

echo
echo "=========================================="
echo "총 ${total}장  ->  $OUT_DIR"
[ -n "$SHORT" ] && echo "목표 미달:$SHORT"
echo "출처 표기: manifest.csv 의 photographer / pexels_page 를 화면에 써야 합니다."
echo "=========================================="

# 매니페스트 정리 — 다운로드가 실패한 행이 남아 있으면 지운다.
# 매니페스트는 큐에 넣는 시점에 쓰므로 실제 파일보다 많을 수 있다.
# (실패분은 스크립트를 한 번 더 돌리면 채워진다. have 가 실제 파일 수를 다시 세기 때문이다.)
tmp="$MANIFEST.tmp"
head -1 "$MANIFEST" > "$tmp"
tail -n +2 "$MANIFEST" | sort -u -t, -k1,1 -k3,3 | while IFS=, read -r d ko fn rest; do
  [ -f "$OUT_DIR/$d/$fn" ] && printf '%s,%s,%s,%s\n' "$d" "$ko" "$fn" "$rest"
done >> "$tmp"
mv "$tmp" "$MANIFEST"
echo "매니페스트 정리: $(( $(wc -l < "$MANIFEST") - 1 ))행 (실제 파일과 일치)"
