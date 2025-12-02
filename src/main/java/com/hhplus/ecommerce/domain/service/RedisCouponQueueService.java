package com.hhplus.ecommerce.domain.service;

import com.hhplus.ecommerce.domain.entity.Coupon;
import com.hhplus.ecommerce.domain.entity.User;
import com.hhplus.ecommerce.domain.entity.UserCoupon;
import com.hhplus.ecommerce.domain.enums.CouponStatus;
import com.hhplus.ecommerce.domain.repository.CouponRepository;
import com.hhplus.ecommerce.domain.repository.UserCouponRepository;
import com.hhplus.ecommerce.infrastructure.redis.RedisKeyGenerator;
import com.hhplus.ecommerce.presentation.dto.CouponQueueResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis Sorted Set 기반 선착순 쿠폰 대기열 서비스
 *
 * Sorted Set 활용:
 * - Key: queue:coupon:{couponId}
 * - Member: user:{userId}
 * - Score: timestamp (밀리초) - 자동 선착순 정렬
 *
 * 장점:
 * 1. O(log N) 성능: ZADD, ZRANK 모두 매우 빠름
 * 2. 자동 정렬: timestamp score로 선착순 자동 보장
 * 3. 원자적 연산: Redis 자체가 Single Thread라 락 불필요
 * 4. 실시간 순번: ZRANK로 즉시 조회 가능
 * 5. 메모리 효율: TTL 설정으로 자동 정리
 */
@Slf4j
@Service
public class RedisCouponQueueService {

    private final RedisTemplate<String, String> redisTemplate;
    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final UserService userService;

    public RedisCouponQueueService(RedisTemplate<String, String> redisTemplate,
                                   CouponRepository couponRepository,
                                   UserCouponRepository userCouponRepository,
                                   UserService userService) {
        this.redisTemplate = redisTemplate;
        this.couponRepository = couponRepository;
        this.userCouponRepository = userCouponRepository;
        this.userService = userService;
    }

    /**
     * 대기열 진입 (원자적 연산)
     *
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID
     * @return 대기 순번 (1부터 시작)
     */
    public CouponQueueResponse joinQueue(Long userId, Long couponId) {
        // 1. 사용자 및 쿠폰 검증
        User user = userService.getUser(userId);
        Coupon coupon = getCoupon(couponId);

        // 2. 이미 발급받았는지 확인
        Optional<UserCoupon> existingCoupon = userCouponRepository.findByUserIdAndCouponId(userId, couponId);
        if (existingCoupon.isPresent()) {
            throw new IllegalStateException("이미 발급받은 쿠폰입니다.");
        }

        // 3. Redis Sorted Set에 추가
        String queueKey = RedisKeyGenerator.couponQueue(couponId);
        String member = "user:" + userId;
        double score = System.currentTimeMillis(); // timestamp (밀리초)

        // 원자적 연산: 이미 있으면 false 반환
        Boolean added = redisTemplate.opsForZSet().add(queueKey, member, score);

        if (added == null || !added) {
            // 이미 대기열에 있는 경우 - 순번만 반환
            Integer position = getQueuePosition(userId, couponId);
            log.info("이미 대기열에 존재: userId={}, couponId={}, position={}", userId, couponId, position);
            return new CouponQueueResponse(couponId, position, getTotalWaiting(couponId));
        }

        // 4. TTL 설정 (24시간 후 자동 삭제)
        redisTemplate.expire(queueKey, 24, TimeUnit.HOURS);

        // 5. 순번 조회
        Integer position = getQueuePosition(userId, couponId);

        log.info("대기열 진입 성공: userId={}, couponId={}, position={}", userId, couponId, position);
        return new CouponQueueResponse(couponId, position, getTotalWaiting(couponId));
    }

    /**
     * 내 대기 순번 조회
     *
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID
     * @return 대기 순번 (1등부터 시작)
     */
    public Integer getQueuePosition(Long userId, Long couponId) {
        String queueKey = RedisKeyGenerator.couponQueue(couponId);
        String member = "user:" + userId;

        // ZRANK: O(log N)
        Long rank = redisTemplate.opsForZSet().rank(queueKey, member);

        if (rank == null) {
            throw new IllegalStateException("대기열에 진입하지 않았습니다.");
        }

        // 0-based index → 1-based position
        return rank.intValue() + 1;
    }

    /**
     * 대기 상태 조회
     *
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID
     * @return 대기열 정보
     */
    public CouponQueueResponse getQueueStatus(Long userId, Long couponId) {
        Integer position = getQueuePosition(userId, couponId);
        Long totalWaiting = getTotalWaiting(couponId);

        return new CouponQueueResponse(couponId, position, totalWaiting);
    }

    /**
     * 전체 대기 인원 조회
     *
     * @param couponId 쿠폰 ID
     * @return 전체 대기 인원
     */
    public Long getTotalWaiting(Long couponId) {
        String queueKey = RedisKeyGenerator.couponQueue(couponId);

        // ZCARD: O(1)
        Long size = redisTemplate.opsForZSet().size(queueKey);
        return size != null ? size : 0L;
    }

    /**
     * 상위 N명 쿠폰 발급 처리
     *
     * 배치로 호출되는 메서드입니다.
     * 1. 상위 N명 조회
     * 2. 각 사용자에게 쿠폰 발급
     * 3. 대기열에서 제거
     *
     * @param couponId 쿠폰 ID
     * @param batchSize 한 번에 처리할 개수 (기본 10명)
     * @return 성공 발급 개수
     */
    public int processBatch(Long couponId, int batchSize) {
        String queueKey = RedisKeyGenerator.couponQueue(couponId);

        // 1. 상위 N명 조회 (선착순): O(log N + batchSize)
        Set<ZSetOperations.TypedTuple<String>> topUsers =
                redisTemplate.opsForZSet().rangeWithScores(queueKey, 0, batchSize - 1);

        if (topUsers == null || topUsers.isEmpty()) {
            log.debug("대기열이 비어있습니다: couponId={}", couponId);
            return 0;
        }

        int successCount = 0;

        // 2. 각 사용자에게 쿠폰 발급
        for (ZSetOperations.TypedTuple<String> tuple : topUsers) {
            String member = tuple.getValue();
            if (member == null) continue;

            Long userId = extractUserId(member);

            try {
                // 쿠폰 발급 (트랜잭션)
                issueAndRemove(userId, couponId, member);
                successCount++;

                log.info("쿠폰 발급 성공: userId={}, couponId={}", userId, couponId);
            } catch (IllegalStateException e) {
                // 이미 발급받았거나 수량 소진
                log.warn("쿠폰 발급 실패 (대기열에서 제거): userId={}, couponId={}, reason={}",
                        userId, couponId, e.getMessage());

                // 대기열에서 제거 (더 이상 처리 불필요)
                redisTemplate.opsForZSet().remove(queueKey, member);
            } catch (Exception e) {
                // 예상치 못한 에러 - 대기열 유지 (다음 배치에서 재시도)
                log.error("쿠폰 발급 중 예외 발생 (대기열 유지): userId={}, couponId={}",
                        userId, couponId, e);
            }
        }

        log.info("배치 처리 완료: couponId={}, 처리={}/{}", couponId, successCount, topUsers.size());
        return successCount;
    }

    /**
     * 쿠폰 발급 및 대기열 제거 (트랜잭션)
     *
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID
     * @param member Redis member (user:{userId})
     */
    @Transactional
    protected void issueAndRemove(Long userId, Long couponId, String member) {
        // 1. 중복 발급 검증
        Optional<UserCoupon> existing = userCouponRepository.findByUserIdAndCouponId(userId, couponId);
        if (existing.isPresent()) {
            throw new IllegalStateException("이미 발급받은 쿠폰입니다.");
        }

        // 2. 쿠폰 조회 및 발급 가능 여부 확인
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다: " + couponId));

        if (!coupon.isIssuable()) {
            throw new IllegalStateException("쿠폰의 모든 수량이 소진되었습니다.");
        }

        // 3. 쿠폰 발급
        User user = userService.getUser(userId);

        coupon.increaseIssuedQuantity();
        couponRepository.save(coupon);

        UserCoupon userCoupon = new UserCoupon(
                user,
                coupon,
                CouponStatus.UNUSED,
                coupon.getEndDate()
        );
        userCouponRepository.save(userCoupon);

        // 4. 대기열에서 제거 (발급 성공 시)
        String queueKey = RedisKeyGenerator.couponQueue(couponId);
        redisTemplate.opsForZSet().remove(queueKey, member);
    }

    /**
     * 대기열 초기화 (테스트용)
     *
     * @param couponId 쿠폰 ID
     */
    public void clearQueue(Long couponId) {
        String queueKey = RedisKeyGenerator.couponQueue(couponId);
        redisTemplate.delete(queueKey);
        log.info("대기열 초기화: couponId={}", couponId);
    }

    /**
     * Redis member에서 사용자 ID 추출
     * "user:123" → 123
     *
     * @param member Redis member
     * @return 사용자 ID
     */
    private Long extractUserId(String member) {
        return Long.parseLong(member.replace("user:", ""));
    }

    /**
     * 쿠폰 조회 (캐시 활용)
     *
     * @param couponId 쿠폰 ID
     * @return 쿠폰
     */
    private Coupon getCoupon(Long couponId) {
        return couponRepository.findById(couponId)
                .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다: " + couponId));
    }
}