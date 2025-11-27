package com.hhplus.ecommerce.application.usecase.coupon;

import com.hhplus.ecommerce.BaseIntegrationTest;
import com.hhplus.ecommerce.application.command.JoinCouponQueueCommand;
import com.hhplus.ecommerce.domain.entity.Coupon;
import com.hhplus.ecommerce.domain.entity.CouponQueue;
import com.hhplus.ecommerce.domain.entity.User;
import com.hhplus.ecommerce.domain.enums.CouponQueueStatus;
import com.hhplus.ecommerce.domain.repository.CouponQueueRepository;
import com.hhplus.ecommerce.domain.repository.CouponRepository;
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
 * JoinCouponQueueUseCase 통합 테스트
 *
 * 대기열 진입 핵심 기능을 검증합니다.
 * - 대기열 진입
 * - 대기 순번 할당
 * - 중복 진입 방지
 * - 대기 상태 확인
 */
class JoinCouponQueueUseCaseIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private JoinCouponQueueUseCase joinCouponQueueUseCase;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private CouponQueueRepository couponQueueRepository;

    @Test
    @DisplayName("선착순 쿠폰 대기열에 진입할 수 있다")
    void joinQueue_Success() {
        // given - 사용자 생성
        User user = createUser("홍길동", "hong@test.com", "010-1234-5678");
        userRepository.save(user);

        // given - 대기열 방식 쿠폰 생성
        Coupon coupon = createQueueCoupon("선착순 쿠폰", 20, 100);
        couponRepository.save(coupon);

        // given - 대기열 진입 명령
        JoinCouponQueueCommand command = new JoinCouponQueueCommand(user.getPublicId(), coupon.getId());

        // when - 대기열 진입
        CouponQueue couponQueue = joinCouponQueueUseCase.execute(command);

        // then - 진입 검증
        assertThat(couponQueue).isNotNull();
        assertThat(couponQueue.getUser().getId()).isEqualTo(user.getId());
        assertThat(couponQueue.getCoupon().getId()).isEqualTo(coupon.getId());
        assertThat(couponQueue.getStatus()).isEqualTo(CouponQueueStatus.WAITING);
        assertThat(couponQueue.getQueuePosition()).isEqualTo(1);
        assertThat(couponQueue.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("대기 순번이 순차적으로 할당된다")
    void joinQueue_QueuePosition() {
        // given - 여러 사용자 생성
        User user1 = createUser("사용자1", "user1@test.com", "010-1111-1111");
        User user2 = createUser("사용자2", "user2@test.com", "010-2222-2222");
        User user3 = createUser("사용자3", "user3@test.com", "010-3333-3333");
        userRepository.save(user1);
        userRepository.save(user2);
        userRepository.save(user3);

        // given - 대기열 쿠폰 생성
        Coupon coupon = createQueueCoupon("인기 쿠폰", 30, 50);
        couponRepository.save(coupon);

        // when - 순차적으로 대기열 진입
        CouponQueue queue1 = joinCouponQueueUseCase.execute(
                new JoinCouponQueueCommand(user1.getPublicId(), coupon.getId())
        );
        CouponQueue queue2 = joinCouponQueueUseCase.execute(
                new JoinCouponQueueCommand(user2.getPublicId(), coupon.getId())
        );
        CouponQueue queue3 = joinCouponQueueUseCase.execute(
                new JoinCouponQueueCommand(user3.getPublicId(), coupon.getId())
        );

        // then - 순번 확인
        assertThat(queue1.getQueuePosition()).isEqualTo(1);
        assertThat(queue2.getQueuePosition()).isEqualTo(2);
        assertThat(queue3.getQueuePosition()).isEqualTo(3);

        // then - 모두 WAITING 상태
        assertThat(queue1.getStatus()).isEqualTo(CouponQueueStatus.WAITING);
        assertThat(queue2.getStatus()).isEqualTo(CouponQueueStatus.WAITING);
        assertThat(queue3.getStatus()).isEqualTo(CouponQueueStatus.WAITING);
    }

    @Test
    @DisplayName("동일한 쿠폰 대기열에 중복 진입할 수 없다")
    void joinQueue_DuplicateJoin() {
        // given - 사용자 생성
        User user = createUser("김철수", "kim@test.com", "010-2345-6789");
        userRepository.save(user);

        // given - 쿠폰 생성
        Coupon coupon = createQueueCoupon("한정 쿠폰", 50, 10);
        couponRepository.save(coupon);

        // given - 첫 번째 진입
        JoinCouponQueueCommand command = new JoinCouponQueueCommand(user.getPublicId(), coupon.getId());
        joinCouponQueueUseCase.execute(command);

        // when & then - 두 번째 진입 시도 시 예외 발생
        assertThatThrownBy(() -> joinCouponQueueUseCase.execute(command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 대기열에 진입했습니다");
    }

    @Test
    @DisplayName("여러 사용자가 동일한 쿠폰 대기열에 진입할 수 있다")
    void joinQueue_MultipleUsers() {
        // given - 여러 사용자 생성
        User user1 = createUser("사용자A", "userA@test.com", "010-1111-1111");
        User user2 = createUser("사용자B", "userB@test.com", "010-2222-2222");
        User user3 = createUser("사용자C", "userC@test.com", "010-3333-3333");
        User user4 = createUser("사용자D", "userD@test.com", "010-4444-4444");
        User user5 = createUser("사용자E", "userE@test.com", "010-5555-5555");
        userRepository.save(user1);
        userRepository.save(user2);
        userRepository.save(user3);
        userRepository.save(user4);
        userRepository.save(user5);

        // given - 쿠폰 생성
        Coupon coupon = createQueueCoupon("대박 쿠폰", 40, 100);
        couponRepository.save(coupon);

        // when - 여러 사용자가 진입
        CouponQueue queue1 = joinCouponQueueUseCase.execute(
                new JoinCouponQueueCommand(user1.getPublicId(), coupon.getId())
        );
        CouponQueue queue2 = joinCouponQueueUseCase.execute(
                new JoinCouponQueueCommand(user2.getPublicId(), coupon.getId())
        );
        CouponQueue queue3 = joinCouponQueueUseCase.execute(
                new JoinCouponQueueCommand(user3.getPublicId(), coupon.getId())
        );
        CouponQueue queue4 = joinCouponQueueUseCase.execute(
                new JoinCouponQueueCommand(user4.getPublicId(), coupon.getId())
        );
        CouponQueue queue5 = joinCouponQueueUseCase.execute(
                new JoinCouponQueueCommand(user5.getPublicId(), coupon.getId())
        );

        // then - 모두 진입 성공
        assertThat(queue1).isNotNull();
        assertThat(queue2).isNotNull();
        assertThat(queue3).isNotNull();
        assertThat(queue4).isNotNull();
        assertThat(queue5).isNotNull();

        // then - 순번 확인
        assertThat(queue1.getQueuePosition()).isEqualTo(1);
        assertThat(queue2.getQueuePosition()).isEqualTo(2);
        assertThat(queue3.getQueuePosition()).isEqualTo(3);
        assertThat(queue4.getQueuePosition()).isEqualTo(4);
        assertThat(queue5.getQueuePosition()).isEqualTo(5);

        // then - 대기열 전체 개수 확인
        List<CouponQueue> allQueues = couponQueueRepository.findByCouponIdAndStatus(
                coupon.getId(), CouponQueueStatus.WAITING
        );
        assertThat(allQueues).hasSize(5);
    }

    @Test
    @DisplayName("존재하지 않는 사용자는 대기열에 진입할 수 없다")
    void joinQueue_UserNotFound() {
        // given - 쿠폰만 생성
        Coupon coupon = createQueueCoupon("테스트 쿠폰", 10, 100);
        couponRepository.save(coupon);

        // when & then - 존재하지 않는 사용자로 진입 시도
        assertThatThrownBy(() ->
                joinCouponQueueUseCase.execute(new JoinCouponQueueCommand("invalid-uuid", coupon.getId()))
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("존재하지 않는 쿠폰 대기열에 진입할 수 없다")
    void joinQueue_CouponNotFound() {
        // given - 사용자만 생성
        User user = createUser("이영희", "lee@test.com", "010-3456-7890");
        userRepository.save(user);

        // when & then - 존재하지 않는 쿠폰 진입 시도
        assertThatThrownBy(() ->
                joinCouponQueueUseCase.execute(new JoinCouponQueueCommand(user.getPublicId(), 999L))
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("쿠폰을 찾을 수 없습니다");
    }

    @Test
    @DisplayName("대기열 진입 후 상태를 확인할 수 있다")
    void joinQueue_CheckStatus() {
        // given - 사용자 생성
        User user = createUser("박민수", "park@test.com", "010-4567-8901");
        userRepository.save(user);

        // given - 쿠폰 생성
        Coupon coupon = createQueueCoupon("확인 테스트 쿠폰", 15, 50);
        couponRepository.save(coupon);

        // when - 대기열 진입
        CouponQueue joinedQueue = joinCouponQueueUseCase.execute(
                new JoinCouponQueueCommand(user.getPublicId(), coupon.getId())
        );

        // then - Repository에서 조회 시 동일한 상태
        CouponQueue foundQueue = couponQueueRepository.findByUserIdAndCouponId(
                user.getId(), coupon.getId()
        ).orElseThrow();

        assertThat(foundQueue.getId()).isEqualTo(joinedQueue.getId());
        assertThat(foundQueue.getQueuePosition()).isEqualTo(joinedQueue.getQueuePosition());
        assertThat(foundQueue.getStatus()).isEqualTo(CouponQueueStatus.WAITING);
    }

    @Test
    @DisplayName("한 사용자가 여러 쿠폰 대기열에 각각 진입할 수 있다")
    void joinQueue_MultipleCoupons() {
        // given - 사용자 생성
        User user = createUser("최지은", "choi@test.com", "010-5678-9012");
        userRepository.save(user);

        // given - 여러 쿠폰 생성
        Coupon coupon1 = createQueueCoupon("쿠폰A", 10, 100);
        Coupon coupon2 = createQueueCoupon("쿠폰B", 20, 100);
        Coupon coupon3 = createQueueCoupon("쿠폰C", 30, 100);
        couponRepository.save(coupon1);
        couponRepository.save(coupon2);
        couponRepository.save(coupon3);

        // when - 여러 쿠폰 대기열에 진입
        CouponQueue queue1 = joinCouponQueueUseCase.execute(
                new JoinCouponQueueCommand(user.getPublicId(), coupon1.getId())
        );
        CouponQueue queue2 = joinCouponQueueUseCase.execute(
                new JoinCouponQueueCommand(user.getPublicId(), coupon2.getId())
        );
        CouponQueue queue3 = joinCouponQueueUseCase.execute(
                new JoinCouponQueueCommand(user.getPublicId(), coupon3.getId())
        );

        // then - 모두 진입 성공
        assertThat(queue1).isNotNull();
        assertThat(queue2).isNotNull();
        assertThat(queue3).isNotNull();

        // then - 각각 다른 쿠폰
        assertThat(queue1.getCoupon().getId()).isEqualTo(coupon1.getId());
        assertThat(queue2.getCoupon().getId()).isEqualTo(coupon2.getId());
        assertThat(queue3.getCoupon().getId()).isEqualTo(coupon3.getId());

        // then - 각 쿠폰에서 모두 첫 번째 순번
        assertThat(queue1.getQueuePosition()).isEqualTo(1);
        assertThat(queue2.getQueuePosition()).isEqualTo(1);
        assertThat(queue3.getQueuePosition()).isEqualTo(1);
    }

    // Helper methods
    private User createUser(String name, String email, String phone) {
        return new User(name, new Email(email), new Phone(phone));
    }

    private Coupon createQueueCoupon(String name, int discountRate, int totalQuantity) {
        return new Coupon(
                name,
                "PERCENTAGE",
                new DiscountRate(discountRate),
                null,
                new Money(0L),
                totalQuantity,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(30),
                true  // useQueue = true
        );
    }
}
