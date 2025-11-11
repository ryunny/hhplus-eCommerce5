package com.hhplus.ecommerce.application.usecase.coupon;

import com.hhplus.ecommerce.BaseIntegrationTest;
import com.hhplus.ecommerce.application.command.IssueCouponCommand;
import com.hhplus.ecommerce.domain.entity.Coupon;
import com.hhplus.ecommerce.domain.entity.User;
import com.hhplus.ecommerce.domain.entity.UserCoupon;
import com.hhplus.ecommerce.domain.enums.CouponStatus;
import com.hhplus.ecommerce.domain.repository.CouponRepository;
import com.hhplus.ecommerce.domain.repository.UserCouponRepository;
import com.hhplus.ecommerce.domain.repository.UserRepository;
import com.hhplus.ecommerce.domain.vo.DiscountRate;
import com.hhplus.ecommerce.domain.vo.Email;
import com.hhplus.ecommerce.domain.vo.Money;
import com.hhplus.ecommerce.domain.vo.Phone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * IssueCouponUseCase 통합 테스트
 *
 * 쿠폰 발급 핵심 기능을 검증합니다.
 * - 쿠폰 즉시 발급
 * - 쿠폰 발급 수량 관리
 * - 중복 발급 방지
 * - 발급 기간 검증
 */
class IssueCouponUseCaseIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private IssueCouponUseCase issueCouponUseCase;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Test
    @DisplayName("쿠폰을 즉시 발급받을 수 있다")
    void issueCoupon_Success() {
        // given - 사용자 생성
        User user = createUser("홍길동", "hong@test.com", "010-1234-5678");
        userRepository.save(user);

        // given - 쿠폰 생성 (즉시 발급 방식)
        Coupon coupon = createPercentageCoupon("10% 할인", 10, 100, false);
        couponRepository.save(coupon);

        // given - 발급 명령
        IssueCouponCommand command = new IssueCouponCommand(user.getPublicId(), coupon.getId());

        // when - 쿠폰 발급
        UserCoupon userCoupon = issueCouponUseCase.execute(command);

        // then - 발급 검증
        assertThat(userCoupon).isNotNull();
        assertThat(userCoupon.getUser().getId()).isEqualTo(user.getId());
        assertThat(userCoupon.getCoupon().getId()).isEqualTo(coupon.getId());
        assertThat(userCoupon.getStatus()).isEqualTo(CouponStatus.UNUSED);
        assertThat(userCoupon.getIssuedAt()).isNotNull();
        assertThat(userCoupon.getExpiresAt()).isNotNull();

        // then - 쿠폰 발급 수량 증가 확인
        Coupon updatedCoupon = couponRepository.findById(coupon.getId()).orElseThrow();
        assertThat(updatedCoupon.getIssuedQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("동일한 쿠폰을 중복 발급받을 수 없다")
    void issueCoupon_DuplicateIssue() {
        // given - 사용자 생성
        User user = createUser("김철수", "kim@test.com", "010-2345-6789");
        userRepository.save(user);

        // given - 쿠폰 생성
        Coupon coupon = createPercentageCoupon("20% 할인", 20, 100, false);
        couponRepository.save(coupon);

        // given - 첫 번째 발급
        IssueCouponCommand command = new IssueCouponCommand(user.getPublicId(), coupon.getId());
        issueCouponUseCase.execute(command);

        // when & then - 두 번째 발급 시도 시 예외 발생
        assertThatThrownBy(() -> issueCouponUseCase.execute(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 발급받은 쿠폰입니다");

        // then - 발급 수량은 1개만 증가
        Coupon updatedCoupon = couponRepository.findById(coupon.getId()).orElseThrow();
        assertThat(updatedCoupon.getIssuedQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("쿠폰 재고가 소진되면 발급할 수 없다")
    void issueCoupon_SoldOut() {
        // given - 사용자들 생성
        User user1 = createUser("사용자1", "user1@test.com", "010-1111-1111");
        User user2 = createUser("사용자2", "user2@test.com", "010-2222-2222");
        userRepository.save(user1);
        userRepository.save(user2);

        // given - 수량이 1개인 쿠폰 생성
        Coupon coupon = createPercentageCoupon("한정 쿠폰", 30, 1, false);
        couponRepository.save(coupon);

        // given - 첫 번째 사용자가 발급 (성공)
        issueCouponUseCase.execute(new IssueCouponCommand(user1.getPublicId(), coupon.getId()));

        // when & then - 두 번째 사용자 발급 시도 시 실패
        assertThatThrownBy(() ->
                issueCouponUseCase.execute(new IssueCouponCommand(user2.getPublicId(), coupon.getId()))
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("쿠폰을 발급할 수 없습니다");

        // then - 발급 수량 확인
        Coupon updatedCoupon = couponRepository.findById(coupon.getId()).orElseThrow();
        assertThat(updatedCoupon.getIssuedQuantity()).isEqualTo(1);
        assertThat(updatedCoupon.getTotalQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("여러 사용자가 동시에 쿠폰을 발급받을 수 있다")
    void issueCoupon_MultipleUsers() {
        // given - 여러 사용자 생성
        User user1 = createUser("사용자1", "user1@test.com", "010-1111-1111");
        User user2 = createUser("사용자2", "user2@test.com", "010-2222-2222");
        User user3 = createUser("사용자3", "user3@test.com", "010-3333-3333");
        userRepository.save(user1);
        userRepository.save(user2);
        userRepository.save(user3);

        // given - 쿠폰 생성
        Coupon coupon = createPercentageCoupon("인기 쿠폰", 15, 100, false);
        couponRepository.save(coupon);

        // when - 여러 사용자가 발급
        UserCoupon userCoupon1 = issueCouponUseCase.execute(
                new IssueCouponCommand(user1.getPublicId(), coupon.getId())
        );
        UserCoupon userCoupon2 = issueCouponUseCase.execute(
                new IssueCouponCommand(user2.getPublicId(), coupon.getId())
        );
        UserCoupon userCoupon3 = issueCouponUseCase.execute(
                new IssueCouponCommand(user3.getPublicId(), coupon.getId())
        );

        // then - 모두 발급 성공
        assertThat(userCoupon1).isNotNull();
        assertThat(userCoupon2).isNotNull();
        assertThat(userCoupon3).isNotNull();

        // then - 발급 수량 확인
        Coupon updatedCoupon = couponRepository.findById(coupon.getId()).orElseThrow();
        assertThat(updatedCoupon.getIssuedQuantity()).isEqualTo(3);

        // then - 사용자별 쿠폰 확인
        List<UserCoupon> user1Coupons = userCouponRepository.findByUserId(user1.getId());
        List<UserCoupon> user2Coupons = userCouponRepository.findByUserId(user2.getId());
        List<UserCoupon> user3Coupons = userCouponRepository.findByUserId(user3.getId());
        assertThat(user1Coupons).hasSize(1);
        assertThat(user2Coupons).hasSize(1);
        assertThat(user3Coupons).hasSize(1);
    }

    @Test
    @DisplayName("만료된 쿠폰은 발급할 수 없다")
    void issueCoupon_ExpiredCoupon() {
        // given - 사용자 생성
        User user = createUser("이영희", "lee@test.com", "010-3456-7890");
        userRepository.save(user);

        // given - 만료된 쿠폰 생성
        Coupon expiredCoupon = new Coupon(
                "만료된 쿠폰",
                "PERCENTAGE",
                new DiscountRate(10),
                null,
                new Money(0L),
                100,
                LocalDateTime.now().minusDays(10), // 10일 전 시작
                LocalDateTime.now().minusDays(1)   // 어제 종료
        );
        couponRepository.save(expiredCoupon);

        // when & then - 만료된 쿠폰 발급 시도 시 실패
        assertThatThrownBy(() ->
                issueCouponUseCase.execute(new IssueCouponCommand(user.getPublicId(), expiredCoupon.getId()))
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("쿠폰을 발급할 수 없습니다");
    }

    @Test
    @DisplayName("정액 할인 쿠폰을 발급받을 수 있다")
    void issueCoupon_AmountCoupon() {
        // given - 사용자 생성
        User user = createUser("박민수", "park@test.com", "010-4567-8901");
        userRepository.save(user);

        // given - 정액 할인 쿠폰 생성
        Coupon amountCoupon = new Coupon(
                "5000원 할인",
                "AMOUNT",
                null,
                new Money(5000L),
                new Money(30000L), // 최소 주문 금액
                100,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(30)
        );
        couponRepository.save(amountCoupon);

        // when - 쿠폰 발급
        UserCoupon userCoupon = issueCouponUseCase.execute(
                new IssueCouponCommand(user.getPublicId(), amountCoupon.getId())
        );

        // then - 발급 검증
        assertThat(userCoupon).isNotNull();
        assertThat(userCoupon.getCoupon().getCouponType()).isEqualTo("AMOUNT");
        assertThat(userCoupon.getCoupon().getDiscountAmount().getAmount()).isEqualTo(5000L);
        assertThat(userCoupon.getCoupon().getMinOrderAmount().getAmount()).isEqualTo(30000L);
    }

    @Test
    @DisplayName("존재하지 않는 사용자는 쿠폰을 발급받을 수 없다")
    void issueCoupon_UserNotFound() {
        // given - 쿠폰만 생성
        Coupon coupon = createPercentageCoupon("테스트 쿠폰", 10, 100, false);
        couponRepository.save(coupon);

        // when & then - 존재하지 않는 사용자로 발급 시도
        assertThatThrownBy(() ->
                issueCouponUseCase.execute(new IssueCouponCommand("invalid-uuid", coupon.getId()))
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("존재하지 않는 쿠폰은 발급할 수 없다")
    void issueCoupon_CouponNotFound() {
        // given - 사용자만 생성
        User user = createUser("최지은", "choi@test.com", "010-5678-9012");
        userRepository.save(user);

        // when & then - 존재하지 않는 쿠폰 발급 시도
        assertThatThrownBy(() ->
                issueCouponUseCase.execute(new IssueCouponCommand(user.getPublicId(), 999L))
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("쿠폰을 찾을 수 없습니다");
    }

    // Helper methods
    private User createUser(String name, String email, String phone) {
        return new User(name, new Email(email), new Phone(phone));
    }

    private Coupon createPercentageCoupon(String name, int discountRate, int totalQuantity, boolean useQueue) {
        return new Coupon(
                name,
                "PERCENTAGE",
                new DiscountRate(discountRate),
                null,
                new Money(0L),
                totalQuantity,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(30),
                useQueue
        );
    }
}
