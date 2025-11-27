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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * JoinCouponQueueUseCase 동시성 테스트
 *
 * 실제 멀티스레드 환경에서 대기열 진입의 동시성 제어를 검증합니다.
 * - 대기 순번(queuePosition) 중복 방지
 * - 분산락을 통한 동시성 제어
 * - 중복 진입 방지
 */
class JoinCouponQueueUseCaseConcurrencyTest extends BaseIntegrationTest {

    @Autowired
    private JoinCouponQueueUseCase joinCouponQueueUseCase;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private CouponQueueRepository couponQueueRepository;

    @Test
    @DisplayName("100명이 동시에 대기열에 진입하면 순번이 중복 없이 1~100까지 정확히 할당된다")
    void joinQueue_Concurrency_QueuePosition() throws InterruptedException {
        // given - 대기열 쿠폰 생성
        Coupon coupon = createQueueCoupon("선착순 100명 쿠폰", 30, 100);
        couponRepository.save(coupon);

        // given - 100명의 사용자 생성
        int totalUsers = 100;
        List<User> users = new ArrayList<>();
        for (int i = 0; i < totalUsers; i++) {
            User user = createUser("사용자" + i, "user" + i + "@test.com", "010-0000-" + String.format("%04d", i));
            users.add(userRepository.save(user));
        }

        // given - 동시성 제어를 위한 설정
        ExecutorService executorService = Executors.newFixedThreadPool(100);
        CountDownLatch readyLatch = new CountDownLatch(totalUsers);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalUsers);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when - 100명이 동시에 대기열 진입 시도
        for (User user : users) {
            executorService.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await(); // 모든 스레드가 준비될 때까지 대기

                    JoinCouponQueueCommand command = new JoinCouponQueueCommand(user.getPublicId(), coupon.getId());
                    joinCouponQueueUseCase.execute(command);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(); // 모든 스레드가 준비될 때까지 대기
        startLatch.countDown(); // 동시에 시작
        doneLatch.await(); // 모든 스레드가 완료될 때까지 대기

        executorService.shutdown();

        // then - 모두 성공
        assertThat(successCount.get()).isEqualTo(100);
        assertThat(failCount.get()).isEqualTo(0);

        // then - 대기열 개수 확인
        List<CouponQueue> allQueues = couponQueueRepository.findByCouponIdAndStatus(
                coupon.getId(), CouponQueueStatus.WAITING
        );
        assertThat(allQueues).hasSize(100);

        // then - 순번 중복 없이 1~100까지 정확히 할당되었는지 확인
        Set<Integer> positions = new HashSet<>();
        for (CouponQueue queue : allQueues) {
            positions.add(queue.getQueuePosition());
        }
        assertThat(positions).hasSize(100);
        assertThat(positions).containsExactlyInAnyOrder(
                1, 2, 3, 4, 5, 6, 7, 8, 9, 10,
                11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
                21, 22, 23, 24, 25, 26, 27, 28, 29, 30,
                31, 32, 33, 34, 35, 36, 37, 38, 39, 40,
                41, 42, 43, 44, 45, 46, 47, 48, 49, 50,
                51, 52, 53, 54, 55, 56, 57, 58, 59, 60,
                61, 62, 63, 64, 65, 66, 67, 68, 69, 70,
                71, 72, 73, 74, 75, 76, 77, 78, 79, 80,
                81, 82, 83, 84, 85, 86, 87, 88, 89, 90,
                91, 92, 93, 94, 95, 96, 97, 98, 99, 100
        );
    }

    @Test
    @DisplayName("동일한 사용자가 여러 스레드에서 동시에 대기열 진입을 시도해도 1개만 성공한다")
    void joinQueue_Concurrency_SameUser() throws InterruptedException {
        // given - 사용자 생성
        User user = createUser("중복시도자", "duplicate@test.com", "010-1234-5678");
        userRepository.save(user);

        // given - 대기열 쿠폰 생성
        Coupon coupon = createQueueCoupon("중복 방지 테스트 쿠폰", 20, 100);
        couponRepository.save(coupon);

        // given - 동시성 제어를 위한 설정
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when - 동일 사용자가 10개 스레드에서 동시 진입 시도
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await();

                    JoinCouponQueueCommand command = new JoinCouponQueueCommand(user.getPublicId(), coupon.getId());
                    joinCouponQueueUseCase.execute(command);
                    successCount.incrementAndGet();
                } catch (IllegalStateException e) {
                    if (e.getMessage().contains("이미 대기열에 진입했습니다")) {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await();

        executorService.shutdown();

        // then - 1개만 성공
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(9);

        // then - 해당 사용자의 대기열이 1개만 생성되었는지 확인
        List<CouponQueue> userQueues = couponQueueRepository.findByUserIdAndCouponId(
                user.getId(), coupon.getId()
        ).stream().toList();
        assertThat(userQueues).hasSize(1);
        assertThat(userQueues.get(0).getQueuePosition()).isEqualTo(1);
    }

    @Test
    @DisplayName("여러 사용자가 동시에 진입할 때 순번이 순차적으로 증가한다")
    void joinQueue_Concurrency_SequentialPosition() throws InterruptedException {
        // given - 대기열 쿠폰 생성
        Coupon coupon = createQueueCoupon("순차 테스트 쿠폰", 15, 1000);
        couponRepository.save(coupon);

        // given - 50명의 사용자 생성
        int totalUsers = 50;
        List<User> users = new ArrayList<>();
        for (int i = 0; i < totalUsers; i++) {
            User user = createUser("유저" + i, "testuser" + i + "@test.com", "010-9999-" + String.format("%04d", i));
            users.add(userRepository.save(user));
        }

        // given - 동시성 제어
        ExecutorService executorService = Executors.newFixedThreadPool(50);
        CountDownLatch readyLatch = new CountDownLatch(totalUsers);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalUsers);

        AtomicInteger successCount = new AtomicInteger(0);

        // when - 50명이 동시에 대기열 진입
        for (User user : users) {
            executorService.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await();

                    JoinCouponQueueCommand command = new JoinCouponQueueCommand(user.getPublicId(), coupon.getId());
                    joinCouponQueueUseCase.execute(command);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 예외 발생 시 무시 (동시성 테스트이므로)
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await();

        executorService.shutdown();

        // then - 모두 성공
        assertThat(successCount.get()).isEqualTo(50);

        // then - 대기열 개수 확인
        List<CouponQueue> allQueues = couponQueueRepository.findByCouponIdAndStatus(
                coupon.getId(), CouponQueueStatus.WAITING
        );
        assertThat(allQueues).hasSize(50);

        // then - 순번이 1~50까지 존재하는지 확인
        Set<Integer> positions = new HashSet<>();
        for (CouponQueue queue : allQueues) {
            positions.add(queue.getQueuePosition());
        }
        assertThat(positions).hasSize(50);

        // then - 최소값 1, 최대값 50
        assertThat(positions).contains(1, 50);
        assertThat(positions.stream().min(Integer::compareTo)).hasValue(1);
        assertThat(positions.stream().max(Integer::compareTo)).hasValue(50);
    }

    @Test
    @DisplayName("두 개의 다른 쿠폰에 동시에 진입할 때 각각 순번이 독립적으로 할당된다")
    void joinQueue_Concurrency_MultipleCoupons() throws InterruptedException {
        // given - 두 개의 쿠폰 생성
        Coupon coupon1 = createQueueCoupon("쿠폰A", 10, 100);
        Coupon coupon2 = createQueueCoupon("쿠폰B", 20, 100);
        couponRepository.save(coupon1);
        couponRepository.save(coupon2);

        // given - 30명의 사용자 생성
        int totalUsers = 30;
        List<User> users = new ArrayList<>();
        for (int i = 0; i < totalUsers; i++) {
            User user = createUser("멀티유저" + i, "multi" + i + "@test.com", "010-8888-" + String.format("%04d", i));
            users.add(userRepository.save(user));
        }

        // given - 동시성 제어
        ExecutorService executorService = Executors.newFixedThreadPool(60);
        CountDownLatch readyLatch = new CountDownLatch(totalUsers * 2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalUsers * 2);

        AtomicInteger successCount = new AtomicInteger(0);

        // when - 30명이 두 개의 쿠폰에 동시에 진입 (총 60개 요청)
        for (User user : users) {
            // 쿠폰1 진입
            executorService.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await();

                    joinCouponQueueUseCase.execute(new JoinCouponQueueCommand(user.getPublicId(), coupon1.getId()));
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 예외 무시
                } finally {
                    doneLatch.countDown();
                }
            });

            // 쿠폰2 진입
            executorService.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await();

                    joinCouponQueueUseCase.execute(new JoinCouponQueueCommand(user.getPublicId(), coupon2.getId()));
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 예외 무시
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await();

        executorService.shutdown();

        // then - 모두 성공
        assertThat(successCount.get()).isEqualTo(60);

        // then - 각 쿠폰별로 30명씩 대기열 생성
        List<CouponQueue> coupon1Queues = couponQueueRepository.findByCouponIdAndStatus(
                coupon1.getId(), CouponQueueStatus.WAITING
        );
        List<CouponQueue> coupon2Queues = couponQueueRepository.findByCouponIdAndStatus(
                coupon2.getId(), CouponQueueStatus.WAITING
        );

        assertThat(coupon1Queues).hasSize(30);
        assertThat(coupon2Queues).hasSize(30);

        // then - 각 쿠폰의 순번이 1~30까지 독립적으로 할당
        Set<Integer> coupon1Positions = new HashSet<>();
        Set<Integer> coupon2Positions = new HashSet<>();

        for (CouponQueue queue : coupon1Queues) {
            coupon1Positions.add(queue.getQueuePosition());
        }
        for (CouponQueue queue : coupon2Queues) {
            coupon2Positions.add(queue.getQueuePosition());
        }

        assertThat(coupon1Positions).hasSize(30);
        assertThat(coupon2Positions).hasSize(30);
        assertThat(coupon1Positions).contains(1, 30);
        assertThat(coupon2Positions).contains(1, 30);
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
