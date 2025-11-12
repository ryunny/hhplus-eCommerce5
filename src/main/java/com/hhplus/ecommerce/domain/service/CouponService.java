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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 쿠폰 관련 비즈니스 로직을 처리하는 서비스
 */
@Slf4j
@Service
public class CouponService {

    private static final int LOCK_TIMEOUT_SECONDS = 5;
    private final ConcurrentHashMap<Long, ReentrantLock> couponLocks = new ConcurrentHashMap<>();

    private final UserCouponRepository userCouponRepository;
    private final CouponRepository couponRepository;
    private final CouponQueueRepository couponQueueRepository;
    private final UserService userService;

    public CouponService(UserCouponRepository userCouponRepository,
                        CouponRepository couponRepository,
                        CouponQueueRepository couponQueueRepository,
                        UserService userService) {
        this.userCouponRepository = userCouponRepository;
        this.couponRepository = couponRepository;
        this.couponQueueRepository = couponQueueRepository;
        this.userService = userService;
    }

    // ===== 쿠폰 조회 =====

    /**
     * 쿠폰 조회
     *
     * @param couponId 쿠폰 ID
     * @return 쿠폰
     */
    public Coupon getCoupon(Long couponId) {
        return couponRepository.findById(couponId)
                .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다: " + couponId));
    }

    /**
     * 발급 가능한 쿠폰 목록 조회
     *
     * @return 발급 가능한 쿠폰 목록
     */
    public List<Coupon> getIssuableCoupons() {
        return couponRepository.findIssuableCoupons(LocalDateTime.now());
    }

    /**
     * 사용자의 쿠폰 목록 조회
     *
     * @param userId 사용자 ID
     * @return 사용자 쿠폰 목록
     */
    public List<UserCoupon> getUserCoupons(Long userId) {
        return userCouponRepository.findByUserId(userId);
    }

    /**
     * 사용자의 사용 가능한 쿠폰 목록 조회
     *
     * @param userId 사용자 ID
     * @return 사용 가능한 쿠폰 목록
     */
    public List<UserCoupon> getAvailableCoupons(Long userId) {
        return userCouponRepository.findByUserIdAndStatus(userId, CouponStatus.UNUSED);
    }

    /**
     * 사용자 쿠폰 조회
     *
     * @param userCouponId 사용자 쿠폰 ID
     * @return UserCoupon
     */
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
     * ReentrantLock을 사용하여 동시성 제어를 수행합니다.
     * 선착순 쿠폰의 경우 Race Condition을 방지하여 정확한 수량만큼만 발급됩니다.
     *
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID
     * @return 발급된 UserCoupon
     */
    @Transactional
    public UserCoupon issueCoupon(Long userId, Long couponId) {
        // 쿠폰별 락 객체 획득 (공정성 보장)
        ReentrantLock lock = couponLocks.computeIfAbsent(couponId, k -> new ReentrantLock(true));

        try {
            // 타임아웃과 함께 락 획득 시도
            if (!lock.tryLock(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("쿠폰 발급 요청이 혼잡합니다. 다시 시도해주세요.");
            }

            try {
                // 1. 사용자 조회
                User user = userService.getUser(userId);

                // 2. 쿠폰 정보 조회 (락 안에서 최신 데이터 조회)
                Coupon coupon = getCoupon(couponId);

                // 3. 이미 발급받았는지 확인
                Optional<UserCoupon> existingCoupon = userCouponRepository.findByUserIdAndCouponId(userId, couponId);
                if (existingCoupon.isPresent()) {
                    throw new IllegalStateException("이미 발급받은 쿠폰입니다.");
                }

                // 4. 발급 가능 여부 확인 (락 안에서 체크 - Race Condition 방지!)
                if (!coupon.isIssuable()) {
                    throw new IllegalStateException("쿠폰의 모든 수량이 소진되었습니다.");
                }

                // 5. 트랜잭션 처리 (원자적 실행)
                // 5-1. 발급 수량 증가 (락 안에서 실행)
                coupon.increaseIssuedQuantity();
                couponRepository.save(coupon);

                // 5-2. 사용자 쿠폰 생성
                UserCoupon userCoupon = new UserCoupon(
                        user,
                        coupon,
                        CouponStatus.UNUSED,
                        coupon.getEndDate()
                );
                userCouponRepository.save(userCoupon);

                return userCoupon;
            } finally {
                lock.unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("쿠폰 발급이 중단되었습니다.", e);
        }
    }

    // ===== 쿠폰 만료 =====

    /**
     * 만료된 쿠폰 처리
     *
     * @return 만료 처리된 쿠폰 개수
     */
    @Transactional
    public int expireOldCoupons() {
        LocalDateTime now = LocalDateTime.now();
        int expiredCount = 0;

        List<UserCoupon> allUnusedCoupons = userCouponRepository.findAll().stream()
                .filter(uc -> uc.getStatus() == CouponStatus.UNUSED)
                .toList();

        for (UserCoupon userCoupon : allUnusedCoupons) {
            if (userCoupon.getExpiresAt().isBefore(now)) {
                // 쿠폰별 락 획득 (동시성 제어)
                Long couponId = userCoupon.getCoupon().getId();
                ReentrantLock lock = couponLocks.computeIfAbsent(couponId, k -> new ReentrantLock(true));

                try {
                    // 타임아웃과 함께 락 획득
                    if (lock.tryLock(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                        try {
                            // 만료 처리 (락 안에서 처리)
                            userCoupon.expire();
                            userCouponRepository.save(userCoupon);

                            Coupon coupon = userCoupon.getCoupon();
                            coupon.decreaseIssuedQuantity();
                            couponRepository.save(coupon);

                            expiredCount++;
                        } finally {
                            lock.unlock();
                        }
                    }
                    // 락 획득 실패 시 건너뛰기 (다음 배치에서 처리)
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    // 인터럽트 발생 시 배치 중단
                    break;
                }
            }
        }

        return expiredCount;
    }

    // ===== 대기열 시스템 =====

    /**
     * 선착순 쿠폰 대기열 진입
     *
     * 사용자를 대기열에 등록하고 현재 대기 순번을 반환합니다.
     *
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID
     * @return 생성된 CouponQueue (대기 정보 포함)
     */
    @Transactional
    public CouponQueue joinQueue(Long userId, Long couponId) {
        // 1. 사용자 조회
        User user = userService.getUser(userId);

        // 2. 쿠폰 조회
        Coupon coupon = getCoupon(couponId);

        // 3. 이미 대기열에 있는지 확인
        Optional<CouponQueue> existingQueue = couponQueueRepository.findByUserIdAndCouponId(userId, couponId);
        if (existingQueue.isPresent()) {
            CouponQueue existing = existingQueue.get();
            if (existing.getStatus() == CouponQueueStatus.WAITING || existing.getStatus() == CouponQueueStatus.PROCESSING) {
                return existing; // 이미 대기 중
            }
            if (existing.getStatus() == CouponQueueStatus.COMPLETED) {
                throw new IllegalStateException("이미 발급받은 쿠폰입니다.");
            }
        }

        // 4. 현재 대기 인원 계산
        int waitingCount = couponQueueRepository.countByCouponIdAndStatus(couponId, CouponQueueStatus.WAITING);
        int processingCount = couponQueueRepository.countByCouponIdAndStatus(couponId, CouponQueueStatus.PROCESSING);
        int queuePosition = waitingCount + processingCount + 1;

        // 5. 대기열 생성
        CouponQueue couponQueue = new CouponQueue(user, coupon, CouponQueueStatus.WAITING, queuePosition);
        couponQueueRepository.save(couponQueue);

        log.info("대기열 진입: userId={}, couponId={}, position={}", userId, couponId, queuePosition);
        return couponQueue;
    }

    /**
     * 대기 상태 조회
     *
     * @param userId 사용자 ID
     * @param couponId 쿠폰 ID
     * @return 대기열 정보
     */
    public CouponQueue getQueueStatus(Long userId, Long couponId) {
        return couponQueueRepository.findByUserIdAndCouponId(userId, couponId)
                .orElseThrow(() -> new IllegalArgumentException("대기열에 진입하지 않았습니다."));
    }

    /**
     * 특정 쿠폰의 대기열 처리
     *
     * 대기 중인 사용자들을 순서대로 처리하여 쿠폰을 발급합니다.
     * 한 번에 최대 10명까지 처리하며, 발급 실패 시 FAILED 상태로 변경합니다.
     *
     * @param coupon 처리할 쿠폰
     */
    @Transactional
    public void processQueueForCoupon(Coupon coupon) {
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
     * 개별 대기열 항목 처리
     *
     * @param queue 처리할 대기열 항목
     */
    @Transactional
    public void processQueueItem(CouponQueue queue) {
        ReentrantLock lock = couponLocks.computeIfAbsent(queue.getCoupon().getId(), k -> new ReentrantLock(true));

        try {
            if (!lock.tryLock(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("락 획득 실패: queueId={}", queue.getId());
                return;
            }

            try {
                // 상태 변경: WAITING -> PROCESSING
                queue.updateStatus(CouponQueueStatus.PROCESSING);
                couponQueueRepository.save(queue);

                // 최신 쿠폰 정보 조회
                Coupon coupon = getCoupon(queue.getCoupon().getId());

                // 이미 발급받았는지 확인
                Optional<UserCoupon> existing = userCouponRepository.findByUserIdAndCouponId(
                        queue.getUser().getId(), coupon.getId());
                if (existing.isPresent()) {
                    queue.setFailedReason("이미 발급받은 쿠폰입니다.");
                    couponQueueRepository.save(queue);
                    log.warn("이미 발급: userId={}, couponId={}", queue.getUser().getId(), coupon.getId());
                    return;
                }

                // 발급 가능 여부 확인
                if (!coupon.isIssuable()) {
                    queue.setFailedReason("쿠폰의 모든 수량이 소진되었습니다.");
                    couponQueueRepository.save(queue);
                    log.warn("수량 소진: couponId={}", coupon.getId());
                    return;
                }

                // 쿠폰 발급
                coupon.increaseIssuedQuantity();
                couponRepository.save(coupon);

                UserCoupon userCoupon = new UserCoupon(
                        queue.getUser(),
                        coupon,
                        CouponStatus.UNUSED,
                        coupon.getEndDate()
                );
                userCouponRepository.save(userCoupon);

                // 상태 변경: PROCESSING -> COMPLETED
                queue.updateStatus(CouponQueueStatus.COMPLETED);
                couponQueueRepository.save(queue);

                log.info("쿠폰 발급 완료: userId={}, couponId={}", queue.getUser().getId(), coupon.getId());

            } finally {
                lock.unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("대기열 처리 중단: queueId={}", queue.getId(), e);
        } catch (Exception e) {
            queue.setFailedReason("발급 처리 중 오류: " + e.getMessage());
            couponQueueRepository.save(queue);
            log.error("대기열 처리 실패: queueId={}", queue.getId(), e);
        }
    }

    /**
     * 대기 순번 업데이트
     */
    @Transactional
    public void updateQueuePositions() {
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
    public CouponQueue getQueueStatusByPublicId(String publicId, Long couponId) {
        User user = userService.getUserByPublicId(publicId);
        return getQueueStatus(user.getId(), couponId);
    }
}
