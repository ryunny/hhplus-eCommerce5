package com.hhplus.ecommerce.domain.service;

import com.hhplus.ecommerce.domain.entity.Coupon;
import com.hhplus.ecommerce.domain.entity.UserCoupon;
import com.hhplus.ecommerce.domain.repository.UserCouponRepository;
import com.hhplus.ecommerce.domain.vo.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 쿠폰 사용 관련 비즈니스 로직을 처리하는 서비스
 *
 * 참고: 쿠폰 발급 로직은 CouponUseCase에서 처리 (동시성 제어 포함)
 */
@Service
public class CouponService {

    private final UserCouponRepository userCouponRepository;

    public CouponService(UserCouponRepository userCouponRepository) {
        this.userCouponRepository = userCouponRepository;
    }

    /**
     * 쿠폰 사용
     *
     * @param userCouponId 사용자 쿠폰 ID
     * @param userId 사용자 ID (검증용)
     * @return 사용된 UserCoupon
     */
    @Transactional
    public UserCoupon useCoupon(Long userCouponId, Long userId) {
        UserCoupon userCoupon = userCouponRepository.findById(userCouponId)
                .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다: " + userCouponId));

        // 쿠폰 소유권 검증
        if (!userCoupon.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("다른 사용자의 쿠폰입니다.");
        }

        // 쿠폰 사용 처리
        userCoupon.use();
        // 더티 체킹으로 자동 저장 (save() 불필요)

        return userCoupon;
    }

    /**
     * 쿠폰 사용 취소 (주문 취소 시)
     *
     * @param userCouponId 사용자 쿠폰 ID
     */
    @Transactional
    public void cancelCoupon(Long userCouponId) {
        UserCoupon userCoupon = userCouponRepository.findById(userCouponId)
                .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다: " + userCouponId));

        userCoupon.cancel();
        // 더티 체킹으로 자동 저장 (save() 불필요)
    }

    /**
     * 할인 금액 계산
     *
     * @param userCoupon 사용자 쿠폰
     * @param orderAmount 주문 금액
     * @return 할인 금액
     */
    public Money calculateDiscount(UserCoupon userCoupon, Money orderAmount) {
        Coupon coupon = userCoupon.getCoupon();
        return coupon.calculateDiscount(orderAmount);
    }

    /**
     * 쿠폰 조회
     *
     * @param userCouponId 사용자 쿠폰 ID
     * @return UserCoupon
     */
    public UserCoupon getUserCoupon(Long userCouponId) {
        return userCouponRepository.findById(userCouponId)
                .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다: " + userCouponId));
    }
}
