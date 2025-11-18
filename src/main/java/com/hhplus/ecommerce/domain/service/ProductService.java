package com.hhplus.ecommerce.domain.service;

import com.hhplus.ecommerce.domain.dto.ProductSalesDto;
import com.hhplus.ecommerce.domain.entity.Product;
import com.hhplus.ecommerce.domain.repository.OrderItemRepository;
import com.hhplus.ecommerce.domain.repository.ProductRepository;
import com.hhplus.ecommerce.domain.vo.Quantity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 상품 관련 비즈니스 로직을 처리하는 서비스
 */
@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;

    public ProductService(ProductRepository productRepository, OrderItemRepository orderItemRepository) {
        this.productRepository = productRepository;
        this.orderItemRepository = orderItemRepository;
    }

    /**
     * 상품 조회
     *
     * @param productId 상품 ID
     * @return 상품 엔티티
     */
    @Transactional(readOnly = true)
    public Product getProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + productId));
    }

    /**
     * 여러 상품 조회
     *
     * @param productIds 상품 ID 목록
     * @return 상품 엔티티 목록
     */
    @Transactional(readOnly = true)
    public List<Product> getProducts(List<Long> productIds) {
        List<Product> products = new ArrayList<>();
        for (Long productId : productIds) {
            Product product = productRepository.findById(productId)
                    .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + productId));
            products.add(product);
        }
        return products;
    }

    /**
     * 재고 충분성 검증
     *
     * @param product 상품
     * @param quantity 요청 수량
     * @throws IllegalStateException 재고가 부족한 경우
     */
    public void validateStock(Product product, Quantity quantity) {
        if (!product.hasSufficientStock(quantity)) {
            throw new IllegalStateException("재고가 부족합니다: " + product.getName());
        }
    }

    /**
     * 재고 차감
     *
     * 트랜잭션 범위를 최소화하여 DB 락 시간을 줄입니다.
     * - 트랜잭션 밖: 상품 조회, 사전 검증
     * - 트랜잭션 안: 비관적 락 + 재검증 + 재고 차감만
     *
     * @param productId 상품 ID
     * @param quantity 차감할 수량
     */
    public void decreaseStock(Long productId, Quantity quantity) {
        // 1. 사전 조회 (트랜잭션 밖 - 일반 SELECT)
        Product product = getProduct(productId);

        // 2. 사전 검증 (트랜잭션 밖)
        validateStock(product, quantity);

        // 3. 실제 재고 차감만 트랜잭션 안에서 (락 시간 최소화)
        decreaseStockWithLock(productId, quantity);
    }

    /**
     * 재고 차감 (DB 락 사용 - 트랜잭션 범위 최소화)
     *
     * @param productId 상품 ID
     * @param quantity 차감할 수량
     */
    @Transactional
    private void decreaseStockWithLock(Long productId, Quantity quantity) {
        // 락 시작!
        Product product = productRepository.findByIdWithLock(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + productId));

        // 재검증 (동시성 문제 대비)
        if (!product.hasSufficientStock(quantity)) {
            throw new IllegalStateException("재고가 부족합니다: " + product.getName());
        }

        product.decreaseStock(quantity);
        // 더티 체킹으로 자동 저장
        // 락 해제!
    }

    /**
     * 재고 복구 (주문 취소 시 사용)
     *
     * @param productId 상품 ID
     * @param quantity 복구할 수량
     */
    @Transactional
    public void increaseStock(Long productId, Quantity quantity) {
        Product product = productRepository.findByIdWithLock(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + productId));
        product.increaseStock(quantity);
        // 더티 체킹으로 자동 저장 (save() 불필요)
    }

    /**
     * 모든 상품 조회
     *
     * @return 전체 상품 목록
     */
    @Transactional(readOnly = true)
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    /**
     * 인기 상품 집계 조회 (스케줄러 전용)
     * 최근 3일간 판매량 기준 상위 N개 상품의 통계를 반환합니다.
     *
     * ⚠️ 주의: 이 메서드는 PopularProductScheduler에서만 사용됩니다.
     * 일반 API에서는 popular_products 테이블을 직접 조회하세요.
     *
     * Repository에서 DTO를 직접 반환하므로 중복 연산이 제거되었습니다.
     *
     * @param limit 조회할 상품 개수
     * @return 상품 판매 통계 DTO 목록
     */
    @Transactional(readOnly = true)
    public List<ProductSalesDto> getTopSellingProducts(int limit) {
        LocalDateTime threeDaysAgo = LocalDateTime.now().minusDays(3);
        return orderItemRepository.getTopSellingProducts(threeDaysAgo, limit);
    }
}
