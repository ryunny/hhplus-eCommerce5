package com.hhplus.ecommerce.domain.service;

import com.hhplus.ecommerce.BaseIntegrationTest;
import com.hhplus.ecommerce.domain.entity.User;
import com.hhplus.ecommerce.domain.repository.UserRepository;
import com.hhplus.ecommerce.domain.vo.Email;
import com.hhplus.ecommerce.domain.vo.Money;
import com.hhplus.ecommerce.domain.vo.Phone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * UserService 동시성 테스트
 *
 * 실제 멀티스레드 환경에서 사용자 잔액 관리의 동시성 제어를 검증합니다.
 * - 동시 잔액 충전 시 정확성
 * - 동시 잔액 사용 시 정확성
 * - 충전과 사용이 동시에 발생할 때 정확성
 * - 비관적 락을 통한 동시성 제어
 */
class UserServiceConcurrencyTest extends BaseIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("동일한 사용자가 100개 스레드에서 동시에 1000원씩 충전하면 총 100,000원이 충전된다")
    void chargeBalance_Concurrency() throws InterruptedException {
        // given - 사용자 생성 (초기 잔액 0원)
        User user = createUser("테스트유저", "test@test.com", "010-1234-5678");
        userRepository.save(user);

        // given - 동시성 제어 설정
        int totalThreads = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(100);
        CountDownLatch readyLatch = new CountDownLatch(totalThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalThreads);

        // when - 100개 스레드에서 동시에 1000원씩 충전
        for (int i = 0; i < totalThreads; i++) {
            executorService.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await();

                    userService.chargeBalance(user.getId(), new Money(1000L));
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

        // then - 총 잔액이 100,000원
        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updatedUser.getBalance().getAmount()).isEqualTo(100_000L);
    }

    @Test
    @DisplayName("동일한 사용자가 100개 스레드에서 동시에 1000원씩 사용하면 총 100,000원이 차감된다")
    void useBalance_Concurrency() throws InterruptedException {
        // given - 사용자 생성 및 충분한 잔액 충전 (200,000원)
        User user = createUser("부자유저", "rich@test.com", "010-9999-9999");
        user.chargeBalance(new Money(200_000L));
        userRepository.save(user);

        // given - 동시성 제어 설정
        int totalThreads = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(100);
        CountDownLatch readyLatch = new CountDownLatch(totalThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalThreads);

        // when - 100개 스레드에서 동시에 1000원씩 사용
        for (int i = 0; i < totalThreads; i++) {
            executorService.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await();

                    userService.deductBalance(user.getId(), new Money(1000L));
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

        // then - 잔액이 100,000원 (200,000 - 100,000)
        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updatedUser.getBalance().getAmount()).isEqualTo(100_000L);
    }

    @Test
    @DisplayName("잔액이 10,000원일 때 100명이 동시에 1,000원씩 사용하면 10명만 성공한다")
    void useBalance_Concurrency_InsufficientBalance() throws InterruptedException {
        // given - 사용자 생성 및 10,000원 충전
        User user = createUser("가난한유저", "poor@test.com", "010-0000-0000");
        user.chargeBalance(new Money(10_000L));
        userRepository.save(user);

        // given - 동시성 제어 설정
        int totalThreads = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(100);
        CountDownLatch readyLatch = new CountDownLatch(totalThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when - 100명이 동시에 1,000원씩 사용 시도
        for (int i = 0; i < totalThreads; i++) {
            executorService.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await();

                    userService.deductBalance(user.getId(), new Money(1000L));
                    successCount.incrementAndGet();
                } catch (IllegalStateException e) {
                    failCount.incrementAndGet();
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

        // then - 10명만 성공
        assertThat(successCount.get()).isEqualTo(10);
        assertThat(failCount.get()).isEqualTo(90);

        // then - 잔액이 0원
        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updatedUser.getBalance().getAmount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("동시에 충전과 사용이 섞여서 발생해도 최종 잔액은 정확하다")
    void chargeAndUseBalance_Concurrency() throws InterruptedException {
        // given - 사용자 생성 및 초기 잔액 50,000원
        User user = createUser("일반유저", "normal@test.com", "010-5555-5555");
        user.chargeBalance(new Money(50_000L));
        userRepository.save(user);

        // given - 동시성 제어 설정
        int chargeThreads = 50; // 50개 충전 스레드 (각 1000원)
        int useThreads = 30;    // 30개 사용 스레드 (각 1000원)
        int totalThreads = chargeThreads + useThreads;

        ExecutorService executorService = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch readyLatch = new CountDownLatch(totalThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalThreads);

        AtomicInteger successUseCount = new AtomicInteger(0);

        // when - 50개 충전 스레드
        for (int i = 0; i < chargeThreads; i++) {
            executorService.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await();

                    userService.chargeBalance(user.getId(), new Money(1000L));
                } catch (Exception e) {
                    // 예외 무시
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // when - 30개 사용 스레드
        for (int i = 0; i < useThreads; i++) {
            executorService.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await();

                    userService.deductBalance(user.getId(), new Money(1000L));
                    successUseCount.incrementAndGet();
                } catch (Exception e) {
                    // 잔액 부족 시 무시
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await();

        executorService.shutdown();

        // then - 최종 잔액 계산
        // 초기: 50,000
        // 충전: +50,000 (50 * 1000)
        // 사용: -30,000 (30 * 1000, 모두 성공 가정)
        // 예상: 70,000
        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        long expectedBalance = 50_000L + (chargeThreads * 1000L) - (successUseCount.get() * 1000L);
        assertThat(updatedUser.getBalance().getAmount()).isEqualTo(expectedBalance);
    }

    @Test
    @DisplayName("잔액이 1000원일 때 100명이 동시에 1000원씩 사용하면 1명만 성공한다")
    void useBalance_Concurrency_LastMoney() throws InterruptedException {
        // given - 사용자 생성 및 1000원만 충전
        User user = createUser("마지막유저", "last@test.com", "010-1111-1111");
        user.chargeBalance(new Money(1000L));
        userRepository.save(user);

        // given - 동시성 제어 설정
        int totalThreads = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(100);
        CountDownLatch readyLatch = new CountDownLatch(totalThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when - 100명이 동시에 1000원 사용 시도
        for (int i = 0; i < totalThreads; i++) {
            executorService.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await();

                    userService.deductBalance(user.getId(), new Money(1000L));
                    successCount.incrementAndGet();
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

        // then - 1명만 성공
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(99);

        // then - 잔액 0원
        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updatedUser.getBalance().getAmount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("여러 스레드에서 다른 금액을 동시에 충전해도 총 잔액은 정확하다")
    void chargeBalance_Concurrency_DifferentAmounts() throws InterruptedException {
        // given - 사용자 생성
        User user = createUser("다양한충전", "various@test.com", "010-7777-7777");
        userRepository.save(user);

        // given - 동시성 제어 설정
        int totalThreads = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(100);
        CountDownLatch readyLatch = new CountDownLatch(totalThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalThreads);

        AtomicInteger totalCharged = new AtomicInteger(0);

        // when - 100개 스레드에서 다른 금액 충전 (1000, 2000, ..., 10000 반복)
        for (int i = 0; i < totalThreads; i++) {
            final int amount = ((i % 10) + 1) * 1000; // 1000~10000원
            executorService.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await();

                    userService.chargeBalance(user.getId(), new Money((long) amount));
                    totalCharged.addAndGet(amount);
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

        // then - 총 충전액과 잔액이 일치
        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updatedUser.getBalance().getAmount()).isEqualTo(totalCharged.get());
    }

    // Helper method
    private User createUser(String name, String email, String phone) {
        return new User(name, new Email(email), new Phone(phone));
    }
}
