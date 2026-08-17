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

    /** 일회용 코드를 원자적으로 소비합니다. get 후 delete 로 나누면 동시 요청이 둘 다 통과합니다. */
    public Optional<OAuthLinkContext> consume(String code) {
        return Optional.ofNullable(bucket(code).getAndDelete());
    }

    private RBucket<OAuthLinkContext> bucket(String code) {
        return redissonClient.getBucket(KEY_PREFIX + code);
    }
}
