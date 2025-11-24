package com.hhplus.ecommerce.concurrency;

import com.hhplus.ecommerce.domain.entity.Coupon;
import com.hhplus.ecommerce.domain.vo.Money;
import com.hhplus.ecommerce.infrastructure.lock.RedisPubSubLock;
import com.hhplus.ecommerce.infrastructure.lock.RedisSimpleLock;
import com.hhplus.ecommerce.infrastructure.lock.RedisSpinLock;
import com.hhplus.ecommerce.infrastructure.persistence.CouponJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DistributedLockComparisonTest {

    private static final Logger log = LoggerFactory.getLogger(DistributedLockComparisonTest.class);

    @Autowired
    private CouponJpaRepository couponRepository;

    @Autowired
    private RedisSimpleLock simpleLock;

    @Autowired
    private RedisSpinLock spinLock;

    @Autowired
    private RedisPubSubLock pubSubLock;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Coupon testCoupon;

    @BeforeEach
    void setUp() {
        couponRepository.deleteAll();
        testCoupon = new Coupon(
                "테스트 쿠폰",
                "AMOUNT",
                null,
                new Money(10000L),
                null,
                10,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(30)
        );
        testCoupon = couponRepository.save(testCoupon);
    }

    @Test
    @DisplayName("Simple Lock: 100 스레드 중 1개만 즉시 성공, 나머지는 즉시 실패")
    void simpleLock_100threads_10stock() throws InterruptedException {
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    String lockKey = "coupon:" + testCoupon.getId();

                    if (simpleLock.tryLock(lockKey)) {
                        try {
                            transactionTemplate.execute(status -> {
                                Coupon coupon = couponRepository.findById(testCoupon.getId()).orElseThrow();

                                if (coupon.getIssuedQuantity() < coupon.getTotalQuantity()) {
                                    coupon.increaseIssuedQuantity();
                                    couponRepository.save(coupon);
                                    successCount.incrementAndGet();

                                    try {
                                        Thread.sleep(10);
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                    }
                                } else {
                                    failCount.incrementAndGet();
                                }
                                return null;
                            });
                        } finally {
                            simpleLock.unlock(lockKey);
                        }
                    } else {
                        failCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.SECONDS);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        Coupon result = couponRepository.findById(testCoupon.getId()).orElseThrow();

        log.info("=== Simple Lock 결과 ===");
        log.info("성공: {}, 실패: {}", successCount.get(), failCount.get());
        log.info("최종 발급 수량: {}/{}", result.getIssuedQuantity(), result.getTotalQuantity());
        log.info("소요 시간: {}ms", duration);

        System.out.println("=== Simple Lock 결과 ===");
        System.out.println("성공: " + successCount.get() + ", 실패: " + failCount.get());
        System.out.println("최종 발급 수량: " + result.getIssuedQuantity() + "/" + result.getTotalQuantity());
        System.out.println("소요 시간: " + duration + "ms");

        assertThat(result.getIssuedQuantity()).isLessThanOrEqualTo(10);
        assertThat(successCount.get()).isLessThanOrEqualTo(10);
        assertThat(duration).isLessThan(1000);
    }

    @Test
    @DisplayName("Spin Lock: 100 스레드가 재시도하며 10개 발급 (Redis 부하 높음)")
    void spinLock_100threads_10stock() throws InterruptedException {
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    String lockKey = "coupon:" + testCoupon.getId();

                    if (spinLock.lock(lockKey)) {
                        try {
                            transactionTemplate.execute(status -> {
                                Coupon coupon = couponRepository.findById(testCoupon.getId()).orElseThrow();

                                if (coupon.getIssuedQuantity() < coupon.getTotalQuantity()) {
                                    coupon.increaseIssuedQuantity();
                                    couponRepository.save(coupon);
                                    successCount.incrementAndGet();

                                    try {
                                        Thread.sleep(10);
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                    }
                                } else {
                                    failCount.incrementAndGet();
                                }
                                return null;
                            });
                        } finally {
                            spinLock.unlock(lockKey);
                        }
                    } else {
                        failCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.SECONDS);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        Coupon result = couponRepository.findById(testCoupon.getId()).orElseThrow();

        log.info("=== Spin Lock 결과 ===");
        log.info("성공: {}, 실패: {}", successCount.get(), failCount.get());
        log.info("최종 발급 수량: {}/{}", result.getIssuedQuantity(), result.getTotalQuantity());
        log.info("소요 시간: {}ms", duration);

        System.out.println("=== Spin Lock 결과 ===");
        System.out.println("성공: " + successCount.get() + ", 실패: " + failCount.get());
        System.out.println("최종 발급 수량: " + result.getIssuedQuantity() + "/" + result.getTotalQuantity());
        System.out.println("소요 시간: " + duration + "ms");

        assertThat(result.getIssuedQuantity()).isEqualTo(10);
        assertThat(successCount.get()).isEqualTo(10);
    }

    @Test
    @DisplayName("Pub/Sub Lock: 100 스레드가 순차 대기하며 10개 발급 (효율적)")
    void pubSubLock_100threads_10stock() throws InterruptedException {
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    String lockKey = "coupon:" + testCoupon.getId();

                    if (pubSubLock.tryLock(lockKey, 5, TimeUnit.SECONDS)) {
                        try {
                            transactionTemplate.execute(status -> {
                                Coupon coupon = couponRepository.findById(testCoupon.getId()).orElseThrow();

                                if (coupon.getIssuedQuantity() < coupon.getTotalQuantity()) {
                                    coupon.increaseIssuedQuantity();
                                    couponRepository.save(coupon);
                                    successCount.incrementAndGet();

                                    try {
                                        Thread.sleep(10);
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                    }
                                } else {
                                    failCount.incrementAndGet();
                                }
                                return null;
                            });
                        } finally {
                            pubSubLock.unlock(lockKey);
                        }
                    } else {
                        failCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();
        executorService.awaitTermination(30, TimeUnit.SECONDS);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        Coupon result = couponRepository.findById(testCoupon.getId()).orElseThrow();

        log.info("=== Pub/Sub Lock 결과 ===");
        log.info("성공: {}, 실패: {}", successCount.get(), failCount.get());
        log.info("최종 발급 수량: {}/{}", result.getIssuedQuantity(), result.getTotalQuantity());
        log.info("소요 시간: {}ms", duration);

        System.out.println("=== Pub/Sub Lock 결과 ===");
        System.out.println("성공: " + successCount.get() + ", 실패: " + failCount.get());
        System.out.println("최종 발급 수량: " + result.getIssuedQuantity() + "/" + result.getTotalQuantity());
        System.out.println("소요 시간: " + duration + "ms");

        assertThat(result.getIssuedQuantity()).isEqualTo(10);
        assertThat(successCount.get()).isEqualTo(10);
    }
}
