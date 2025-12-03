package com.hhplus.ecommerce.infrastructure.cache;

import com.hhplus.ecommerce.infrastructure.redis.RedisKeyGenerator;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * Redis Cache 키 생성기 (중앙 집중식)
 *
 * 모든 @Cacheable, @CacheEvict, @CachePut에서 사용되며,
 * RedisKeyGenerator를 통해 통일된 키 형식을 보장합니다.
 *
 * 사용법:
 * <pre>
 * @Cacheable(value = "users:id", keyGenerator = "cacheKeyGenerator")
 * public User getUser(Long userId) { ... }
 *
 * @Cacheable(value = "users:publicId", keyGenerator = "cacheKeyGenerator")
 * public User getUserByPublicId(String publicId) { ... }
 * </pre>
 *
 * value 형식: {domain}:{keyType}
 * - users:id → RedisKeyGenerator.userCacheKey(userId)
 * - users:publicId → RedisKeyGenerator.userCacheKeyByPublicId(publicId)
 * - products:id → RedisKeyGenerator.productCacheKey(productId)
 * - coupons:id → RedisKeyGenerator.couponCacheKey(couponId)
 *
 * 장점:
 * 1. 완전한 중앙 집중: 모든 캐시 키가 RedisKeyGenerator를 거침
 * 2. 변경 용이: 키 형식 변경 시 RedisKeyGenerator만 수정
 * 3. 추적 가능: 어떤 캐시 키든 RedisKeyGenerator에서 확인 가능
 * 4. Lock과 통일: Lock 키도 RedisKeyGenerator 사용
 */
@Component("cacheKeyGenerator")
public class CacheKeyGenerator implements KeyGenerator {

    @Override
    public Object generate(Object target, Method method, Object... params) {
        String methodName = method.getName();

        // 파라미터가 없는 경우 (예: getIssuableCoupons())
        if (params.length == 0) {
            if (methodName.contains("Issuable")) {
                return RedisKeyGenerator.issuableCouponsCacheKey();
            }
            throw new IllegalArgumentException(
                    String.format("캐시 키 생성 실패: 파라미터가 없는 메서드를 지원하지 않습니다. method=%s", methodName)
            );
        }

        // 첫 번째 파라미터가 캐시 키의 주요 값
        Object keyParam = params[0];
        Class<?> paramType = keyParam.getClass();

        // User 도메인
        if (methodName.contains("User")) {
            if (keyParam instanceof Long) {
                return RedisKeyGenerator.userCacheKey((Long) keyParam);
            } else if (keyParam instanceof String) {
                return RedisKeyGenerator.userCacheKeyByPublicId((String) keyParam);
            }
        }

        // Product 도메인
        if (methodName.contains("Product")) {
            if (keyParam instanceof Long) {
                return RedisKeyGenerator.productCacheKey((Long) keyParam);
            }
        }

        // Coupon 도메인
        if (methodName.contains("Coupon")) {
            if (keyParam instanceof Long) {
                return RedisKeyGenerator.couponCacheKey((Long) keyParam);
            }
        }

        // TopProducts (limit 기반)
        if (methodName.contains("TopSelling")) {
            if (keyParam instanceof Integer) {
                return RedisKeyGenerator.topProductsCacheKey((Integer) keyParam);
            }
        }

        // 기본 처리: 메서드명 + 파라미터
        throw new IllegalArgumentException(
                String.format("캐시 키 생성 실패: 지원하지 않는 메서드입니다. method=%s, paramType=%s",
                        methodName, paramType.getSimpleName())
        );
    }
}
