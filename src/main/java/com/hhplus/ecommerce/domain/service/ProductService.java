package com.hhplus.ecommerce.domain.service;

import com.hhplus.ecommerce.domain.dto.ProductSalesDto;
import com.hhplus.ecommerce.domain.entity.Product;
import com.hhplus.ecommerce.domain.repository.OrderItemRepository;
import com.hhplus.ecommerce.domain.repository.ProductRepository;
import com.hhplus.ecommerce.domain.vo.Quantity;
import com.hhplus.ecommerce.infrastructure.lock.RedisPubSubLock;
import com.hhplus.ecommerce.infrastructure.redis.RedisKeyGenerator;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 상품 관련 비즈니스 로직을 처리하는 서비스
 */
@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;
    private final RedisPubSubLock pubSubLock;

    public ProductService(ProductRepository productRepository, OrderItemRepository orderItemRepository, RedisPubSubLock pubSubLock) {
        this.productRepository = productRepository;
        this.orderItemRepository = orderItemRepository;
        this.pubSubLock = pubSubLock;
    }

    /**
     * 상품 조회
     *
     * @param productId 상품 ID
     * @return 상품 엔티티
     */
    @Cacheable(value = "products", key = "#productId")
    @Transactional(readOnly = true)
    public Product getProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + productId));
    }

    /**
     * 여러 상품 조회 (IN 쿼리로 한 번에 조회하여 N+1 문제 해결)
     *
     * @param productIds 상품 ID 목록
     * @return 상품 엔티티 목록
     */
    @Transactional(readOnly = true)
    public List<Product> getProducts(List<Long> productIds) {
        List<Product> products = productRepository.findAllById(productIds);

        // 모든 ID가 존재하는지 검증
        if (products.size() != productIds.size()) {
            Set<Long> foundIds = products.stream()
                    .map(Product::getId)
                    .collect(java.util.stream.Collectors.toSet());
            List<Long> missingIds = productIds.stream()
                    .filter(id -> !foundIds.contains(id))
                    .toList();
            throw new IllegalArgumentException("상품을 찾을 수 없습니다: " + missingIds);
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
     * 재고 차감 (Redis Pub/Sub Lock 사용 - 분산 환경 대응)
     *
     * Redis Pub/Sub Lock을 사용하여 분산 환경에서도 동시성을 제어합니다.
     * - 트랜잭션 밖: 상품 조회, 사전 검증
     * - Redis 락 획득 → DB 트랜잭션 (재검증 + 재고 차감) → Redis 락 해제
     *
     * @param productId 상품 ID
     * @param quantity 차감할 수량
     */
    public void decreaseStock(Long productId, Quantity quantity) {
        // 1. 사전 조회 (트랜잭션 밖 - 일반 SELECT)
        Product product = getProduct(productId);

        // 2. 사전 검증 (트랜잭션 밖)
        validateStock(product, quantity);

        // 3. Redis 락 사용하여 재고 차감
        decreaseStockWithLock(productId, quantity);
    }

    /**
     * 재고 차감 (Redis Pub/Sub Lock 사용 - 트랜잭션 범위 최소화)
     *
     * @param productId 상품 ID
     * @param quantity 차감할 수량
     */
    private void decreaseStockWithLock(Long productId, Quantity quantity) {
        String lockKey = RedisKeyGenerator.productStockDecreaseLock(productId);

        // Redis Pub/Sub Lock 획득 (최대 5초 대기)
        if (!pubSubLock.tryLock(lockKey, 5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("재고 처리 중입니다. 잠시 후 다시 시도해주세요.");
        }

        try {
            // DB 트랜잭션 실행 (락 보호 영역)
            decreaseStockTransaction(productId, quantity);
        } finally {
            // Redis 락 해제 (반드시 실행)
            pubSubLock.unlock(lockKey);
        }
    }

    /**
     * 재고 차감 트랜잭션 (Redis 락으로 보호됨)
     *
     * @param productId 상품 ID
     * @param quantity 차감할 수량
     */
    @CacheEvict(value = "products", key = "#productId")
    @Transactional
    public void decreaseStockTransaction(Long productId, Quantity quantity) {
        // 상품 조회 (일반 SELECT - Redis 락이 동시성 보장)
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + productId));

        // 재검증 (동시성 문제 대비)
        if (!product.hasSufficientStock(quantity)) {
            throw new IllegalStateException("재고가 부족합니다: " + product.getName());
        }

        product.decreaseStock(quantity);
        // 더티 체킹으로 자동 저장
    }

    /**
     * 재고 복구 (주문 취소 시 사용, Redis Pub/Sub Lock 사용)
     *
     * @param productId 상품 ID
     * @param quantity 복구할 수량
     */
    public void increaseStock(Long productId, Quantity quantity) {
        String lockKey = RedisKeyGenerator.productStockIncreaseLock(productId);

        // Redis Pub/Sub Lock 획득 (최대 5초 대기)
        if (!pubSubLock.tryLock(lockKey, 5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("재고 처리 중입니다. 잠시 후 다시 시도해주세요.");
        }

        try {
            // DB 트랜잭션 실행 (락 보호 영역)
            increaseStockTransaction(productId, quantity);
        } finally {
            // Redis 락 해제 (반드시 실행)
            pubSubLock.unlock(lockKey);
        }
    }

    /**
     * 재고 복구 트랜잭션 (Redis 락으로 보호됨)
     *
     * @param productId 상품 ID
     * @param quantity 복구할 수량
     */
    @Transactional
    public void increaseStockTransaction(Long productId, Quantity quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + productId));
        product.increaseStock(quantity);
        // 더티 체킹으로 자동 저장
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
     * 캐시 적용: TTL 60분
     * - 통계성 데이터로 장시간 캐싱 가능
     * - 스케줄러가 자주 실행되어도 DB 부하 최소화
     *
     * @param limit 조회할 상품 개수
     * @return 상품 판매 통계 DTO 목록
     */
    @Cacheable(value = "topProducts", key = "#limit")
    @Transactional(readOnly = true)
    public List<ProductSalesDto> getTopSellingProducts(int limit) {
        LocalDateTime threeDaysAgo = LocalDateTime.now().minusDays(3);
        return orderItemRepository.getTopSellingProducts(threeDaysAgo, limit);
    }
}
