package com.hhplus.ecommerce.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hhplus.ecommerce.config.properties.CacheProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

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
 *
 * Redis 장애 대응:
 * - Redis 장애 시 캐시 동작을 무시하고 DB로 Fallback
 * - 서비스 중단 없이 계속 동작 (성능 저하만 발생)
 */
@Slf4j
@Configuration
@EnableCaching
@RequiredArgsConstructor
@EnableConfigurationProperties(CacheProperties.class)
public class RedisCacheConfig implements CachingConfigurer {

    private final CacheProperties cacheProperties;

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(cacheProperties.getDefaultTtl())
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())
                )
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer(objectMapper)
                        )
                );

        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        cacheConfigurations.put("products", defaultConfig.entryTtl(cacheProperties.getProductsTtl()));
        cacheConfigurations.put("coupons", defaultConfig.entryTtl(cacheProperties.getCouponsTtl()));
        cacheConfigurations.put("issuableCoupons", defaultConfig.entryTtl(cacheProperties.getIssuableCouponsTtl()));
        cacheConfigurations.put("users", defaultConfig.entryTtl(cacheProperties.getUsersTtl()));
        cacheConfigurations.put("topProducts", defaultConfig.entryTtl(cacheProperties.getTopProductsTtl()));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }

    /**
     * Redis 장애 시 에러 처리
     *
     * Redis 장애가 발생해도 서비스는 계속 동작하도록 에러를 무시합니다.
     * - GET 실패: 캐시를 건너뛰고 메서드 실행 (DB 조회)
     * - PUT 실패: 캐시 저장 실패를 무시하고 계속 진행
     * - EVICT 실패: 캐시 삭제 실패를 무시 (다음 TTL 만료 시 자동 삭제)
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                // Redis 조회 실패 → 로그만 남기고 메서드 실행 (DB Fallback)
                log.warn("Cache GET failed (fallback to DB): cache={}, key={}, error={}",
                        cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                // Redis 저장 실패 → 무시하고 계속 (다음 요청 시 다시 시도)
                log.warn("Cache PUT failed (ignored): cache={}, key={}, error={}",
                        cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                // Redis 삭제 실패 → 무시 (TTL로 자동 만료됨)
                log.warn("Cache EVICT failed (ignored): cache={}, key={}, error={}",
                        cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                // Redis 전체 삭제 실패 → 무시
                log.warn("Cache CLEAR failed (ignored): cache={}, error={}",
                        cache.getName(), exception.getMessage());
            }
        };
    }
}
