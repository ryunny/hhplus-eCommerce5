package com.hhplus.ecommerce.domain.repository;

import com.hhplus.ecommerce.domain.entity.User;

import java.util.Optional;

/**
 * 사용자 Repository 인터페이스
 * Domain 계층에서 정의하는 순수 인터페이스 (프레임워크 독립적)
 */
public interface UserRepository {
    User save(User user);

    Optional<User> findById(Long id);

    Optional<User> findByEmail(String email);

    Optional<User> findByPublicId(String publicId);

    Optional<User> findByIdWithLock(Long id);

    Optional<User> findByPublicIdWithLock(String publicId);

    void deleteById(Long id);

    default User findByIdOrThrow(Long id) {
        return findById(id)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + id));
    }

    default User findByPublicIdOrThrow(String publicId) {
        return findByPublicId(publicId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + publicId));
    }

    default User findByIdWithLockOrThrow(Long id) {
        return findByIdWithLock(id)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + id));
    }

    default User findByPublicIdWithLockOrThrow(String publicId) {
        return findByPublicIdWithLock(publicId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + publicId));
    }
}
