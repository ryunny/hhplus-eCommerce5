package com.hhplus.ecommerce.presentation.controller;

import com.hhplus.ecommerce.application.command.IssueCouponCommand;
import com.hhplus.ecommerce.application.query.GetAvailableCouponsQuery;
import com.hhplus.ecommerce.application.query.GetUserCouponsQuery;
import com.hhplus.ecommerce.application.usecase.coupon.*;
import com.hhplus.ecommerce.config.KafkaConfig;
import com.hhplus.ecommerce.domain.entity.Coupon;
import com.hhplus.ecommerce.domain.entity.UserCoupon;
import com.hhplus.ecommerce.domain.event.CouponIssueRequestedEvent;
import com.hhplus.ecommerce.domain.service.UserService;
import com.hhplus.ecommerce.presentation.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final GetIssuableCouponsUseCase getIssuableCouponsUseCase;
    private final IssueCouponUseCase issueCouponUseCase;
    private final GetUserCouponsUseCase getUserCouponsUseCase;
    private final GetAvailableCouponsUseCase getAvailableCouponsUseCase;
    private final GetRedisQueueStatusUseCase getRedisQueueStatusUseCase;

    // Kafka 기반 선착순 쿠폰 발급용
    private final UserService userService;
    private final RedisTemplate<String, String> redisTemplate;
    private final KafkaTemplate<String, CouponIssueRequestedEvent> kafkaTemplate;

    @GetMapping("/issuable")
    public ResponseEntity<List<CouponResponse>> getIssuableCoupons() {
        List<Coupon> coupons = getIssuableCouponsUseCase.execute();
        List<CouponResponse> response = coupons.stream()
                .map(CouponResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }

    /**
     * 쿠폰 발급 (통합 API)
     *
     * 쿠폰 설정(useQueue)에 따라 자동으로 즉시 발급 또는 대기열 발급을 선택합니다.
     *
     * useQueue = false (즉시 발급):
     * - 즉시 쿠폰 발급 (Redis 분산 락으로 동시성 제어)
     * - 200 OK + UserCoupon 반환
     *
     * useQueue = true (대기열 발급):
     * - Redis 대기열 진입 시도 → 실패 시 DB 대기열로 Fallback
     * - 202 Accepted + 대기 안내 메시지
     * - 스케줄러가 순차 처리
     * - /queue/status API로 대기 순번 조회 가능
     *
     * @param couponId 쿠폰 ID
     * @param publicId 사용자 Public ID
     * @return 즉시 발급: 200 + UserCoupon, 대기열: 202 + 메시지
     */
    @PostMapping("/{couponId}/issue/{publicId}")
    public ResponseEntity<?> issueCoupon(
            @PathVariable Long couponId,
            @PathVariable String publicId) {
        IssueCouponCommand command = new IssueCouponCommand(publicId, couponId);
        UserCoupon userCoupon = issueCouponUseCase.execute(command);

        if (userCoupon == null) {
            return ResponseEntity.accepted()
                    .body(new MessageResponse("대기열에 추가되었습니다. /queue/status API로 순번을 확인하세요."));
        }

        return ResponseEntity.ok(UserCouponResponse.from(userCoupon));
    }

    @GetMapping("/user/{publicId}")
    public ResponseEntity<List<UserCouponResponse>> getUserCoupons(@PathVariable String publicId) {
        GetUserCouponsQuery query = new GetUserCouponsQuery(publicId);
        List<UserCoupon> userCoupons = getUserCouponsUseCase.execute(query);
        List<UserCouponResponse> response = userCoupons.stream()
                .map(UserCouponResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/user/{publicId}/available")
    public ResponseEntity<List<UserCouponResponse>> getAvailableCoupons(@PathVariable String publicId) {
        GetAvailableCouponsQuery query = new GetAvailableCouponsQuery(publicId);
        List<UserCoupon> userCoupons = getAvailableCouponsUseCase.execute(query);
        List<UserCouponResponse> response = userCoupons.stream()
                .map(UserCouponResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }

    /**
     * 대기 상태 조회 (Redis → DB Fallback)
     *
     * 참고: 대기열 진입은 /issue API의 useQueue 설정으로 자동 처리됩니다.
     *
     * @param couponId 쿠폰 ID
     * @param publicId 사용자 Public ID
     * @return 실시간 대기 순번
     */
    @GetMapping("/{couponId}/queue/status/{publicId}")
    public ResponseEntity<CouponQueueResponse> getQueueStatus(
            @PathVariable Long couponId,
            @PathVariable String publicId) {
        CouponQueueResponse response = getRedisQueueStatusUseCase.execute(publicId, couponId);
        return ResponseEntity.ok(response);
    }

    // ========================================
    // Kafka 기반 선착순 쿠폰 발급 API
    // ========================================

    /**
     * 선착순 쿠폰 발급 요청 (Kafka 기반)
     *
     * Redis로 빠른 중복 체크 및 재고 확인 후 Kafka로 비동기 처리
     *
     * 흐름:
     * 1. Redis 중복 체크 (setIfAbsent)
     * 2. Redis 재고 확인 (decrement)
     * 3. Kafka 이벤트 발행 (CouponIssueRequestedEvent)
     * 4. 즉시 응답 (202 Accepted)
     *
     * 실제 발급은 CouponIssueEventHandler (Kafka Consumer)가 처리
     *
     * @param couponId 쿠폰 ID
     * @param publicId 사용자 Public ID
     * @return 202 Accepted + requestId
     */
    @PostMapping("/{couponId}/issue-fcfs/{publicId}")
    public ResponseEntity<CouponIssueResponse> issueCouponFCFS(
            @PathVariable Long couponId,
            @PathVariable String publicId) {

        Long userId = userService.getUserByPublicId(publicId).getId();

        // ====================================
        // Step 1: Redis 중복 체크 (빠른 실패)
        // ====================================
        String redisKey = "coupon:issued:" + couponId + ":" + userId;
        Boolean isFirstRequest = redisTemplate.opsForValue()
            .setIfAbsent(redisKey, "1", Duration.ofHours(24));

        if (Boolean.FALSE.equals(isFirstRequest)) {
            throw new IllegalStateException("이미 발급 요청한 쿠폰입니다");
        }

        // ====================================
        // Step 2: Redis 재고 확인 (빠른 실패)
        // ====================================
        String stockKey = "coupon:stock:" + couponId;
        Long remainingStock = redisTemplate.opsForValue().decrement(stockKey);

        if (remainingStock == null || remainingStock < 0) {
            // Redis 롤백
            redisTemplate.opsForValue().increment(stockKey);
            redisTemplate.delete(redisKey);
            throw new IllegalStateException("쿠폰이 모두 소진되었습니다");
        }

        // ====================================
        // Step 3: Kafka 이벤트 발행
        // ====================================
        String requestId = UUID.randomUUID().toString();
        CouponIssueRequestedEvent event = new CouponIssueRequestedEvent(
            requestId,
            couponId,
            userId,
            Instant.now()
        );

        // Key를 userId로 설정하여 같은 사용자는 같은 파티션으로 전송
        // → 순서 보장 (한 사용자의 요청은 순차 처리)
        kafkaTemplate.send(
            KafkaConfig.COUPON_ISSUE_REQUESTED_TOPIC,
            userId.toString(),  // partition key
            event
        );

        log.info("[쿠폰-FCFS] 발급 요청 접수: couponId={}, userId={}, requestId={}",
            couponId, userId, requestId);

        // ====================================
        // Step 4: 즉시 응답 (비동기 처리)
        // ====================================
        return ResponseEntity.accepted()
            .body(new CouponIssueResponse(
                requestId,
                "쿠폰 발급 요청이 접수되었습니다. 잠시 후 결과를 확인해주세요.",
                remainingStock
            ));
    }
}
