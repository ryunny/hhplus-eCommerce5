package com.hhplus.ecommerce.domain.service;

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
import com.hhplus.ecommerce.infrastructure.redis.RedisKeyGenerator;
import com.hhplus.ecommerce.presentation.dto.CouponQueueResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis Sorted Set 기반 쿠폰 대기열 동시성 테스트
 *
 * 목적:
 * 1. 1000명 동시 진입 시 순번이 정확히 부여되는지 검증
 * 2. 중복 진입 방지 검증
 * 3. 배치 처리 시 선착순 보장 검증
 * 4. 동시성 처리 성능 측정
 */
@SpringBootTest
class RedisCouponQueueServiceConcurrencyTest {

    @Autowired
    private RedisCouponQueueService queueService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private Coupon testCoupon;
    private List<User> testUsers;

    @BeforeEach
    void setUp() {
        // 테스트 쿠폰 생성 (수량 100개)
        testCoupon = new Coupon(
                "선착순 100명 쿠폰",
                "PERCENTAGE",
                new DiscountRate(10),
                null,
                new Money(0L),
                100,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(30),
                true
        );
        testCoupon = couponRepository.save(testCoupon);

        // 테스트 사용자 1000명 생성
        testUsers = new ArrayList<>();
        for (int i = 1; i <= 1000; i++) {
            User user = new User(
                    "사용자" + i,
                    new Email("user" + i + "@test.com"),
                    new Phone("010-0000-" + String.format("%04d", i))
            );
            testUsers.add(userRepository.save(user));
        }

        // Redis 대기열 초기화
        queueService.clearQueue(testCoupon.getId());
    }

    @AfterEach
    void tearDown() {
        // Redis 대기열 정리
        if (testCoupon != null) {
            queueService.clearQueue(testCoupon.getId());
        }
    }

    @Test
    @DisplayName("1000명이 동시에 대기열 진입 - 순번이 정확히 부여된다")
    void joinQueue_1000ConcurrentUsers_AssignsCorrectPositions() throws InterruptedException {
        // Given
        int threadCount = 1000;
        ExecutorService executorService = Executors.newFixedThreadPool(100);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // When - 1000명 동시 진입
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            int userIndex = i;
            executorService.submit(() -> {
                try {
                    User user = testUsers.get(userIndex);
                    CouponQueueResponse response = queueService.joinQueue(user.getId(), testCoupon.getId());

                    assertThat(response.queuePosition()).isNotNull();
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        long duration = System.currentTimeMillis() - startTime;

        // Then
        System.out.println("=== 동시 진입 테스트 결과 ===");
        System.out.println("성공: " + successCount.get() + "명");
        System.out.println("실패: " + failCount.get() + "명");
        System.out.println("소요 시간: " + duration + "ms");
        System.out.println("평균 처리 시간: " + (duration / (double) threadCount) + "ms/명");

        // 모두 성공해야 함
        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(failCount.get()).isZero();

        // Redis에 1000명 모두 대기 중
        Long totalWaiting = queueService.getTotalWaiting(testCoupon.getId());
        assertThat(totalWaiting).isEqualTo(threadCount);

        // 첫 번째 사용자 순번 확인
        Integer firstUserPosition = queueService.getQueuePosition(testUsers.get(0).getId(), testCoupon.getId());
        assertThat(firstUserPosition).isBetween(1, threadCount);
    }

    @Test
    @DisplayName("같은 사용자가 중복 진입 시도 - 순번은 변경되지 않는다")
    void joinQueue_DuplicateJoin_KeepsSamePosition() {
        // Given
        User user = testUsers.get(0);

        // When - 첫 진입
        CouponQueueResponse firstResponse = queueService.joinQueue(user.getId(), testCoupon.getId());

        // When - 중복 진입 (에러 없이 기존 순번 반환)
        CouponQueueResponse secondResponse = queueService.joinQueue(user.getId(), testCoupon.getId());

        // Then - 순번 동일
        assertThat(firstResponse.queuePosition()).isEqualTo(secondResponse.queuePosition());

        // 대기열에는 1명만 있어야 함
        Long totalWaiting = queueService.getTotalWaiting(testCoupon.getId());
        assertThat(totalWaiting).isEqualTo(1);
    }

    @Test
    @DisplayName("배치 처리 시 선착순으로 발급된다")
    void processBatch_IssuesCouponsInFIFOOrder() {
        // Given - 100명 진입
        for (int i = 0; i < 100; i++) {
            queueService.joinQueue(testUsers.get(i).getId(), testCoupon.getId());
        }

        // Redis에서 첫 10명 확인
        String queueKey = RedisKeyGenerator.couponQueue(testCoupon.getId());
        Set<String> top10Before = redisTemplate.opsForZSet().range(queueKey, 0, 9);
        assertThat(top10Before).hasSize(10);

        // When - 상위 10명 발급
        int processed = queueService.processBatch(testCoupon.getId(), 10);

        // Then
        assertThat(processed).isEqualTo(10);

        // 대기열에는 90명 남음
        Long remainingWaiting = queueService.getTotalWaiting(testCoupon.getId());
        assertThat(remainingWaiting).isEqualTo(90);

        // DB에 10개 쿠폰 발급됨
        List<UserCoupon> issuedCoupons = userCouponRepository.findAll().stream()
                .filter(uc -> uc.getCoupon().getId().equals(testCoupon.getId()))
                .toList();
        assertThat(issuedCoupons).hasSize(10);

        // 발급받은 사용자 확인 (첫 10명이어야 함)
        for (UserCoupon uc : issuedCoupons) {
            Long userId = uc.getUser().getId();
            assertThat(testUsers.subList(0, 10).stream().anyMatch(u -> u.getId().equals(userId)))
                    .isTrue();
        }
    }

    @Test
    @DisplayName("수량 소진 시 더 이상 발급되지 않는다")
    void processBatch_StopsWhenCouponDepleted() {
        // Given - 쿠폰 수량 10개로 제한
        testCoupon = new Coupon(
                "선착순 10명 쿠폰",
                "PERCENTAGE",
                new DiscountRate(10),
                null,
                new Money(0L),
                10,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(30),
                true
        );
        testCoupon = couponRepository.save(testCoupon);

        // 100명 진입
        for (int i = 0; i < 100; i++) {
            queueService.joinQueue(testUsers.get(i).getId(), testCoupon.getId());
        }

        // When - 상위 20명 발급 시도 (실제로는 10명만 발급 가능)
        int processed = queueService.processBatch(testCoupon.getId(), 20);

        // Then - 10명만 발급됨
        assertThat(processed).isLessThanOrEqualTo(10);

        List<UserCoupon> issuedCoupons = userCouponRepository.findAll().stream()
                .filter(uc -> uc.getCoupon().getId().equals(testCoupon.getId()))
                .toList();
        assertThat(issuedCoupons).hasSize(10);

        // 쿠폰이 소진됨
        Coupon reloadedCoupon = couponRepository.findById(testCoupon.getId()).orElseThrow();
        assertThat(reloadedCoupon.getIssuedQuantity()).isEqualTo(10);
        assertThat(reloadedCoupon.isIssuable()).isFalse();
    }

    @Test
    @DisplayName("이미 발급받은 사용자는 대기열에서 제거된다")
    void processBatch_RemovesAlreadyIssuedUsers() {
        // Given - 10명 진입
        for (int i = 0; i < 10; i++) {
            queueService.joinQueue(testUsers.get(i).getId(), testCoupon.getId());
        }

        // 첫 번째 사용자에게 직접 쿠폰 발급 (DB)
        User firstUser = testUsers.get(0);
        UserCoupon existingCoupon = new UserCoupon(
                firstUser,
                testCoupon,
                CouponStatus.UNUSED,
                testCoupon.getEndDate()
        );
        userCouponRepository.save(existingCoupon);

        testCoupon.increaseIssuedQuantity();
        couponRepository.save(testCoupon);

        // When - 배치 처리
        int processed = queueService.processBatch(testCoupon.getId(), 10);

        // Then - 첫 번째 사용자는 이미 발급받아서 건너뛰고, 나머지 9명만 발급됨
        assertThat(processed).isLessThanOrEqualTo(9);

        // 대기열에서 제거 확인 (실패한 사용자는 제거됨)
        Long remainingWaiting = queueService.getTotalWaiting(testCoupon.getId());
        assertThat(remainingWaiting).isLessThan(10);
    }

    @Test
    @DisplayName("순번 조회가 실시간으로 업데이트된다")
    void getQueuePosition_UpdatesInRealTime() {
        // Given - 100명 진입
        for (int i = 0; i < 100; i++) {
            queueService.joinQueue(testUsers.get(i).getId(), testCoupon.getId());
        }

        // 50번째 사용자 순번 확인
        User user50 = testUsers.get(49);
        Integer positionBefore = queueService.getQueuePosition(user50.getId(), testCoupon.getId());

        // When - 상위 10명 발급
        queueService.processBatch(testCoupon.getId(), 10);

        // Then - 순번이 10 줄어듦
        Integer positionAfter = queueService.getQueuePosition(user50.getId(), testCoupon.getId());
        assertThat(positionAfter).isEqualTo(positionBefore - 10);
    }
}
