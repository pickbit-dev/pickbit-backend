-- 모아둔 조회수를 통째로 꺼내고 같은 원자 구간에서 비운다.
--
-- 예전에는 HGETALL 후 HDEL 로 나뉘어 있었다. 그 사이에 들어온 HINCRBY 는 뒤이은 HDEL 이
-- 필드를 통째로 지우면서 함께 사라졌다 — 다음 주기로 넘어간 게 아니라 유실이었다.
--
-- KEYS[1] = product:viewcount:pending
--
-- 반환: HGETALL 형태의 평탄한 배열 (field1, value1, field2, value2, ...)

local values = redis.call('HGETALL', KEYS[1])
if #values > 0 then
  redis.call('DEL', KEYS[1])
end
return values
