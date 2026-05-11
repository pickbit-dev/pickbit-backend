package com.pickbit.authservice.infrastructure.redis;

import com.pickbit.authservice.api.dto.response.TokenResponse;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OAuthExchangeCodeRepository {

    private static final String KEY_PREFIX = "oauth:exchange:";

    private final RedissonClient redissonClient;

    public void save(String code, TokenResponse tokenResponse, Duration ttl) {
        bucket(code).set(tokenResponse, ttl);
    }

    public Optional<TokenResponse> consume(String code) {
        RBucket<TokenResponse> bucket = bucket(code);
        TokenResponse tokenResponse = bucket.get();
        if (tokenResponse != null) {
            bucket.delete();
        }
        return Optional.ofNullable(tokenResponse);
    }

    private RBucket<TokenResponse> bucket(String code) {
        return redissonClient.getBucket(KEY_PREFIX + code);
    }
}
