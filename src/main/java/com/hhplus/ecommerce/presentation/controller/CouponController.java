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
            // 대기열 방식: Redis → DB Fallback 적용
            return ResponseEntity.accepted()
                    .body(new MessageResponse("대기열에 추가되었습니다. /queue/status API로 순번을 확인하세요."));
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

    // ===== 대기열 상태 조회 API =====

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

    // ===== Deprecated APIs (하위 호환성 유지) =====

    /**
     * @deprecated /issue API 사용 권장 (useQueue 설정에 따라 자동으로 대기열 진입)
     *             Redis → DB Fallback 자동 적용
     */
    @Deprecated
    @PostMapping("/{couponId}/queue/join/{publicId}")
    public ResponseEntity<CouponQueueResponse> joinQueue(
            @PathVariable Long couponId,
            @PathVariable String publicId) {
        CouponQueueResponse response = joinRedisQueueUseCase.execute(publicId, couponId);
        return ResponseEntity.ok(response);
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

    /**
     * @deprecated /issue API 사용 권장
     */
    @Deprecated
    @PostMapping("/{couponId}/redis-queue/join/{publicId}")
    public ResponseEntity<CouponQueueResponse> joinRedisQueue(
            @PathVariable Long couponId,
            @PathVariable String publicId) {
        return joinQueue(couponId, publicId);
    }
}
