package com.hhplus.ecommerce.infrastructure.cache;

import com.hhplus.ecommerce.infrastructure.redis.RedisKeyGenerator;
import org.springframework.stereotype.Component;

/**
 * Cache 키 제공자 (SpEL에서 사용)
 *
 * @CacheEvict, @CachePut 등에서 SpEL로 간단하게 캐시 키를 생성할 수 있도록 도와주는 Bean입니다.
 *
 * 사용법:
 * <pre>
 * @CacheEvict(value = "ecommerce", key = "@cacheKeys.userKey(#userId)")
 * @CacheEvict(value = "ecommerce", key = "@cacheKeys.productKey(#productId)")
 * </pre>
 *
 * 장점:
 * - SpEL에서 짧고 명확하게 사용 가능
 * - RedisKeyGenerator를 통한 중앙 집중식 키 관리 유지
 * - 전체 패키지명(FQN) 불필요
 */
@Component("cacheKeys")
public class CacheKeyProvider {

    /**
     * 사용자 캐시 키 (ID 기반)
     */
    public String userKey(Long userId) {
        return RedisKeyGenerator.userCacheKey(userId);
    }

    /**
     * 사용자 캐시 키 (PublicId 기반)
     */
    public String userKeyByPublicId(String publicId) {
        return RedisKeyGenerator.userCacheKeyByPublicId(publicId);
    }

    /**
     * 상품 캐시 키
     */
    public String productKey(Long productId) {
        return RedisKeyGenerator.productCacheKey(productId);
    }

    /**
     * 쿠폰 캐시 키
     */
    public String couponKey(Long couponId) {
        return RedisKeyGenerator.couponCacheKey(couponId);
    }

    /**
     * 발급 가능한 쿠폰 목록 캐시 키
     */
    public String issuableCouponsKey() {
        return RedisKeyGenerator.issuableCouponsCacheKey();
    }

    /**
     * 인기 상품 목록 캐시 키
     */
    public String topProductsKey(Integer limit) {
        return RedisKeyGenerator.topProductsCacheKey(limit);
    }
}
