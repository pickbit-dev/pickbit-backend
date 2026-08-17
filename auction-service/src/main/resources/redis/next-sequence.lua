-- 경매 상태가 살아 있을 때만 순번을 발급한다.
--
-- 예전에는 호출자가 read() 로 상태 존재를 확인한 뒤 따로 HINCRBY 를 보냈다. 그 사이에 Redis 가
-- 재시작되면 HINCRBY 가 없는 키를 새로 만들면서 seq=1 을 발급하는데, DB 에는 이미 더 큰 순번이
-- 기록돼 있어 순번이 충돌한다. 확인과 증가를 한 연산으로 묶어 그 틈을 없앤다.
--
-- KEYS[1] = auction:state:{auctionId}
--
-- 반환: 발급된 순번, 또는 상태가 없으면 -1 (호출자가 DB 순번으로 폴백한다)

if redis.call('EXISTS', KEYS[1]) == 0 then
  return -1
end

return redis.call('HINCRBY', KEYS[1], 'seq', 1)
