package com.pickbit.authservice.infrastructure.redis;

import com.pickbit.authservice.security.oauth.OAuthLinkContext;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OAuthLinkCodeRepository {

    private static final String KEY_PREFIX = "oauth:link:";

    private final RedissonClient redissonClient;

    public void save(String code, OAuthLinkContext context, Duration ttl) {
        bucket(code).set(context, ttl);
    }

    public Optional<OAuthLinkContext> find(String code) {
        return Optional.ofNullable(bucket(code).get());
    }

    public Optional<OAuthLinkContext> consume(String code) {
        RBucket<OAuthLinkContext> bucket = bucket(code);
        OAuthLinkContext context = bucket.get();
        if (context != null) {
            bucket.delete();
        }
        return Optional.ofNullable(context);
    }

    private RBucket<OAuthLinkContext> bucket(String code) {
        return redissonClient.getBucket(KEY_PREFIX + code);
    }
}
