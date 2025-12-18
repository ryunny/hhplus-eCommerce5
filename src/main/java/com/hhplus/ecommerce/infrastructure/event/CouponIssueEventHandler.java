package com.hhplus.ecommerce.infrastructure.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hhplus.ecommerce.config.KafkaConfig;
import com.hhplus.ecommerce.domain.entity.Coupon;
import com.hhplus.ecommerce.domain.entity.User;
import com.hhplus.ecommerce.domain.entity.UserCoupon;
import com.hhplus.ecommerce.domain.enums.CouponStatus;
import com.hhplus.ecommerce.domain.event.CouponIssueFailedEvent;
import com.hhplus.ecommerce.domain.event.CouponIssueRequestedEvent;
import com.hhplus.ecommerce.domain.event.CouponIssuedEvent;
import com.hhplus.ecommerce.domain.repository.CouponRepository;
import com.hhplus.ecommerce.domain.repository.UserCouponRepository;
import com.hhplus.ecommerce.domain.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
public class CouponIssueEventHandler {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final UserService userService;
    private final RedisTemplate<String, String> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public CouponIssueEventHandler(CouponRepository couponRepository,
                                  UserCouponRepository userCouponRepository,
                                  UserService userService,
                                  RedisTemplate<String, String> redisTemplate,
                                  KafkaTemplate<String, Object> kafkaTemplate,
                                  ObjectMapper objectMapper) {
        this.couponRepository = couponRepository;
        this.userCouponRepository = userCouponRepository;
        this.userService = userService;
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    @KafkaListener(
        topics = KafkaConfig.COUPON_ISSUE_REQUESTED_TOPIC,
        groupId = "coupon-issue-service",
        concurrency = "10"
    )
    public void handleCouponIssueRequested(CouponIssueRequestedEvent event) {
        try {
            String idempotencyKey = "coupon:processed:" + event.requestId();
            Boolean isFirstProcessing = redisTemplate.opsForValue()
                .setIfAbsent(idempotencyKey, "1", Duration.ofDays(7));

            if (Boolean.FALSE.equals(isFirstProcessing)) {
                return;
            }

            Coupon coupon = couponRepository.findByIdWithLockOrThrow(event.couponId());
            User user = userService.getUser(event.userId());

            if (!coupon.hasStock()) {
                throw new IllegalStateException("쿠폰 재고가 소진되었습니다");
            }

            if (userCouponRepository.findByUserIdAndCouponId(user.getId(), coupon.getId()).isPresent()) {
                throw new IllegalStateException("이미 발급받은 쿠폰입니다");
            }

            coupon.increaseIssuedQuantity();
            couponRepository.save(coupon);

            UserCoupon userCoupon = new UserCoupon(
                user,
                coupon,
                CouponStatus.UNUSED,
                coupon.getEndDate()
            );
            userCouponRepository.save(userCoupon);

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

        } catch (IllegalStateException e) {
            log.warn("쿠폰 발급 실패: requestId={}, reason={}", event.requestId(), e.getMessage());
            publishFailureEvent(event, e.getMessage());
            rollbackRedisCache(event);

        } catch (Exception e) {
            log.error("쿠폰 발급 시스템 오류: requestId={}", event.requestId(), e);
            publishFailureEvent(event, "시스템 오류: " + e.getMessage());
            rollbackRedisCache(event);
        }
    }

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
    }

    private void rollbackRedisCache(CouponIssueRequestedEvent event) {
        try {
            String stockKey = "coupon:stock:" + event.couponId();
            redisTemplate.opsForValue().increment(stockKey);

            String userKey = "coupon:issued:" + event.couponId() + ":" + event.userId();
            redisTemplate.delete(userKey);
        } catch (Exception e) {
            log.error("Redis 롤백 실패: couponId={}, userId={}", event.couponId(), event.userId(), e);
        }
    }
}
