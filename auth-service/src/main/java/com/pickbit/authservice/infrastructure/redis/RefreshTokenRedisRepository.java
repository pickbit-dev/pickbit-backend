package com.pickbit.authservice.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.api.bucket.CompareAndSetArgs;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRedisRepository {

    private static final String KEY_PREFIX = "refresh:";

    private final RedissonClient redissonClient;

    public void save(Long accountId, String refreshToken, Duration ttl) {
        bucket(accountId).set(refreshToken, ttl);
    }

    public Optional<String> findByAccountId(Long accountId) {
        return Optional.ofNullable(bucket(accountId).get());
    }

    /**
     * 저장된 토큰이 {@code expected} 와 같을 때만 {@code replacement} 로 교체합니다.
     *
     * @return 교체했으면 {@code true}. 저장된 값이 다르거나 없으면 {@code false}
     *
     * <p>검사와 교체가 한 연산이어야 합니다. 예전에는 조회로 확인한 뒤 따로 저장했는데,
     * 같은 refresh token 으로 동시에 두 요청이 들어오면 둘 다 검사를 통과해
     * 토큰이 사실상 재사용 가능했습니다.
     */
    public boolean rotate(Long accountId, String expected, String replacement, Duration ttl) {
        return bucket(accountId).compareAndSet(
                CompareAndSetArgs.<String>expected(expected)
                        .set(replacement)
                        .timeToLive(ttl));
    }

    public void delete(Long accountId) {
        bucket(accountId).delete();
    }

    private RBucket<String> bucket(Long accountId) {
        return redissonClient.getBucket(KEY_PREFIX + accountId);
    }
}
