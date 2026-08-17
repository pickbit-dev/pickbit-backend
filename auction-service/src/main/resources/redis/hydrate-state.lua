-- 경매 상태를 "없을 때만" 만들어 넣는다.
--
-- 예전에는 HSET(putAll) 로 무조건 덮어썼다. hydrate 는 Lua 중재가 NOT_LOADED 를 돌려줬을 때
-- 호출되는데, 동시에 두 입찰이 NOT_LOADED 를 받으면 둘 다 hydrate 를 부른다. 그 사이에 한쪽이
-- 이미 입찰을 반영했다면 나중 hydrate 가 currentPriceMinor 와 seq 를 DB 값으로 되돌려버린다.
-- 가격이 되감기면 이미 지나간 금액으로 다시 입찰할 수 있고, seq 가 되감기면 순번이 중복돼
-- 클라이언트의 누락 이벤트 복구(afterEventId)가 어긋난다.
--
-- 키가 이미 있으면 그쪽이 더 최신이므로 아무것도 하지 않는다.
--
-- KEYS[1] = auction:state:{auctionId}
-- ARGV    = field1, value1, field2, value2, ...
--
-- 반환: 1 = 새로 만듦, 0 = 이미 있어서 건드리지 않음

if redis.call('EXISTS', KEYS[1]) == 1 then
  return 0
end

redis.call('HSET', KEYS[1], unpack(ARGV))
return 1
