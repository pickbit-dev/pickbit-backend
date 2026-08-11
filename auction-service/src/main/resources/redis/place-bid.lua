-- 입찰 검증과 현재가 갱신을 한 번의 왕복으로 원자적으로 처리한다.
--
-- 예전에는 Redisson 분산락을 DB 트랜잭션 전체 동안 붙들고 있어서 한 경매의 입찰이
-- 완전히 직렬화됐다. 여기서는 Redis 안에서 비교·교체가 끝나고 DB 기록은 스트림으로 넘긴다.
--
-- 금액은 전부 minor unit(원 * 100) 정수로 다룬다. Lua 의 double 연산으로 인한
-- 금액 오차를 원천적으로 없애기 위함이다.
--
-- KEYS[1] = auction:state:{auctionId}
-- KEYS[2] = auction:bid:stream
-- ARGV[1] = bidderUserId
-- ARGV[2] = bidderNickname
-- ARGV[3] = amountMinor
-- ARGV[4] = nowEpochMs
-- ARGV[5] = maxStreamLen
-- ARGV[6] = auctionId
--
-- 반환: { accepted, reason, minimumRequiredMinor, seq, ended, endedSeq }

if redis.call('EXISTS', KEYS[1]) == 0 then
  -- 상태가 아직 로드되지 않았다. 호출자가 DB에서 복구한 뒤 재시도한다.
  return { '0', 'NOT_LOADED', '0', '0', '0', '0' }
end

local state = redis.call('HMGET', KEYS[1],
  'status', 'currentPriceMinor', 'minIncrementMinor', 'startingPriceMinor',
  'buyNowPriceMinor', 'endTimeEpochMs', 'sellerUserId', 'hasBid')

local status = state[1]
if status ~= 'ACTIVE' then
  return { '0', 'NOT_ACTIVE', '0', '0', '0', '0' }
end

local endTime = tonumber(state[6])
local now = tonumber(ARGV[4])
if endTime ~= nil and now >= endTime then
  return { '0', 'TIME_OVER', '0', '0', '0', '0' }
end

if state[7] == ARGV[1] then
  return { '0', 'SELLER', '0', '0', '0', '0' }
end

local amount = tonumber(ARGV[3])
local minimum
if state[8] == '1' then
  minimum = tonumber(state[2]) + tonumber(state[3])
else
  minimum = tonumber(state[4])
end

if amount < minimum then
  return { '0', 'TOO_LOW', tostring(minimum), '0', '0' }
end

-- 여기서부터는 수락이 확정됐다.
local seq = redis.call('HINCRBY', KEYS[1], 'seq', 1)
redis.call('HSET', KEYS[1], 'currentPriceMinor', amount, 'hasBid', '1')

-- 즉시 구매가에 도달하면 같은 원자 구간에서 경매를 닫아 추가 입찰을 막는다.
-- 종료 이벤트는 입찰 이벤트와 별개의 순번을 받아야 한다. 같은 순번을 공유하면
-- 클라이언트의 누락 이벤트 복구(afterEventId)가 둘 중 하나를 건너뛴다.
local ended = '0'
local endedSeq = 0
local buyNow = tonumber(state[5])
if buyNow ~= nil and buyNow > 0 and amount >= buyNow then
  redis.call('HSET', KEYS[1], 'status', 'ENDED')
  ended = '1'
  endedSeq = redis.call('HINCRBY', KEYS[1], 'seq', 1)
end

redis.call('XADD', KEYS[2], 'MAXLEN', '~', ARGV[5], '*',
  'auctionId', ARGV[6],
  'bidderUserId', ARGV[1],
  'bidderNickname', ARGV[2],
  'amountMinor', ARGV[3],
  'seq', tostring(seq),
  'bidTimeEpochMs', ARGV[4],
  'ended', ended,
  'endedSeq', tostring(endedSeq))

return { '1', 'OK', '0', tostring(seq), ended, tostring(endedSeq) }
