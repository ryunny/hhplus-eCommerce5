package com.hhplus.ecommerce.infrastructure.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhplus.ecommerce.config.KafkaConfig;
import com.hhplus.ecommerce.domain.entity.Coupon;
import com.hhplus.ecommerce.domain.entity.User;
import com.hhplus.ecommerce.domain.entity.UserCoupon;
import com.hhplus.ecommerce.domain.event.CouponIssueFailedEvent;
import com.hhplus.ecommerce.domain.event.CouponIssueRequestedEvent;
import com.hhplus.ecommerce.domain.event.CouponIssuedEvent;
import com.hhplus.ecommerce.domain.service.CouponService;
import com.hhplus.ecommerce.domain.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * 쿠폰 발급 이벤트 핸들러 (Kafka Consumer)
 *
 * Kafka에서 쿠폰 발급 요청을 수신하여 실제 발급 처리를 담당합니다.
 *
 * Consumer Group: coupon-issue-service
 * Concurrency: 10 (파티션 수와 동일)
 *
 * 주요 기능:
 * 1. 멱등성 보장 (중복 처리 방지)
 * 2. DB 비관적 락으로 최종 재고 확인
 * 3. 쿠폰 발급 (UserCoupon 생성)
 * 4. 성공/실패 이벤트 발행
 */
@Slf4j
@Component
public class CouponIssueEventHandler {

    private final CouponService couponService;
    private final UserService userService;
    private final RedisTemplate<String, String> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public CouponIssueEventHandler(CouponService couponService,
                                  UserService userService,
                                  RedisTemplate<String, String> redisTemplate,
                                  KafkaTemplate<String, Object> kafkaTemplate,
                                  ObjectMapper objectMapper) {
        this.couponService = couponService;
        this.userService = userService;
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 쿠폰 발급 요청 처리
     *
     * Kafka Consumer가 요청을 꺼내서 실제 DB에 발급
     *
     * @Transactional: 쿠폰 발급 + 재고 차감을 하나의 트랜잭션으로 처리
     */
    @Transactional
    @KafkaListener(
        topics = KafkaConfig.COUPON_ISSUE_REQUESTED_TOPIC,
        groupId = "coupon-issue-service",
        concurrency = "10"  // 10개 Consumer 스레드 (파티션 수와 동일)
    )
    public void handleCouponIssueRequested(CouponIssueRequestedEvent event) {
        try {
            log.info("[쿠폰-Kafka] 발급 처리 시작: requestId={}, couponId={}, userId={}",
                event.requestId(), event.couponId(), event.userId());

            // ====================================
            // Step 1: 멱등성 체크 (중복 처리 방지)
            // ====================================
            String idempotencyKey = "coupon:processed:" + event.requestId();
            Boolean isFirstProcessing = redisTemplate.opsForValue()
                .setIfAbsent(idempotencyKey, "1", Duration.ofDays(7));

            if (Boolean.FALSE.equals(isFirstProcessing)) {
                log.warn("[쿠폰-Kafka] 중복 처리 요청 무시: requestId={}", event.requestId());
                return;  // 이미 처리됨
            }

            // ====================================
            // Step 2: DB에서 실제 발급
            // ====================================
            Coupon coupon = couponService.getCouponWithLock(event.couponId());
            User user = userService.getUser(event.userId());

            // 재고 확인 (비관적 락)
            if (!coupon.hasStock()) {
                throw new IllegalStateException("쿠폰 재고가 소진되었습니다");
            }

            // 중복 발급 체크 (DB unique 제약)
            UserCoupon userCoupon = couponService.issueCoupon(coupon, user);

            log.info("[쿠폰-Kafka] 발급 완료: userCouponId={}, couponId={}, userId={}",
                userCoupon.getId(), event.couponId(), event.userId());

            // ====================================
            // Step 3: 성공 이벤트 발행
            // ====================================
            CouponIssuedEvent successEvent = new CouponIssuedEvent(
                event.requestId(),
                userCoupon.getId(),
                event.couponId(),
                event.userId(),
                Instant.now()
            );

            kafkaTemplate.send(
                KafkaConfig.COUPON_ISSUED_TOPIC,
                event.userId().toString(),
                successEvent
            );

            log.info("[쿠폰-Kafka] 발급 성공 이벤트 발행: requestId={}", event.requestId());

        } catch (IllegalStateException e) {
            // 예상된 실패 (재고 소진, 중복 발급 등)
            log.warn("[쿠폰-Kafka] 발급 실패 (예상): requestId={}, reason={}",
                event.requestId(), e.getMessage());

            publishFailureEvent(event, e.getMessage());
            rollbackRedisCache(event);

        } catch (Exception e) {
            // 예상치 못한 오류
            log.error("[쿠폰-Kafka] 발급 실패 (시스템 오류): requestId={}, error={}",
                event.requestId(), e.getMessage(), e);

            publishFailureEvent(event, "시스템 오류: " + e.getMessage());
            rollbackRedisCache(event);

            // 재시도를 위해 예외를 던지지 않음 (DLQ로 전송됨)
        }
    }

    /**
     * 실패 이벤트 발행 (DLQ)
     */
    private void publishFailureEvent(CouponIssueRequestedEvent event, String reason) {
        CouponIssueFailedEvent failEvent = new CouponIssueFailedEvent(
            event.requestId(),
            event.couponId(),
            event.userId(),
            reason,
            Instant.now()
        );

        kafkaTemplate.send(
            KafkaConfig.COUPON_ISSUE_FAILED_TOPIC,
            event.userId().toString(),
            failEvent
        );

        log.info("[쿠폰-Kafka] 실패 이벤트 발행: requestId={}, reason={}", event.requestId(), reason);
    }

    /**
     * Redis 캐시 롤백 (재고 복구)
     */
    private void rollbackRedisCache(CouponIssueRequestedEvent event) {
        try {
            String stockKey = "coupon:stock:" + event.couponId();
            redisTemplate.opsForValue().increment(stockKey);

            String userKey = "coupon:issued:" + event.couponId() + ":" + event.userId();
            redisTemplate.delete(userKey);

            log.info("[쿠폰-Kafka] Redis 캐시 롤백 완료: couponId={}, userId={}",
                event.couponId(), event.userId());
        } catch (Exception e) {
            log.error("[쿠폰-Kafka] Redis 롤백 실패: couponId={}, userId={}, error={}",
                event.couponId(), event.userId(), e.getMessage());
        }
    }
}
