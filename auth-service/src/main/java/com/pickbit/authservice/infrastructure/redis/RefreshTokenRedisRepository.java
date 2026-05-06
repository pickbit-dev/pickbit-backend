package com.pickbit.authservice.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
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

    public boolean existsAndMatches(Long accountId, String refreshToken) {
        return findByAccountId(accountId).filter(refreshToken::equals).isPresent();
    }

    public void delete(Long accountId) {
        bucket(accountId).delete();
    }

    private RBucket<String> bucket(Long accountId) {
        return redissonClient.getBucket(KEY_PREFIX + accountId);
    }
}
