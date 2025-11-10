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
     * 비관적 락과 더티 체킹을 활용하여 동시성 제어
     *
     * @param userId 사용자 ID
     * @param amount 차감할 금액
     */
    @Transactional
    public void deductBalance(Long userId, Money amount) {
        User user = userRepository.findByIdWithLock(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));
        user.deductBalance(amount);
        // 더티 체킹으로 자동 저장 (save() 불필요)
    }

    /**
     * 잔액 차감 (Public ID 기반)
     * 비관적 락과 더티 체킹을 활용하여 동시성 제어
     *
     * @param publicId 사용자 Public ID (UUID)
     * @param amount 차감할 금액
     */
    @Transactional
    public void deductBalanceByPublicId(String publicId, Money amount) {
        User user = userRepository.findByPublicIdWithLock(publicId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + publicId));
        user.deductBalance(amount);
        // 더티 체킹으로 자동 저장 (save() 불필요)
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
