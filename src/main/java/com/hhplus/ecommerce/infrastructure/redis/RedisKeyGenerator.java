package com.hhplus.ecommerce.infrastructure.redis;

/**
 * Redis 키 생성 유틸리티 클래스
 *
 * 계층적 네임스페이스 구조를 사용하여 키의 목적과 출처를 명확히 합니다.
 * 형식: {domain}:{usecase}:{action}:{type}:{resource}
 *
 * 예시:
 * - coupon:issueCoupon:direct:lock:123       (쿠폰 즉시 발급 락)
 * - order:placeOrder:stock:lock:456          (주문 시 재고 차감 락)
 * - cache:products:789                        (상품 캐시)
 *
 * 장점:
 * 1. 추적 가능: KEYS coupon:issueCoupon:* 로 특정 usecase의 모든 락 조회
 * 2. 패턴 삭제: KEYS order:* 로 주문 관련 모든 키 삭제
 * 3. 모니터링: 어떤 usecase가 락을 많이 사용하는지 분석 가능
 * 4. 명확성: 키만 봐도 어떤 비즈니스 행위가 생성한 건지 알 수 있음
 */
public class RedisKeyGenerator {

    // ===== Lock 키 생성 =====

    /**
     * Lock 키 생성 (범용)
     *
     * @param domain 도메인 (예: coupon, product, order)
     * @param usecase UseCase 이름 (예: issueCoupon, placeOrder)
     * @param action 액션 (예: direct, stock, batch)
     * @param resource 리소스 ID
     * @return Redis Lock 키
     */
    public static String lockKey(String domain, String usecase, String action, String resource) {
        return String.format("%s:%s:%s:lock:%s", domain, usecase, action, resource);
    }

    // ===== Coupon Domain =====

    /**
     * 쿠폰 즉시 발급 락 키
     *
     * @param couponId 쿠폰 ID
     * @return coupon:issueCoupon:direct:lock:{couponId}
     */
    public static String couponIssueLock(Long couponId) {
        return lockKey("coupon", "issueCoupon", "direct", String.valueOf(couponId));
    }

    /**
     * 쿠폰 대기열 진입 락 키
     *
     * @param couponId 쿠폰 ID
     * @return coupon:joinQueue:lock:{couponId}
     */
    public static String couponQueueJoinLock(Long couponId) {
        return lockKey("coupon", "joinQueue", "lock", String.valueOf(couponId));
    }

    /**
     * 쿠폰 대기열 개별 처리 락 키
     *
     * @param couponId 쿠폰 ID
     * @return coupon:processQueue:item:lock:{couponId}
     */
    public static String couponQueueItemLock(Long couponId) {
        return lockKey("coupon", "processQueue", "item", String.valueOf(couponId));
    }

    /**
     * 쿠폰 대기열 배치 처리 락 키
     *
     * @param couponId 쿠폰 ID
     * @return coupon:processQueue:batch:lock:{couponId}
     */
    public static String couponQueueBatchLock(Long couponId) {
        return lockKey("coupon", "processQueue", "batch", String.valueOf(couponId));
    }

    /**
     * 쿠폰 만료 배치 락 키
     *
     * @return coupon:expireCoupons:batch:lock:global
     */
    public static String couponExpireBatchLock() {
        return lockKey("coupon", "expireCoupons", "batch", "global");
    }

    /**
     * 쿠폰 대기열 순번 업데이트 배치 락 키
     *
     * @return coupon:updateQueuePositions:batch:lock:global
     */
    public static String couponQueueUpdatePositionsLock() {
        return lockKey("coupon", "updateQueuePositions", "batch", "global");
    }

    // ===== Product Domain =====

    /**
     * 상품 재고 차감 락 키 (주문 시)
     *
     * @param productId 상품 ID
     * @return order:placeOrder:stock:lock:{productId}
     */
    public static String productStockDecreaseLock(Long productId) {
        return lockKey("order", "placeOrder", "stock", String.valueOf(productId));
    }

    /**
     * 상품 재고 복구 락 키 (주문 취소 시)
     *
     * @param productId 상품 ID
     * @return order:cancelOrder:stock:lock:{productId}
     */
    public static String productStockIncreaseLock(Long productId) {
        return lockKey("order", "cancelOrder", "stock", String.valueOf(productId));
    }

    // ===== Cache 키 생성 =====

    /**
     * Cache 키 생성 (범용)
     *
     * @param domain 도메인 (예: products, coupons, users)
     * @param resource 리소스 ID 또는 키
     * @return Redis Cache 키
     */
    public static String cacheKey(String domain, String resource) {
        return String.format("cache:%s:%s", domain, resource);
    }

    /**
     * 상품 캐시 키
     *
     * @param productId 상품 ID
     * @return cache:products:{productId}
     */
    public static String productCacheKey(Long productId) {
        return cacheKey("products", String.valueOf(productId));
    }

    /**
     * 쿠폰 캐시 키
     *
     * @param couponId 쿠폰 ID
     * @return cache:coupons:{couponId}
     */
    public static String couponCacheKey(Long couponId) {
        return cacheKey("coupons", String.valueOf(couponId));
    }

    /**
     * 사용자 캐시 키
     *
     * @param userId 사용자 ID
     * @return cache:users:{userId}
     */
    public static String userCacheKey(Long userId) {
        return cacheKey("users", String.valueOf(userId));
    }

    // ===== 유틸리티 메서드 =====

    /**
     * 특정 도메인의 모든 락 패턴
     *
     * @param domain 도메인
     * @return {domain}:*:*:lock:*
     */
    public static String domainLockPattern(String domain) {
        return String.format("%s:*:*:lock:*", domain);
    }

    /**
     * 특정 usecase의 모든 락 패턴
     *
     * @param domain 도메인
     * @param usecase UseCase
     * @return {domain}:{usecase}:*:lock:*
     */
    public static String usecaseLockPattern(String domain, String usecase) {
        return String.format("%s:%s:*:lock:*", domain, usecase);
    }

    /**
     * 모든 락 패턴
     *
     * @return *:*:*:lock:*
     */
    public static String allLockPattern() {
        return "*:*:*:lock:*";
    }
}
