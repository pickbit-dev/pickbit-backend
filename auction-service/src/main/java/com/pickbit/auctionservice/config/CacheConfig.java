package com.pickbit.auctionservice.config;

import com.pickbit.auctionservice.api.dto.response.AuctionDetailResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String AUCTION_CACHE = "auctions";

    @Bean
    public RedisCacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            @Value("${auction.cache.auction-ttl-seconds:10}") long auctionTtlSeconds) {

        ObjectMapper cacheMapper = JsonMapper.builder()
                .findAndAddModules()
                .build();

        RedisCacheConfiguration baseConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()))
                .disableCachingNullValues()
                .prefixCacheNameWith("auction:cache:");

        JacksonJsonRedisSerializer<AuctionDetailResponse> auctionSerializer =
                new JacksonJsonRedisSerializer<>(cacheMapper, AuctionDetailResponse.class);

        RedisCacheConfiguration auctionCacheConfig = baseConfig
                .entryTtl(Duration.ofSeconds(auctionTtlSeconds))
                .serializeValuesWith(SerializationPair.fromSerializer(auctionSerializer));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(baseConfig)
                .withInitialCacheConfigurations(Map.of(AUCTION_CACHE, auctionCacheConfig))
                .build();
    }
}
