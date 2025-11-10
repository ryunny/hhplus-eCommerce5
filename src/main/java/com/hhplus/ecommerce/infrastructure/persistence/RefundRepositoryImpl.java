package com.hhplus.ecommerce.infrastructure.persistence;

import com.hhplus.ecommerce.domain.entity.Refund;
import com.hhplus.ecommerce.domain.repository.RefundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RefundRepositoryImpl implements RefundRepository {

    private final RefundJpaRepository refundJpaRepository;

    @Override
    public Refund save(Refund refund) {
        return refundJpaRepository.save(refund);
    }

    @Override
    public Optional<Refund> findById(Long id) {
        return refundJpaRepository.findById(id);
    }

    @Override
    public List<Refund> findByOrderId(Long orderId) {
        return refundJpaRepository.findByOrderId(orderId);
    }

    @Override
    public void deleteById(Long id) {
        refundJpaRepository.deleteById(id);
    }
}
