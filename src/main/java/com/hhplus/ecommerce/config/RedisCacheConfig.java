package com.hhplus.ecommerce.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis Cache 설정
 *
 * Spring Cache Abstraction을 사용하여 Redis 기반 캐싱을 제공합니다.
 * - @Cacheable: 캐시에서 조회, 없으면 메서드 실행 후 캐시에 저장
 * - @CachePut: 항상 메서드 실행 후 캐시 업데이트
 * - @CacheEvict: 캐시에서 삭제
 *
 * 엔티티별 TTL 전략:
 * - products: 30분 (상품 정보는 변경 빈도가 낮음)
 * - coupons: 10분 (쿠폰 재고는 실시간 반영 필요)
 * - issuableCoupons: 5분 (발급 가능 쿠폰 목록은 자주 갱신)
 * - users: 5분 (잔액 등 사용자 정보는 자주 변경됨)
 * - topProducts: 60분 (인기 상품은 장시간 캐싱 가능)
 */
@Configuration
@EnableCaching
public class RedisCacheConfig {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // ObjectMapper 설정 (LocalDateTime 직렬화 지원)
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Redis Cache 기본 설정
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))  // 기본 TTL 10분
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())
                )
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer(objectMapper)
                        )
                );

        // 엔티티별 TTL 설정
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // 상품: 30분 (상품 정보는 자주 변경되지 않음)
        cacheConfigurations.put("products", defaultConfig.entryTtl(Duration.ofMinutes(30)));

        // 쿠폰: 10분 (재고 정보 반영 필요)
        cacheConfigurations.put("coupons", defaultConfig.entryTtl(Duration.ofMinutes(10)));

        // 발급 가능 쿠폰 목록: 5분 (자주 갱신 필요)
        cacheConfigurations.put("issuableCoupons", defaultConfig.entryTtl(Duration.ofMinutes(5)));

        // 사용자: 5분 (잔액 등 자주 변경됨)
        cacheConfigurations.put("users", defaultConfig.entryTtl(Duration.ofMinutes(5)));

        // 인기 상품: 60분 (통계성 데이터, 장시간 캐싱)
        cacheConfigurations.put("topProducts", defaultConfig.entryTtl(Duration.ofMinutes(60)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }
}
