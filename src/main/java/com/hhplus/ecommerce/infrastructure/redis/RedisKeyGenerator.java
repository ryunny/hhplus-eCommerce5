package com.hhplus.ecommerce.infrastructure.redis;

/**
 * Redis 키 생성 유틸리티 클래스
 *
 * 타입 기반 계층적 네임스페이스 구조를 사용하여 키의 목적과 출처를 명확히 합니다.
 * 형식: {type}:{domain}:{usecase}:{action}:{resource}
 *
 * 예시:
 * - lock:coupon:issueCoupon:direct:123       (쿠폰 즉시 발급 락)
 * - lock:order:placeOrder:stock:456          (주문 시 재고 차감 락)
 * - cache:products:789                        (상품 캐시)
 * - ranking:products:1day                     (인기 상품 랭킹)
 *
 * 장점:
 * 1. 타입별 조회: KEYS lock:* 로 모든 락 조회, KEYS cache:* 로 모든 캐시 조회
 * 2. 추적 가능: KEYS lock:coupon:issueCoupon:* 로 특정 usecase의 모든 락 조회
 * 3. 패턴 삭제: KEYS lock:order:* 로 주문 관련 모든 락 삭제
 * 4. 모니터링: 어떤 타입의 키가 많은지, 어떤 usecase가 락을 많이 사용하는지 분석 가능
 * 5. 명확성: 키만 봐도 타입과 비즈니스 행위를 알 수 있음
 */
public class RedisKeyGenerator {

    /**
     * Lock 키 생성 (범용)
     *
     * @param domain 도메인 (예: coupon, product, order)
     * @param usecase UseCase 이름 (예: issueCoupon, placeOrder)
     * @param action 액션 (예: direct, stock, batch)
     * @param resource 리소스 ID
     * @return Redis Lock 키 (형식: lock:{domain}:{usecase}:{action}:{resource})
     */
    public static String lockKey(String domain, String usecase, String action, String resource) {
        return String.format("lock:%s:%s:%s:%s", domain, usecase, action, resource);
    }

    /**
     * 쿠폰 즉시 발급 락 키
     *
     * @param couponId 쿠폰 ID
     * @return lock:coupon:issueCoupon:direct:{couponId}
     */
    public static String couponIssueLock(Long couponId) {
        return lockKey("coupon", "issueCoupon", "direct", String.valueOf(couponId));
    }

    /**
     * 쿠폰 대기열 진입 락 키
     *
     * @param couponId 쿠폰 ID
     * @return lock:coupon:joinQueue:queue:{couponId}
     */
    public static String couponQueueJoinLock(Long couponId) {
        return lockKey("coupon", "joinQueue", "queue", String.valueOf(couponId));
    }

    /**
     * 쿠폰 대기열 개별 처리 락 키
     *
     * @param couponId 쿠폰 ID
     * @return lock:coupon:processQueue:item:{couponId}
     */
    public static String couponQueueItemLock(Long couponId) {
        return lockKey("coupon", "processQueue", "item", String.valueOf(couponId));
    }

    /**
     * 쿠폰 대기열 배치 처리 락 키
     *
     * @param couponId 쿠폰 ID
     * @return lock:coupon:processQueue:batch:{couponId}
     */
    public static String couponQueueBatchLock(Long couponId) {
        return lockKey("coupon", "processQueue", "batch", String.valueOf(couponId));
    }

    /**
     * 쿠폰 만료 배치 락 키
     *
     * @return lock:coupon:expireCoupons:batch:global
     */
    public static String couponExpireBatchLock() {
        return lockKey("coupon", "expireCoupons", "batch", "global");
    }

    /**
     * 쿠폰 대기열 순번 업데이트 배치 락 키
     *
     * @return lock:coupon:updateQueuePositions:batch:global
     */
    public static String couponQueueUpdatePositionsLock() {
        return lockKey("coupon", "updateQueuePositions", "batch", "global");
    }

    /**
     * 상품 재고 차감 락 키 (주문 시)
     *
     * @param productId 상품 ID
     * @return lock:order:placeOrder:stock:{productId}
     */
    public static String productStockDecreaseLock(Long productId) {
        return lockKey("order", "placeOrder", "stock", String.valueOf(productId));
    }

    /**
     * 상품 재고 복구 락 키 (주문 취소 시)
     *
     * @param productId 상품 ID
     * @return lock:order:cancelOrder:stock:{productId}
     */
    public static String productStockIncreaseLock(Long productId) {
        return lockKey("order", "cancelOrder", "stock", String.valueOf(productId));
    }

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
     * 사용자 캐시 키 (ID 기반)
     *
     * @param userId 사용자 ID
     * @return cache:users:{userId}
     */
    public static String userCacheKey(Long userId) {
        return cacheKey("users", String.valueOf(userId));
    }

    /**
     * 사용자 캐시 키 (PublicId 기반)
     *
     * @param publicId 사용자 공개 ID
     * @return cache:users:publicId:{publicId}
     */
    public static String userCacheKeyByPublicId(String publicId) {
        return cacheKey("users", "publicId:" + publicId);
    }

    /**
     * 인기 상품 목록 캐시 키
     *
     * @param limit 조회 개수
     * @return cache:topProducts:{limit}
     */
    public static String topProductsCacheKey(Integer limit) {
        return cacheKey("topProducts", String.valueOf(limit));
    }

    /**
     * 발급 가능한 쿠폰 목록 캐시 키
     *
     * @return cache:issuableCoupons:all
     */
    public static String issuableCouponsCacheKey() {
        return cacheKey("issuableCoupons", "all");
    }

    /**
     * 특정 날짜의 인기 상품 랭킹 키 (Sorted Set)
     *
     * @param date 날짜
     * @return ranking:products:{yyyy-MM-dd}
     */
    public static String productRankingByDate(java.time.LocalDate date) {
        return "ranking:products:" + date.toString();
    }

    /**
     * 인기 상품 랭킹 임시 UNION 키
     *
     * @return ranking:products:temp:{uuid}
     */
    public static String productRankingTempKey() {
        return "ranking:products:temp:" + java.util.UUID.randomUUID();
    }

    /**
     * 1일 기준 인기 상품 랭킹 (Sorted Set)
     *
     * @return ranking:products:1day
     * @deprecated 날짜별 키 분리 방식으로 변경. {@link #productRankingByDate(java.time.LocalDate)} 사용
     */
    @Deprecated
    public static String productRanking1Day() {
        return "ranking:products:1day";
    }

    /**
     * 쿠폰 선착순 대기열 (Sorted Set)
     * Member: user:{userId}
     * Score: timestamp (밀리초)
     *
     * @param couponId 쿠폰 ID
     * @return queue:coupon:{couponId}
     */
    public static String couponQueue(Long couponId) {
        return String.format("queue:coupon:%d", couponId);
    }

    /**
     * 쿠폰 대기열 처리 중 상태 (Set)
     * 발급 처리 중인 사용자를 기록하여 중복 처리 방지
     *
     * @param couponId 쿠폰 ID
     * @return queue:coupon:{couponId}:processing
     */
    public static String couponQueueProcessing(Long couponId) {
        return String.format("queue:coupon:%d:processing", couponId);
    }

    /**
     * 7일 기준 인기 상품 랭킹 (Sorted Set)
     *
     * @return ranking:products:7days
     * @deprecated 날짜별 키 분리 방식으로 변경. {@link #productRankingByDate(java.time.LocalDate)} 사용
     */
    @Deprecated
    public static String productRanking7Days() {
        return "ranking:products:7days";
    }

    /**
     * 기간별 인기 상품 랭킹 키 조회
     *
     * @param days 기간 (1 또는 7)
     * @return ranking:products:{days}day(s)
     * @deprecated 날짜별 키 분리 방식으로 변경. {@link #productRankingByDate(java.time.LocalDate)} 사용
     */
    @Deprecated
    public static String productRankingByDays(int days) {
        if (days == 1) {
            return productRanking1Day();
        } else if (days == 7) {
            return productRanking7Days();
        }
        throw new IllegalArgumentException("지원하지 않는 기간입니다: " + days + "일 (1일 또는 7일만 지원)");
    }

    /**
     * 특정 도메인의 모든 락 패턴
     *
     * @param domain 도메인
     * @return lock:{domain}:*
     */
    public static String domainLockPattern(String domain) {
        return String.format("lock:%s:*", domain);
    }

    /**
     * 특정 usecase의 모든 락 패턴
     *
     * @param domain 도메인
     * @param usecase UseCase
     * @return lock:{domain}:{usecase}:*
     */
    public static String usecaseLockPattern(String domain, String usecase) {
        return String.format("lock:%s:%s:*", domain, usecase);
    }

    /**
     * 모든 락 패턴
     *
     * @return lock:*
     */
    public static String allLockPattern() {
        return "lock:*";
    }
}
