package com.hhplus.ecommerce.presentation.controller;

import com.hhplus.ecommerce.application.command.IssueCouponCommand;
import com.hhplus.ecommerce.application.command.JoinCouponQueueCommand;
import com.hhplus.ecommerce.application.query.GetAvailableCouponsQuery;
import com.hhplus.ecommerce.application.query.GetQueueStatusQuery;
import com.hhplus.ecommerce.application.query.GetUserCouponsQuery;
import com.hhplus.ecommerce.application.usecase.coupon.*;
import com.hhplus.ecommerce.domain.entity.Coupon;
import com.hhplus.ecommerce.domain.entity.CouponQueue;
import com.hhplus.ecommerce.domain.entity.UserCoupon;
import com.hhplus.ecommerce.presentation.dto.CouponQueueResponse;
import com.hhplus.ecommerce.presentation.dto.CouponResponse;
import com.hhplus.ecommerce.presentation.dto.MessageResponse;
import com.hhplus.ecommerce.presentation.dto.UserCouponResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final GetIssuableCouponsUseCase getIssuableCouponsUseCase;
    private final IssueCouponUseCase issueCouponUseCase;
    private final GetUserCouponsUseCase getUserCouponsUseCase;
    private final GetAvailableCouponsUseCase getAvailableCouponsUseCase;
    private final JoinCouponQueueUseCase joinCouponQueueUseCase;
    private final GetQueueStatusUseCase getQueueStatusUseCase;

    // Redis 기반 대기열
    private final JoinRedisQueueUseCase joinRedisQueueUseCase;
    private final GetRedisQueueStatusUseCase getRedisQueueStatusUseCase;

    @GetMapping("/issuable")
    public ResponseEntity<List<CouponResponse>> getIssuableCoupons() {
        List<Coupon> coupons = getIssuableCouponsUseCase.execute();
        List<CouponResponse> response = coupons.stream()
                .map(CouponResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{couponId}/issue/{publicId}")
    public ResponseEntity<?> issueCoupon(
            @PathVariable Long couponId,
            @PathVariable String publicId) {
        IssueCouponCommand command = new IssueCouponCommand(publicId, couponId);
        UserCoupon userCoupon = issueCouponUseCase.execute(command);

        if (userCoupon == null) {
            // 대기열 방식: 대기열에 추가됨
            return ResponseEntity.accepted()
                    .body(new MessageResponse("대기열에 추가되었습니다. 상태를 확인해주세요."));
        }

        // 즉시 발급 방식
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

    // ===== 대기열 API (Fallback 패턴: Redis → DB) =====

    /**
     * 대기열 진입 (선착순 쿠폰)
     *
     * Fallback 전략:
     * - Redis 정상: Redis Sorted Set 사용 (O(log N) 성능)
     * - Redis 장애: DB 기반으로 자동 전환
     * - 사용자는 내부 구현을 알 수 없음 (투명한 Fallback)
     *
     * @param couponId 쿠폰 ID
     * @param publicId 사용자 Public ID
     * @return 대기열 정보 (순번 포함)
     */
    @PostMapping("/{couponId}/queue/join/{publicId}")
    public ResponseEntity<CouponQueueResponse> joinQueue(
            @PathVariable Long couponId,
            @PathVariable String publicId) {
        CouponQueueResponse response = joinRedisQueueUseCase.execute(publicId, couponId);
        return ResponseEntity.ok(response);
    }

    /**
     * 대기 상태 조회
     *
     * Fallback 전략:
     * - Redis 정상: Redis 기반 실시간 조회
     * - Redis 장애: DB 기반으로 자동 전환
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

    // ===== Deprecated APIs (하위 호환성 유지) =====

    /**
     * @deprecated Redis Fallback 패턴이 적용된 /queue/join 사용 권장
     */
    @Deprecated
    @PostMapping("/{couponId}/redis-queue/join/{publicId}")
    public ResponseEntity<CouponQueueResponse> joinRedisQueue(
            @PathVariable Long couponId,
            @PathVariable String publicId) {
        return joinQueue(couponId, publicId);
    }

    /**
     * @deprecated Redis Fallback 패턴이 적용된 /queue/status 사용 권장
     */
    @Deprecated
    @GetMapping("/{couponId}/redis-queue/status/{publicId}")
    public ResponseEntity<CouponQueueResponse> getRedisQueueStatus(
            @PathVariable Long couponId,
            @PathVariable String publicId) {
        return getQueueStatus(couponId, publicId);
    }
}
