package com.hhplus.ecommerce.domain.service;

import com.hhplus.ecommerce.BaseIntegrationTest;
import com.hhplus.ecommerce.domain.entity.Order;
import com.hhplus.ecommerce.domain.entity.OrderItem;
import com.hhplus.ecommerce.domain.entity.Product;
import com.hhplus.ecommerce.domain.entity.User;
import com.hhplus.ecommerce.domain.repository.OrderItemRepository;
import com.hhplus.ecommerce.domain.repository.OrderRepository;
import com.hhplus.ecommerce.domain.repository.ProductRepository;
import com.hhplus.ecommerce.domain.repository.UserRepository;
import com.hhplus.ecommerce.domain.vo.*;
import com.hhplus.ecommerce.infrastructure.redis.RedisKeyGenerator;
import com.hhplus.ecommerce.presentation.dto.PopularProductResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis 기반 상품 랭킹 통합 테스트
 *
 * 목적:
 * 1. Redis Sorted Set 기반 랭킹 기능 검증
 * 2. 주문 후 자동 랭킹 업데이트 검증
 * 3. Top N 조회 정확성 검증
 * 4. Testcontainers로 실제 Redis 환경 테스트
 */
@DisplayName("상품 랭킹 통합 테스트")
class ProductRankingServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ProductRankingService productRankingService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private User testUser;
    private Product product1;
    private Product product2;
    private Product product3;

    @BeforeEach
    void setUp() {
        // Redis 데이터 초기화
        redisTemplate.delete(RedisKeyGenerator.productRanking1Day());
        redisTemplate.delete(RedisKeyGenerator.productRanking7Days());

        // 테스트 데이터 생성
        testUser = userRepository.save(User.createUser(
                "test@example.com",
                "테스트유저",
                "010-1234-5678",
                Money.of(1000000L)
        ));

        product1 = productRepository.save(Product.createProduct(
                "상품1", Money.of(10000L), Quantity.of(100)
        ));
        product2 = productRepository.save(Product.createProduct(
                "상품2", Money.of(20000L), Quantity.of(100)
        ));
        product3 = productRepository.save(Product.createProduct(
                "상품3", Money.of(30000L), Quantity.of(100)
        ));
    }

    @AfterEach
    void tearDown() {
        redisTemplate.delete(RedisKeyGenerator.productRanking1Day());
        redisTemplate.delete(RedisKeyGenerator.productRanking7Days());
    }

    @Test
    @DisplayName("주문 시 Redis 랭킹에 판매량이 기록된다")
    void recordSales_UpdatesRedisRanking() {
        // Given: 주문 생성
        Order order = Order.createOrder(testUser, null, Money.of(10000L), Money.of(0L));
        orderRepository.save(order);

        OrderItem orderItem = new OrderItem(order, product1, Quantity.of(5), product1.getPrice());
        orderItemRepository.save(orderItem);

        // When: 판매량 기록
        productRankingService.recordSales(List.of(orderItem));

        // Then: Redis에 기록되었는지 확인
        Double score1Day = redisTemplate.opsForZSet().score(
                RedisKeyGenerator.productRanking1Day(),
                "product:" + product1.getId()
        );
        Double score7Days = redisTemplate.opsForZSet().score(
                RedisKeyGenerator.productRanking7Days(),
                "product:" + product1.getId()
        );

        assertThat(score1Day).isEqualTo(5.0);
        assertThat(score7Days).isEqualTo(5.0);
    }

    @Test
    @DisplayName("여러 주문 시 판매량이 누적된다")
    void recordSales_AccumulatesSales() {
        // Given: 첫 번째 주문
        Order order1 = Order.createOrder(testUser, null, Money.of(10000L), Money.of(0L));
        orderRepository.save(order1);
        OrderItem orderItem1 = new OrderItem(order1, product1, Quantity.of(3), product1.getPrice());
        orderItemRepository.save(orderItem1);

        productRankingService.recordSales(List.of(orderItem1));

        // When: 두 번째 주문
        Order order2 = Order.createOrder(testUser, null, Money.of(10000L), Money.of(0L));
        orderRepository.save(order2);
        OrderItem orderItem2 = new OrderItem(order2, product1, Quantity.of(7), product1.getPrice());
        orderItemRepository.save(orderItem2);

        productRankingService.recordSales(List.of(orderItem2));

        // Then: 판매량 누적 확인 (3 + 7 = 10)
        Double score = redisTemplate.opsForZSet().score(
                RedisKeyGenerator.productRanking1Day(),
                "product:" + product1.getId()
        );

        assertThat(score).isEqualTo(10.0);
    }

    @Test
    @DisplayName("Top N 조회 시 판매량 순으로 정렬된다")
    void getTopProducts_ReturnsProductsOrderedBySales() {
        // Given: 여러 상품의 판매량 기록
        // product1: 10개, product2: 20개, product3: 5개
        recordProductSales(product1, 10);
        recordProductSales(product2, 20);
        recordProductSales(product3, 5);

        // When: Top 3 조회
        List<PopularProductResponse> topProducts = productRankingService.getTopProducts(1, 3);

        // Then: 판매량 순으로 정렬 (product2 > product1 > product3)
        assertThat(topProducts).hasSize(3);
        assertThat(topProducts.get(0).productId()).isEqualTo(product2.getId());
        assertThat(topProducts.get(0).totalSalesQuantity()).isEqualTo(20);
        assertThat(topProducts.get(1).productId()).isEqualTo(product1.getId());
        assertThat(topProducts.get(1).totalSalesQuantity()).isEqualTo(10);
        assertThat(topProducts.get(2).productId()).isEqualTo(product3.getId());
        assertThat(topProducts.get(2).totalSalesQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("Top N 조회 시 N개만 반환된다")
    void getTopProducts_ReturnsLimitedResults() {
        // Given: 5개 상품 판매량 기록
        recordProductSales(product1, 10);
        recordProductSales(product2, 20);
        recordProductSales(product3, 5);

        // When: Top 2 조회
        List<PopularProductResponse> topProducts = productRankingService.getTopProducts(1, 2);

        // Then: 2개만 반환
        assertThat(topProducts).hasSize(2);
        assertThat(topProducts.get(0).productId()).isEqualTo(product2.getId());
        assertThat(topProducts.get(1).productId()).isEqualTo(product1.getId());
    }

    @Test
    @DisplayName("랭킹 데이터가 없을 때 빈 리스트를 반환한다")
    void getTopProducts_ReturnsEmptyListWhenNoData() {
        // When: 랭킹 데이터 없이 조회
        List<PopularProductResponse> topProducts = productRankingService.getTopProducts(1, 5);

        // Then: 빈 리스트 반환
        assertThat(topProducts).isEmpty();
    }

    @Test
    @DisplayName("1일 랭킹과 7일 랭킹이 독립적으로 관리된다")
    void rankings_AreIndependent() {
        // Given: 1일 랭킹에만 데이터 기록
        redisTemplate.opsForZSet().add(
                RedisKeyGenerator.productRanking1Day(),
                "product:" + product1.getId(),
                10.0
        );

        // When: 각각 조회
        List<PopularProductResponse> ranking1Day = productRankingService.getTopProducts(1, 5);
        List<PopularProductResponse> ranking7Days = productRankingService.getTopProducts(7, 5);

        // Then: 1일 랭킹에만 데이터 존재
        assertThat(ranking1Day).hasSize(1);
        assertThat(ranking7Days).isEmpty();
    }

    /**
     * 테스트 헬퍼 메서드: 상품 판매량 기록
     */
    private void recordProductSales(Product product, int quantity) {
        Order order = Order.createOrder(testUser, null, Money.of(10000L), Money.of(0L));
        orderRepository.save(order);

        OrderItem orderItem = new OrderItem(order, product, Quantity.of(quantity), product.getPrice());
        orderItemRepository.save(orderItem);

        productRankingService.recordSales(List.of(orderItem));
    }
}
