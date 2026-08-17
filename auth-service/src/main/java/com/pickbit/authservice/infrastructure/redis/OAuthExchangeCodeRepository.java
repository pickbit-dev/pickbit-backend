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

    /**
     * 코드를 소비합니다. 일회용이므로 동시에 두 번 들어와도 한쪽만 값을 받아야 합니다.
     *
     * <p>{@code get()} 후 {@code delete()} 로 나누면 두 요청이 같은 토큰을 함께 가져갈 수 있습니다.
     * 이 코드는 프론트 리다이렉트 URL 을 타고 흐르므로 원자적인 {@code getAndDelete()} 를 씁니다.
     */
    public Optional<TokenResponse> consume(String code) {
        return Optional.ofNullable(bucket(code).getAndDelete());
    }

    private RBucket<TokenResponse> bucket(String code) {
        return redissonClient.getBucket(KEY_PREFIX + code);
    }
}
