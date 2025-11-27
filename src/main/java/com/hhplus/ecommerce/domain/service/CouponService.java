package com.hhplus.ecommerce.domain.service;

import com.hhplus.ecommerce.domain.entity.Coupon;
import com.hhplus.ecommerce.domain.entity.CouponQueue;
import com.hhplus.ecommerce.domain.entity.User;
import com.hhplus.ecommerce.domain.entity.UserCoupon;
import com.hhplus.ecommerce.domain.enums.CouponQueueStatus;
import com.hhplus.ecommerce.domain.enums.CouponStatus;
import com.hhplus.ecommerce.domain.repository.CouponQueueRepository;
import com.hhplus.ecommerce.domain.repository.CouponRepository;
import com.hhplus.ecommerce.domain.repository.UserCouponRepository;
import com.hhplus.ecommerce.domain.vo.Money;
import com.hhplus.ecommerce.infrastructure.lock.RedisPubSubLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 쿠폰 관련 비즈니스 로직을 처리하는 서비스
 */
@Slf4j
@Service
public class CouponService {

    private final UserCouponRepository userCouponRepository;
    private final CouponRepository couponRepository;
    private final CouponQueueRepository couponQueueRepository;
    private final UserService userService;
    private final RedisPubSubLock pubSubLock;

    public CouponService(UserCouponRepository userCouponRepository,
                        CouponRepository couponRepository,
                        CouponQueueRepository couponQueueRepository,
                        UserService userService,
                        RedisPubSubLock pubSubLock) {
        this.userCouponRepository = userCouponRepository;
        this.couponRepository = couponRepository;
        this.couponQueueRepository = couponQueueRepository;
        this.userService = userService;
        this.pubSubLock = pubSubLock;
    }

    // ===== 쿠폰 조회 =====

    /**
     * 쿠폰 조회
     *
     * @param couponId 쿠폰 ID
     * @return 쿠폰
     */
    @Cacheable(value = "coupons", key = "#couponId")
    @Transactional(readOnly = true)
    public Coupon getCoupon(Long couponId) {
        return couponRepository.findById(couponId)
                .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다: " + couponId));
    }

    /**
     * 발급 가능한 쿠폰 목록 조회
     *
     * @return 발급 가능한 쿠폰 목록
     */
    @Cacheable(value = "issuableCoupons", key = "'all'")
    @Transactional(readOnly = true)
    public List<Coupon> getIssuableCoupons() {
        return couponRepository.findIssuableCoupons(LocalDateTime.now());
    }

    /**
     * 사용자의 쿠폰 목록 조회
     *
     * @param userId 사용자 ID
     * @return 사용자 쿠폰 목록
     */
    @Transactional(readOnly = true)
    public List<UserCoupon> getUserCoupons(Long userId) {
        return userCouponRepository.findByUserId(userId);
    }

    /**
     * 사용자의 사용 가능한 쿠폰 목록 조회
     *
     * @param userId 사용자 ID
     * @return 사용 가능한 쿠폰 목록
     */
    @Transactional(readOnly = true)
    public List<UserCoupon> getAvailableCoupons(Long userId) {
        return userCouponRepository.findByUserIdAndStatus(userId, CouponStatus.UNUSED);
    }

    /**
     * 사용자 쿠폰 조회
     *
     * @param userCouponId 사용자 쿠폰 ID
     * @return UserCoupon
     */
    @Transactional(readOnly = true)
    public UserCoupon getUserCoupon(Long userCouponId) {
        return userCouponRepository.findById(userCouponId)
                .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다: " + userCouponId));
    }

    // ===== 쿠폰 사용 =====

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
        // 더티 체킹으로 자동 저장

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
        // 더티 체킹으로 자동 저장
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

    // ===== 쿠폰 발급 (즉시 발급) =====

    /**
     * 즉시 쿠폰 발급
     *
     * 트랜잭션 범위를 최소화하여 DB 락 시간을 줄입니다.
     * - 트랜잭션 밖: 사용자 조회, 사전 검증 (중복, 발급 가능 여부)
     * - 트랜잭션 안: 비관적 락 + 재검증 + 쓰기만 수행
     *
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID
     * @return 발급된 UserCoupon
     */
    public UserCoupon issueCoupon(Long userId, Long couponId) {
        // 1. 사용자 조회 (트랜잭션 밖)
        User user = userService.getUser(userId);

        // 2. 쿠폰 정보 사전 조회 (트랜잭션 밖 - 일반 SELECT)
        Coupon coupon = getCoupon(couponId);

        // 3. 사전 검증: 이미 발급받았는지 확인 (트랜잭션 밖)
        Optional<UserCoupon> existingCoupon = userCouponRepository.findByUserIdAndCouponId(userId, couponId);
        if (existingCoupon.isPresent()) {
            throw new IllegalStateException("이미 발급받은 쿠폰입니다.");
        }

        // 4. 사전 검증: 발급 가능 여부 확인 (트랜잭션 밖)
        if (!coupon.isIssuable()) {
            throw new IllegalStateException("쿠폰의 모든 수량이 소진되었습니다.");
        }

        // 5. 실제 쓰기 작업만 트랜잭션 안에서 (락 시간 최소화)
        return issueCouponWithLock(user, couponId);
    }

    /**
     * 쿠폰 발급 (Redis Pub/Sub Lock 사용 - 분산 환경 대응)
     *
     * Redis Pub/Sub Lock을 사용하여 분산 환경에서도 동시성을 제어합니다.
     * - Redis 락 획득 → DB 트랜잭션 (재검증 + 쓰기) → Redis 락 해제
     *
     * @param user 사용자
     * @param couponId 쿠폰 ID
     * @return 발급된 UserCoupon
     */
    private UserCoupon issueCouponWithLock(User user, Long couponId) {
        String lockKey = "coupon:issue:" + couponId;

        // Redis Pub/Sub Lock 획득 (최대 5초 대기)
        if (!pubSubLock.tryLock(lockKey, 5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("쿠폰 발급 처리 중입니다. 잠시 후 다시 시도해주세요.");
        }

        try {
            // DB 트랜잭션 실행 (락 보호 영역)
            return issueCouponTransaction(user, couponId);
        } finally {
            // Redis 락 해제 (반드시 실행)
            pubSubLock.unlock(lockKey);
        }
    }

    /**
     * 쿠폰 발급 트랜잭션 (Redis 락으로 보호됨)
     *
     * @param user 사용자
     * @param couponId 쿠폰 ID
     * @return 발급된 UserCoupon
     */
    @CacheEvict(value = {"coupons", "issuableCoupons"}, allEntries = true)
    @Transactional
    private UserCoupon issueCouponTransaction(User user, Long couponId) {
        // 1. 쿠폰 조회 (일반 SELECT - Redis 락이 동시성 보장)
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다: " + couponId));

        // 2. 재검증: 발급 가능 여부 (동시성 문제 대비)
        if (!coupon.isIssuable()) {
            throw new IllegalStateException("쿠폰의 모든 수량이 소진되었습니다.");
        }

        // 3. 쓰기 작업 (원자적 실행)
        // 3-1. 발급 수량 증가
        coupon.increaseIssuedQuantity();
        couponRepository.save(coupon);

        // 3-2. 사용자 쿠폰 생성
        UserCoupon userCoupon = new UserCoupon(
                user,
                coupon,
                CouponStatus.UNUSED,
                coupon.getEndDate()
        );
        userCouponRepository.save(userCoupon);

        return userCoupon;
    }

    // ===== 쿠폰 만료 =====

    /**
     * 만료된 쿠폰 처리 (Redis Pub/Sub Lock 사용 - 분산 환경 대응)
     *
     * Redis Pub/Sub Lock을 사용하여 여러 서버에서 동시에 배치를 실행하지 않도록 제어합니다.
     * - Redis 락 획득 → 만료 쿠폰 처리 → Redis 락 해제
     *
     * @return 만료 처리된 쿠폰 개수
     */
    public int expireOldCoupons() {
        String lockKey = "coupon:batch:expire";

        // Redis Pub/Sub Lock 획득 (최대 10초 대기)
        if (!pubSubLock.tryLock(lockKey, 10, TimeUnit.SECONDS)) {
            log.warn("쿠폰 만료 배치 락 획득 실패: 다른 서버에서 실행 중");
            return 0;
        }

        try {
            // 만료 처리 실행 (락 보호 영역)
            return expireOldCouponsTransaction();
        } finally {
            // Redis 락 해제 (반드시 실행)
            pubSubLock.unlock(lockKey);
        }
    }

    /**
     * 만료된 쿠폰 처리 트랜잭션 (Redis 락으로 보호됨)
     *
     * @return 만료 처리된 쿠폰 개수
     */
    @Transactional
    private int expireOldCouponsTransaction() {
        LocalDateTime now = LocalDateTime.now();
        int expiredCount = 0;

        List<UserCoupon> allUnusedCoupons = userCouponRepository.findAll().stream()
                .filter(uc -> uc.getStatus() == CouponStatus.UNUSED)
                .toList();

        for (UserCoupon userCoupon : allUnusedCoupons) {
            if (userCoupon.getExpiresAt().isBefore(now)) {
                try {
                    // 만료 처리
                    userCoupon.expire();
                    userCouponRepository.save(userCoupon);

                    // 쿠폰 발급 수량 감소 (비관적 락)
                    Long couponId = userCoupon.getCoupon().getId();
                    Coupon coupon = couponRepository.findByIdWithLock(couponId)
                            .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다: " + couponId));
                    coupon.decreaseIssuedQuantity();
                    couponRepository.save(coupon);

                    expiredCount++;
                } catch (Exception e) {
                    log.error("쿠폰 만료 처리 실패: userCouponId={}", userCoupon.getId(), e);
                    // 다음 배치에서 재시도
                }
            }
        }

        return expiredCount;
    }

    // ===== 대기열 시스템 =====

    /**
     * 선착순 쿠폰 대기열 진입
     *
     * 트랜잭션 범위를 최소화하여 DB 커넥션 점유 시간을 줄입니다.
     * - 트랜잭션 밖: 사용자/쿠폰 조회, 기존 대기열 검증
     * - 트랜잭션 안: 대기 인원 계산 + 대기열 생성 (쓰기만)
     *
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID
     * @return 생성된 CouponQueue (대기 정보 포함)
     */
    public CouponQueue joinQueue(Long userId, Long couponId) {
        // 1. 사용자 조회 (트랜잭션 밖)
        User user = userService.getUser(userId);

        // 2. 쿠폰 조회 (트랜잭션 밖)
        Coupon coupon = getCoupon(couponId);

        // 3. 기존 대기열 검증 (트랜잭션 밖)
        validateExistingQueue(userId, couponId);

        // 4. 대기열 생성 (락 + 트랜잭션)
        return createQueueWithLock(user, coupon);
    }

    /**
     * 기존 대기열 검증
     *
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID
     */
    private void validateExistingQueue(Long userId, Long couponId) {
        Optional<CouponQueue> existingQueue = couponQueueRepository.findByUserIdAndCouponId(userId, couponId);
        if (existingQueue.isPresent()) {
            CouponQueue existing = existingQueue.get();
            if (existing.getStatus() == CouponQueueStatus.WAITING || existing.getStatus() == CouponQueueStatus.PROCESSING) {
                throw new IllegalStateException("이미 대기열에 진입했습니다. 현재 순번: " + existing.getQueuePosition());
            }
            if (existing.getStatus() == CouponQueueStatus.COMPLETED) {
                throw new IllegalStateException("이미 발급받은 쿠폰입니다.");
            }
        }
    }

    /**
     * 대기열 생성 (Redis Pub/Sub Lock 사용 - 분산 환경 대응)
     *
     * Redis Pub/Sub Lock을 사용하여 분산 환경에서도 동시성을 제어합니다.
     * - Redis 락 획득 → DB 트랜잭션 (대기 인원 계산 + 대기열 생성) → Redis 락 해제
     *
     * @param user 사용자
     * @param coupon 쿠폰
     * @return 생성된 CouponQueue
     */
    private CouponQueue createQueueWithLock(User user, Coupon coupon) {
        String lockKey = "coupon:queue:join:" + coupon.getId();

        // Redis Pub/Sub Lock 획득 (최대 5초 대기)
        if (!pubSubLock.tryLock(lockKey, 5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("대기열 진입 처리 중입니다. 잠시 후 다시 시도해주세요.");
        }

        try {
            // DB 트랜잭션 실행 (락 보호 영역)
            return createQueue(user, coupon);
        } finally {
            // Redis 락 해제 (반드시 실행)
            pubSubLock.unlock(lockKey);
        }
    }

    /**
     * 대기열 생성 트랜잭션 (Redis 락으로 보호됨)
     *
     * @param user 사용자
     * @param coupon 쿠폰
     * @return 생성된 CouponQueue
     */
    @Transactional
    private CouponQueue createQueue(User user, Coupon coupon) {
        // 1. 현재 대기 인원 계산
        int waitingCount = couponQueueRepository.countByCouponIdAndStatus(coupon.getId(), CouponQueueStatus.WAITING);
        int processingCount = couponQueueRepository.countByCouponIdAndStatus(coupon.getId(), CouponQueueStatus.PROCESSING);
        int queuePosition = waitingCount + processingCount + 1;

        // 2. 대기열 생성
        CouponQueue couponQueue = new CouponQueue(user, coupon, CouponQueueStatus.WAITING, queuePosition);
        couponQueueRepository.save(couponQueue);

        log.info("대기열 진입: userId={}, couponId={}, position={}", user.getId(), coupon.getId(), queuePosition);
        return couponQueue;
    }

    /**
     * 대기 상태 조회
     *
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID
     * @return 대기열 정보
     */
    @Transactional(readOnly = true)
    public CouponQueue getQueueStatus(Long userId, Long couponId) {
        return couponQueueRepository.findByUserIdAndCouponId(userId, couponId)
                .orElseThrow(() -> new IllegalArgumentException("대기열에 진입하지 않았습니다."));
    }

    /**
     * 특정 쿠폰의 대기열 처리 (Redis Pub/Sub Lock 사용 - 분산 환경 대응)
     *
     * Redis Pub/Sub Lock을 사용하여 여러 서버에서 동시에 같은 쿠폰의 대기열을 처리하지 않도록 제어합니다.
     * 대기 중인 사용자들을 순서대로 처리하여 쿠폰을 발급합니다.
     * 한 번에 최대 10명까지 처리하며, 발급 실패 시 FAILED 상태로 변경합니다.
     *
     * @param coupon 처리할 쿠폰
     */
    public void processQueueForCoupon(Coupon coupon) {
        String lockKey = "coupon:queue:batch:" + coupon.getId();

        // Redis Pub/Sub Lock 획득 (최대 10초 대기)
        if (!pubSubLock.tryLock(lockKey, 10, TimeUnit.SECONDS)) {
            log.warn("대기열 배치 처리 락 획득 실패: couponId={}, 다른 서버에서 실행 중", coupon.getId());
            return;
        }

        try {
            // 대기열 처리 실행 (락 보호 영역)
            processQueueForCouponTransaction(coupon);
        } finally {
            // Redis 락 해제 (반드시 실행)
            pubSubLock.unlock(lockKey);
        }
    }

    /**
     * 특정 쿠폰의 대기열 처리 트랜잭션 (Redis 락으로 보호됨)
     *
     * @param coupon 처리할 쿠폰
     */
    @Transactional
    private void processQueueForCouponTransaction(Coupon coupon) {
        // 대기 중인 사람들 조회 (선착순)
        List<CouponQueue> waitingQueues = couponQueueRepository.findByCouponIdAndStatus(
                coupon.getId(), CouponQueueStatus.WAITING);

        if (waitingQueues.isEmpty()) {
            return;
        }

        // 한 번에 처리할 수 (배치 크기)
        int batchSize = Math.min(10, waitingQueues.size());

        for (int i = 0; i < batchSize; i++) {
            CouponQueue queue = waitingQueues.get(i);
            processQueueItem(queue);
        }
    }

    /**
     * 개별 대기열 항목 처리 (Redis Pub/Sub Lock 사용)
     *
     * Redis Pub/Sub Lock을 사용하여 분산 환경에서도 동시성을 제어합니다.
     * - Redis 락 획득 → DB 트랜잭션 (상태 변경 + 쿠폰 발급) → Redis 락 해제
     * - 실패 시 자동 롤백되어 데이터 정합성 보장
     *
     * @param queue 처리할 대기열 항목
     */
    public void processQueueItem(CouponQueue queue) {
        String lockKey = "coupon:queue:" + queue.getCoupon().getId();

        // Redis Pub/Sub Lock 획득 (최대 5초 대기)
        if (!pubSubLock.tryLock(lockKey, 5, TimeUnit.SECONDS)) {
            log.warn("대기열 처리 락 획득 실패: queueId={}", queue.getId());
            updateQueueFailed(queue, "쿠폰 발급 처리 중입니다. 잠시 후 다시 시도됩니다.");
            return;
        }

        try {
            // DB 트랜잭션 실행 (락 보호 영역)
            processQueueItemTransaction(queue);
        } catch (Exception e) {
            updateQueueFailed(queue, "발급 처리 중 오류: " + e.getMessage());
            log.error("대기열 처리 실패: queueId={}", queue.getId(), e);
        } finally {
            // Redis 락 해제 (반드시 실행)
            pubSubLock.unlock(lockKey);
        }
    }

    /**
     * 대기열 처리 트랜잭션 (Redis 락으로 보호됨)
     *
     * @param queue 처리할 대기열 항목
     */
    @Transactional
    private void processQueueItemTransaction(CouponQueue queue) {
        // 1. 상태 변경: WAITING -> PROCESSING
        queue.updateStatus(CouponQueueStatus.PROCESSING);
        couponQueueRepository.save(queue);

        // 2. 최신 쿠폰 정보 조회 (일반 SELECT - Redis 락이 동시성 보장)
        Coupon coupon = couponRepository.findById(queue.getCoupon().getId())
                .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다: " + queue.getCoupon().getId()));

        // 3. 중복 발급 검증
        Optional<UserCoupon> existing = userCouponRepository.findByUserIdAndCouponId(
                queue.getUser().getId(), coupon.getId());
        if (existing.isPresent()) {
            updateQueueFailed(queue, "이미 발급받은 쿠폰입니다.");
            log.warn("이미 발급: userId={}, couponId={}", queue.getUser().getId(), coupon.getId());
            return;
        }

        // 4. 발급 가능 여부 확인
        if (!coupon.isIssuable()) {
            updateQueueFailed(queue, "쿠폰의 모든 수량이 소진되었습니다.");
            log.warn("수량 소진: couponId={}", coupon.getId());
            return;
        }

        // 5. 쿠폰 발급 처리
        coupon.increaseIssuedQuantity();
        couponRepository.save(coupon);

        UserCoupon userCoupon = new UserCoupon(
                queue.getUser(),
                coupon,
                CouponStatus.UNUSED,
                coupon.getEndDate()
        );
        userCouponRepository.save(userCoupon);

        // 6. 상태 변경: PROCESSING -> COMPLETED
        queue.updateStatus(CouponQueueStatus.COMPLETED);
        couponQueueRepository.save(queue);

        log.info("쿠폰 발급 완료: userId={}, couponId={}", queue.getUser().getId(), coupon.getId());
    }

    /**
     * 대기열 실패 상태 업데이트
     *
     * @param queue 대기열
     * @param reason 실패 사유
     */
    private void updateQueueFailed(CouponQueue queue, String reason) {
        queue.updateStatus(CouponQueueStatus.FAILED);
        queue.setFailedReason(reason);
        couponQueueRepository.save(queue);
    }

    /**
     * 대기 순번 업데이트 (Redis Pub/Sub Lock 사용 - 분산 환경 대응)
     *
     * Redis Pub/Sub Lock을 사용하여 여러 서버에서 동시에 순번 업데이트를 실행하지 않도록 제어합니다.
     * - Redis 락 획득 → 순번 업데이트 → Redis 락 해제
     */
    public void updateQueuePositions() {
        String lockKey = "coupon:queue:update-positions";

        // Redis Pub/Sub Lock 획득 (최대 10초 대기)
        if (!pubSubLock.tryLock(lockKey, 10, TimeUnit.SECONDS)) {
            log.warn("대기열 순번 업데이트 락 획득 실패: 다른 서버에서 실행 중");
            return;
        }

        try {
            // 순번 업데이트 실행 (락 보호 영역)
            updateQueuePositionsTransaction();
        } finally {
            // Redis 락 해제 (반드시 실행)
            pubSubLock.unlock(lockKey);
        }
    }

    /**
     * 대기 순번 업데이트 트랜잭션 (Redis 락으로 보호됨)
     */
    @Transactional
    private void updateQueuePositionsTransaction() {
        List<Coupon> allCoupons = couponRepository.findAll();

        for (Coupon coupon : allCoupons) {
            List<CouponQueue> allQueues = couponQueueRepository.findByCouponIdOrderByCreatedAtAsc(coupon.getId());

            int position = 1;
            for (CouponQueue queue : allQueues) {
                if (queue.getStatus() == CouponQueueStatus.WAITING || queue.getStatus() == CouponQueueStatus.PROCESSING) {
                    queue.updateQueuePosition(position++);
                    couponQueueRepository.save(queue);
                }
            }
        }
    }

    // ===== UUID 기반 메서드 (Public ID 사용) =====

    /**
     * 사용자의 쿠폰 목록 조회 (Public ID 기반)
     *
     * @param publicId 사용자 Public ID (UUID)
     * @return 사용자 쿠폰 목록
     */
    @Transactional(readOnly = true)
    public List<UserCoupon> getUserCouponsByPublicId(String publicId) {
        User user = userService.getUserByPublicId(publicId);
        return getUserCoupons(user.getId());
    }

    /**
     * 사용자의 사용 가능한 쿠폰 목록 조회 (Public ID 기반)
     *
     * @param publicId 사용자 Public ID (UUID)
     * @return 사용 가능한 쿠폰 목록
     */
    @Transactional(readOnly = true)
    public List<UserCoupon> getAvailableCouponsByPublicId(String publicId) {
        User user = userService.getUserByPublicId(publicId);
        return getAvailableCoupons(user.getId());
    }

    /**
     * 즉시 쿠폰 발급 (Public ID 기반)
     *
     * @param publicId 사용자 Public ID (UUID)
     * @param couponId 쿠폰 ID
     * @return 발급된 UserCoupon
     */
    public UserCoupon issueCouponByPublicId(String publicId, Long couponId) {
        User user = userService.getUserByPublicId(publicId);
        return issueCoupon(user.getId(), couponId);
    }

    /**
     * 선착순 쿠폰 대기열 진입 (Public ID 기반)
     *
     * @param publicId 사용자 Public ID (UUID)
     * @param couponId 쿠폰 ID
     * @return 생성된 CouponQueue
     */
    public CouponQueue joinQueueByPublicId(String publicId, Long couponId) {
        User user = userService.getUserByPublicId(publicId);
        return joinQueue(user.getId(), couponId);
    }

    /**
     * 대기 상태 조회 (Public ID 기반)
     *
     * @param publicId 사용자 Public ID (UUID)
     * @param couponId 쿠폰 ID
     * @return 대기열 정보
     */
    @Transactional(readOnly = true)
    public CouponQueue getQueueStatusByPublicId(String publicId, Long couponId) {
        User user = userService.getUserByPublicId(publicId);
        return getQueueStatus(user.getId(), couponId);
    }
}
