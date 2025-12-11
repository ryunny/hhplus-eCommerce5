package com.hhplus.ecommerce.infrastructure.event;

import com.hhplus.ecommerce.BaseIntegrationTest;
import com.hhplus.ecommerce.domain.entity.*;
import com.hhplus.ecommerce.domain.event.OrderCompletedEvent;
import com.hhplus.ecommerce.domain.event.OrderConfirmedEvent;
import com.hhplus.ecommerce.domain.repository.CategoryRepository;
import com.hhplus.ecommerce.domain.repository.OrderItemRepository;
import com.hhplus.ecommerce.domain.repository.OrderRepository;
import com.hhplus.ecommerce.domain.repository.ProductRepository;
import com.hhplus.ecommerce.domain.vo.Money;
import com.hhplus.ecommerce.domain.vo.Quantity;
import com.hhplus.ecommerce.domain.vo.Stock;
import com.hhplus.ecommerce.infrastructure.redis.RedisKeyGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ProductRankingEventHandler 통합 테스트
 *
 * 주문 이벤트 발행 시 인기상품 랭킹이 Redis에 정상적으로 기록되는지 검증합니다.
 * - OrderCompletedEvent (Orchestration 패턴)
 * - OrderConfirmedEvent (Choreography 패턴)
 */
class ProductRankingEventHandlerTest extends BaseIntegrationTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Test
    @DisplayName("OrderCompletedEvent 발행 시 Redis에 판매량이 기록된다 (Orchestration)")
    void handleOrderCompleted_RecordsSalesInRedis() throws InterruptedException {
        // given - 상품 생성
        Category category = categoryRepository.save(new Category("전자제품"));
        Product product1 = createProduct(category, "노트북", 50000L, 100);
        Product product2 = createProduct(category, "마우스", 10000L, 100);
        productRepository.save(product1);
        productRepository.save(product2);

        // given - 주문 생성
        Order order = new Order(
                1L,
                "홍길동",
                "서울시 강남구",
                "010-1234-5678",
                new Money(70000L),
                new Money(0L),
                new Money(70000L)
        );
        orderRepository.save(order);

        OrderItem item1 = new OrderItem(order, product1, new Quantity(2), new Money(50000L));
        OrderItem item2 = new OrderItem(order, product2, new Quantity(3), new Money(10000L));
        orderItemRepository.save(item1);
        orderItemRepository.save(item2);

        // when - OrderCompletedEvent 발행
        eventPublisher.publishEvent(new OrderCompletedEvent(order.getId()));

        // 비동기 이벤트 처리 대기
        Thread.sleep(1000);

        // then - Redis에 판매량 기록 확인
        String key = RedisKeyGenerator.productRankingByDate(LocalDate.now());

        Double score1 = redisTemplate.opsForZSet().score(key, "product:" + product1.getId());
        Double score2 = redisTemplate.opsForZSet().score(key, "product:" + product2.getId());

        assertThat(score1).isNotNull();
        assertThat(score1.intValue()).isEqualTo(2); // 2개 판매
        assertThat(score2).isNotNull();
        assertThat(score2.intValue()).isEqualTo(3); // 3개 판매
    }

    @Test
    @DisplayName("OrderConfirmedEvent 발행 시 Redis에 판매량이 기록된다 (Choreography)")
    void handleOrderConfirmed_RecordsSalesInRedis() throws InterruptedException {
        // given - 상품 생성
        Category category = categoryRepository.save(new Category("가전제품"));
        Product product = createProduct(category, "키보드", 15000L, 100);
        productRepository.save(product);

        // given - 주문 생성
        Order order = new Order(
                2L,
                "김철수",
                "서울시 강남구",
                "010-2345-6789",
                new Money(75000L),
                new Money(0L),
                new Money(75000L)
        );
        orderRepository.save(order);

        OrderItem item = new OrderItem(order, product, new Quantity(5), new Money(15000L));
        orderItemRepository.save(item);

        // when - OrderConfirmedEvent 발행
        eventPublisher.publishEvent(new OrderConfirmedEvent(order.getId()));

        // 비동기 이벤트 처리 대기
        Thread.sleep(1000);

        // then - Redis에 판매량 기록 확인
        String key = RedisKeyGenerator.productRankingByDate(LocalDate.now());

        Double score = redisTemplate.opsForZSet().score(key, "product:" + product.getId());

        assertThat(score).isNotNull();
        assertThat(score.intValue()).isEqualTo(5); // 5개 판매
    }

    @Test
    @DisplayName("동일 상품의 여러 주문은 판매량이 누적된다")
    void multipleOrders_AccumulateSales() throws InterruptedException {
        // given - 상품 생성
        Category category = categoryRepository.save(new Category("도서"));
        Product product = createProduct(category, "Java 프로그래밍", 30000L, 100);
        productRepository.save(product);

        // given - 첫 번째 주문
        Order order1 = new Order(
                3L,
                "이영희",
                "서울시 강남구",
                "010-3333-3333",
                new Money(60000L),
                new Money(0L),
                new Money(60000L)
        );
        orderRepository.save(order1);
        OrderItem item1 = new OrderItem(order1, product, new Quantity(2), new Money(30000L));
        orderItemRepository.save(item1);

        // given - 두 번째 주문
        Order order2 = new Order(
                4L,
                "박민수",
                "서울시 강남구",
                "010-4444-4444",
                new Money(90000L),
                new Money(0L),
                new Money(90000L)
        );
        orderRepository.save(order2);
        OrderItem item2 = new OrderItem(order2, product, new Quantity(3), new Money(30000L));
        orderItemRepository.save(item2);

        // when - 두 개의 OrderCompletedEvent 발행
        eventPublisher.publishEvent(new OrderCompletedEvent(order1.getId()));
        eventPublisher.publishEvent(new OrderCompletedEvent(order2.getId()));

        // 비동기 이벤트 처리 대기
        Thread.sleep(1000);

        // then - 판매량이 누적되어 5개가 되어야 함
        String key = RedisKeyGenerator.productRankingByDate(LocalDate.now());

        Double score = redisTemplate.opsForZSet().score(key, "product:" + product.getId());

        assertThat(score).isNotNull();
        assertThat(score.intValue()).isEqualTo(5); // 2 + 3 = 5개
    }

    @Test
    @DisplayName("인기상품 Top N 조회가 정상 동작한다")
    void getTopProducts_ReturnsCorrectRanking() throws InterruptedException {
        // given - 여러 상품 생성 및 주문
        Category category = categoryRepository.save(new Category("의류"));
        Product product1 = createProduct(category, "티셔츠", 20000L, 100);
        Product product2 = createProduct(category, "청바지", 50000L, 100);
        Product product3 = createProduct(category, "운동화", 80000L, 100);
        productRepository.save(product1);
        productRepository.save(product2);
        productRepository.save(product3);

        // 주문 생성 (판매량: 티셔츠 10개, 청바지 5개, 운동화 3개)
        createAndPublishOrder(5L, product1, 10);
        createAndPublishOrder(6L, product2, 5);
        createAndPublishOrder(7L, product3, 3);

        // 비동기 이벤트 처리 대기
        Thread.sleep(1000);

        // when - Top 3 조회
        String key = RedisKeyGenerator.productRankingByDate(LocalDate.now());
        Set<ZSetOperations.TypedTuple<String>> topProducts =
                redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, 2);

        // then - 순위 검증 (판매량 많은 순)
        assertThat(topProducts).hasSize(3);

        List<ZSetOperations.TypedTuple<String>> ranking = List.copyOf(topProducts);

        // 1위: 티셔츠 (10개)
        assertThat(ranking.get(0).getValue()).isEqualTo("product:" + product1.getId());
        assertThat(ranking.get(0).getScore().intValue()).isEqualTo(10);

        // 2위: 청바지 (5개)
        assertThat(ranking.get(1).getValue()).isEqualTo("product:" + product2.getId());
        assertThat(ranking.get(1).getScore().intValue()).isEqualTo(5);

        // 3위: 운동화 (3개)
        assertThat(ranking.get(2).getValue()).isEqualTo("product:" + product3.getId());
        assertThat(ranking.get(2).getScore().intValue()).isEqualTo(3);
    }

    // Helper methods
    private Product createProduct(Category category, String name, Long price, int stock) {
        return new Product(category, name, "상품 설명", new Money(price), new Stock(stock));
    }

    private void createAndPublishOrder(Long userId, Product product, int quantity) {
        Order order = new Order(
                userId,
                "고객" + userId,
                "서울시 강남구",
                "010-0000-0000",
                new Money(product.getPrice().getAmount() * quantity),
                new Money(0L),
                new Money(product.getPrice().getAmount() * quantity)
        );
        orderRepository.save(order);

        OrderItem item = new OrderItem(order, product, new Quantity(quantity), product.getPrice());
        orderItemRepository.save(item);

        eventPublisher.publishEvent(new OrderCompletedEvent(order.getId()));
    }
}
