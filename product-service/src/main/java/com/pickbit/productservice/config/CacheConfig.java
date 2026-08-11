package com.pickbit.productservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import com.pickbit.productservice.api.dto.response.CategoryResponse;
import com.pickbit.productservice.api.dto.response.ProductDetailResponse;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 조회 캐시 설정입니다.
 *
 * <p>상품 조회는 전체 트래픽에서 가장 큰 비중을 차지하므로 DB를 아예 치지 않게 만드는 것이
 * 처리량에 가장 크게 기여합니다. TTL 은 짧게 잡고, 변경이 일어나면 즉시 무효화합니다.
 */
/*
 * 캐시 어드바이저를 트랜잭션 어드바이저보다 바깥에 둔다.
 *
 * 기본값은 둘 다 LOWEST_PRECEDENCE 라 순서가 보장되지 않는데, 트랜잭션이 바깥에 걸리면
 * 캐시가 적중해도 읽기 전용 트랜잭션이 열리면서 DB 커넥션을 잡는다. 캐싱의 목적이
 * "DB를 아예 치지 않는 것"이므로 커넥션을 잡는 순간 의미가 절반으로 줄어든다.
 */
@Configuration
@EnableCaching(order = Ordered.HIGHEST_PRECEDENCE)
public class CacheConfig {

    /** 상품 상세. 변경 시 무효화되므로 TTL 을 조금 길게 잡아도 된다. */
    public static final String PRODUCT_DETAIL_CACHE = "product-detail";

    /** 카테고리 목록. 거의 바뀌지 않는다. */
    public static final String CATEGORY_CACHE = "categories";

    @Bean
    public RedisCacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            @Value("${product.cache.detail-ttl-seconds:30}") long detailTtlSeconds,
            @Value("${product.cache.category-ttl-seconds:300}") long categoryTtlSeconds) {

        // findAndAddModules() 가 없으면 LocalDateTime 직렬화가 실패한다.
        // 응답 DTO 에 시각 필드가 있어 캐시 저장 시점에 바로 터진다.
        ObjectMapper cacheMapper = JsonMapper.builder()
                .findAndAddModules()
                .build();

        RedisCacheConfiguration baseConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()))
                // null 을 캐싱하면 존재하지 않는 ID 로 캐시를 채울 수 있다.
                .disableCachingNullValues()
                .prefixCacheNameWith("product:cache:");

        RedisCacheConfiguration detailConfig = baseConfig
                .entryTtl(Duration.ofSeconds(detailTtlSeconds))
                .serializeValuesWith(SerializationPair.fromSerializer(
                        new JacksonJsonRedisSerializer<>(cacheMapper, ProductDetailResponse.class)));

        RedisCacheConfiguration categoryConfig = baseConfig
                .entryTtl(Duration.ofSeconds(categoryTtlSeconds))
                .serializeValuesWith(SerializationPair.fromSerializer(
                        new JacksonJsonRedisSerializer<>(cacheMapper,
                                cacheMapper.getTypeFactory()
                                        .constructCollectionType(List.class, CategoryResponse.class))));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(baseConfig.entryTtl(Duration.ofSeconds(detailTtlSeconds)))
                .withInitialCacheConfigurations(Map.of(
                        PRODUCT_DETAIL_CACHE, detailConfig,
                        CATEGORY_CACHE, categoryConfig))
                .build();
    }
}
