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

    /** 일회용 코드를 원자적으로 소비합니다. get 후 delete 로 나누면 동시 요청이 둘 다 통과합니다. */
    public Optional<OAuthSignupContext> consume(String code) {
        return Optional.ofNullable(bucket(code).getAndDelete());
    }

    private RBucket<OAuthSignupContext> bucket(String code) {
        return redissonClient.getBucket(KEY_PREFIX + code);
    }
}
