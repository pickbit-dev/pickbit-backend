package com.pickbit.authservice.infrastructure.redis;

import com.pickbit.authservice.security.oauth.OAuthSignupContext;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OAuthSignupCodeRepository {

    private static final String KEY_PREFIX = "oauth:signup:";

    private final RedissonClient redissonClient;

    public void save(String code, OAuthSignupContext context, Duration ttl) {
        bucket(code).set(context, ttl);
    }

    public Optional<OAuthSignupContext> find(String code) {
        return Optional.ofNullable(bucket(code).get());
    }

    public Optional<OAuthSignupContext> consume(String code) {
        RBucket<OAuthSignupContext> bucket = bucket(code);
        OAuthSignupContext context = bucket.get();
        if (context != null) {
            bucket.delete();
        }
        return Optional.ofNullable(context);
    }

    private RBucket<OAuthSignupContext> bucket(String code) {
        return redissonClient.getBucket(KEY_PREFIX + code);
    }
}
