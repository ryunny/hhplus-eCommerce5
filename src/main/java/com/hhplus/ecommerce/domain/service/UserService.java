package com.hhplus.ecommerce.domain.service;

import com.hhplus.ecommerce.domain.entity.User;
import com.hhplus.ecommerce.domain.repository.UserRepository;
import com.hhplus.ecommerce.domain.vo.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 관련 비즈니스 로직을 처리하는 서비스
 */
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 사용자 조회
     *
     * @param userId 사용자 ID
     * @return 사용자 엔티티
     */
    @Transactional(readOnly = true)
    public User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));
    }

    /**
     * 사용자 조회 (Public ID 기반)
     *
     * @param publicId 사용자 Public ID (UUID)
     * @return 사용자 엔티티
     */
    @Transactional(readOnly = true)
    public User getUserByPublicId(String publicId) {
        return userRepository.findByPublicId(publicId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + publicId));
    }

    /**
     * 잔액 충분성 검증
     *
     * @param user 사용자
     * @param amount 필요 금액
     * @throws IllegalStateException 잔액이 부족한 경우
     */
    public void validateBalance(User user, Money amount) {
        if (!user.hasEnoughBalance(amount)) {
            throw new IllegalStateException("잔액이 부족합니다.");
        }
    }

    /**
     * 잔액 차감
     *
     * 트랜잭션 범위를 최소화하여 DB 락 시간을 줄입니다.
     * - 트랜잭션 밖: 사용자 조회, 사전 검증
     * - 트랜잭션 안: 비관적 락 + 재검증 + 차감만
     *
     * @param userId 사용자 ID
     * @param amount 차감할 금액
     */
    public void deductBalance(Long userId, Money amount) {
        // 1. 사전 조회 (트랜잭션 밖)
        User user = getUser(userId);

        // 2. 사전 검증 (트랜잭션 밖)
        validateBalance(user, amount);

        // 3. 실제 차감만 트랜잭션 안에서 (락 시간 최소화)
        deductBalanceWithLock(userId, amount);
    }

    /**
     * 잔액 차감 (DB 락 사용 - 트랜잭션 범위 최소화)
     *
     * @param userId 사용자 ID
     * @param amount 차감할 금액
     */
    @Transactional
    private void deductBalanceWithLock(Long userId, Money amount) {
        // 락 시작!
        User user = userRepository.findByIdWithLock(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));

        // 재검증 (동시성 문제 대비)
        if (!user.hasEnoughBalance(amount)) {
            throw new IllegalStateException("잔액이 부족합니다.");
        }

        user.deductBalance(amount);
        // 더티 체킹으로 자동 저장
        // 락 해제!
    }

    /**
     * 잔액 차감 (Public ID 기반)
     *
     * 트랜잭션 범위를 최소화하여 DB 락 시간을 줄입니다.
     *
     * @param publicId 사용자 Public ID (UUID)
     * @param amount 차감할 금액
     */
    public void deductBalanceByPublicId(String publicId, Money amount) {
        // 1. 사전 조회 (트랜잭션 밖)
        User user = getUserByPublicId(publicId);

        // 2. 사전 검증 (트랜잭션 밖)
        validateBalance(user, amount);

        // 3. 실제 차감만 트랜잭션 안에서
        deductBalanceByPublicIdWithLock(publicId, amount);
    }

    /**
     * 잔액 차감 (Public ID 기반, DB 락 사용)
     *
     * @param publicId 사용자 Public ID
     * @param amount 차감할 금액
     */
    @Transactional
    private void deductBalanceByPublicIdWithLock(String publicId, Money amount) {
        User user = userRepository.findByPublicIdWithLock(publicId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + publicId));

        // 재검증
        if (!user.hasEnoughBalance(amount)) {
            throw new IllegalStateException("잔액이 부족합니다.");
        }

        user.deductBalance(amount);
        // 더티 체킹으로 자동 저장
    }

    /**
     * 잔액 충전
     *
     * @param userId 사용자 ID
     * @param amount 충전할 금액
     * @return 충전 후 사용자 정보
     */
    @Transactional
    public User chargeBalance(Long userId, Money amount) {
        User user = userRepository.findByIdWithLock(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));
        user.chargeBalance(amount);
        // 더티 체킹으로 자동 저장 (save() 불필요)
        return user;
    }

    /**
     * 잔액 충전 (Public ID 기반)
     *
     * @param publicId 사용자 Public ID (UUID)
     * @param amount 충전할 금액
     * @return 충전 후 사용자 정보
     */
    @Transactional
    public User chargeBalanceByPublicId(String publicId, Money amount) {
        User user = userRepository.findByPublicIdWithLock(publicId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + publicId));
        user.chargeBalance(amount);
        // 더티 체킹으로 자동 저장 (save() 불필요)
        return user;
    }
}
