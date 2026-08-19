# Pexels 검색 응답 한 페이지를 한 번에 훑어 TSV 로 뽑는다.
#
# 사진마다 grep/sed 를 띄우던 버전은 Windows Git Bash 에서 프로세스 생성 비용이
# 병목이 됐다(10분에 388장). 페이지당 awk 한 번이면 그 비용이 사라진다.
#
# 출력: id \t large_url \t photographer \t photographer_url \t page_url \t alt
# URL 의 \u0026 와 \/ 는 호출 측에서 sed 한 번으로 푼다.

function get(rec, key,   pat, i, s, q) {
  # "key":"value" 를 정확히 집는다. 앞의 따옴표까지 포함해서 찾으므로
  # "url" 을 찾을 때 "photographer_url" 에 걸리지 않고,
  # "large" 를 찾을 때 "large2x" 에 걸리지 않는다.
  pat = "\"" key "\":\""
  i = index(rec, pat)
  if (i == 0) return ""
  s = substr(rec, i + length(pat))
  q = index(s, "\"")
  if (q == 0) return ""
  return substr(s, 1, q - 1)
}

function clean(v) { gsub(/,/, " ", v); gsub(/\t/, " ", v); return v }

BEGIN { RS = "[{]\"id\":" }

NR > 1 {
  id = $0
  sub(/[^0-9].*$/, "", id)
  if (id == "") next
  large = get($0, "large")
  if (large == "") next
  printf "%s\t%s\t%s\t%s\t%s\t%s\n", id, large,
    clean(get($0, "photographer")), clean(get($0, "photographer_url")),
    clean(get($0, "url")), clean(get($0, "alt"))
}
