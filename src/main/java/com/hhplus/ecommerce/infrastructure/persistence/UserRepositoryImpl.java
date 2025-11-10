package com.hhplus.ecommerce.infrastructure.persistence;

import com.hhplus.ecommerce.domain.entity.User;
import com.hhplus.ecommerce.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * UserRepository 구현체 (Adapter)
 * Domain Repository 인터페이스를 Infrastructure의 JpaRepository로 연결
 */
@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository userJpaRepository;

    @Override
    public User save(User user) {
        return userJpaRepository.save(user);
    }

    @Override
    public Optional<User> findById(Long id) {
        return userJpaRepository.findById(id);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return userJpaRepository.findByEmail(email);
    }

    @Override
    public Optional<User> findByPublicId(String publicId) {
        return userJpaRepository.findByPublicId(publicId);
    }

    @Override
    public Optional<User> findByIdWithLock(Long id) {
        return userJpaRepository.findByIdWithLock(id);
    }

    @Override
    public Optional<User> findByPublicIdWithLock(String publicId) {
        return userJpaRepository.findByPublicIdWithLock(publicId);
    }

    @Override
    public void deleteById(Long id) {
        userJpaRepository.deleteById(id);
    }
}
