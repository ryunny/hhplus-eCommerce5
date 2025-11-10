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
}
