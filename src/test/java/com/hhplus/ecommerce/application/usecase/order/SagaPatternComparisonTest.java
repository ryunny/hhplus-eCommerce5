package com.hhplus.ecommerce.application.usecase.order;

import com.hhplus.ecommerce.BaseIntegrationTest;
import com.hhplus.ecommerce.domain.entity.*;
import com.hhplus.ecommerce.domain.enums.OrderStatus;
import com.hhplus.ecommerce.domain.repository.*;
import com.hhplus.ecommerce.domain.vo.*;
import com.hhplus.ecommerce.presentation.dto.CreateOrderRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Saga 패턴 비교 통합 테스트 (Orchestration vs Choreography)
 *
 * 두 패턴이 동일한 비즈니스 결과를 보장하는지 검증합니다.
 * - Orchestration: 중앙 관리자가 동기적으로 모든 로직 처리
 * - Choreography: 이벤트 기반으로 각 핸들러가 독립적으로 처리
 */
class SagaPatternComparisonTest extends BaseIntegrationTest {

    @Autowired
    private OrchestrationPlaceOrderUseCase orchestrationPlaceOrderUseCase;

    @Autowired
    private ChoreographyPlaceOrderUseCase choreographyPlaceOrderUseCase;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    @DisplayName("Orchestration 패턴: 주문 생성이 정상 동작한다")
    void orchestration_PlaceOrder_Success() throws InterruptedException {
        // given
        User user = createUser("홍길동", "hong@test.com", "010-1234-5678");
        user.chargeBalance(new Money(100000L));
        userRepository.save(user);

        Category category = categoryRepository.save(new Category("전자제품"));
        Product product = createProduct(category, "노트북", 50000L, 10);
        productRepository.save(product);

        CreateOrderRequest request = new CreateOrderRequest(
                List.of(new CreateOrderRequest.OrderItemRequest(product.getId(), 2)),
                null,
                "홍길동",
                "서울시 강남구",
                "010-1234-5678"
        );

        // when
        Order order = orchestrationPlaceOrderUseCase.execute(user.getPublicId(), request);

        // then - 주문 상태 검증
        assertThat(order).isNotNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getTotalAmount().getAmount()).isEqualTo(100000L);

        // then - 재고 차감 검증
        Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();
        assertThat(updatedProduct.getStock().getQuantity()).isEqualTo(8);

        // then - 잔액 차감 검증
        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updatedUser.getBalance().getAmount()).isEqualTo(0L);

        // then - 결제 생성 검증
        Payment payment = paymentRepository.findByOrderId(order.getId()).orElseThrow();
        assertThat(payment).isNotNull();

        // 비동기 이벤트 처리 대기 (인기상품 랭킹)
        Thread.sleep(1000);
    }

    @Test
    @DisplayName("Choreography 패턴: 주문 생성이 정상 동작한다")
    void choreography_PlaceOrder_Success() throws InterruptedException {
        // given
        User user = createUser("김철수", "kim@test.com", "010-2345-6789");
        user.chargeBalance(new Money(100000L));
        userRepository.save(user);

        Category category = categoryRepository.save(new Category("전자제품"));
        Product product = createProduct(category, "마우스", 10000L, 20);
        productRepository.save(product);

        CreateOrderRequest request = new CreateOrderRequest(
                List.of(new CreateOrderRequest.OrderItemRequest(product.getId(), 3)),
                null,
                "김철수",
                "서울시 강남구",
                "010-2345-6789"
        );

        // when
        Order order = choreographyPlaceOrderUseCase.execute(user.getPublicId(), request);

        // 비동기 이벤트 처리 대기 (재고 차감, 결제 처리 등)
        Thread.sleep(2000);

        // then - 주문 상태 검증
        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PAID);

        // then - 재고 차감 검증 (이벤트로 처리됨)
        Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();
        assertThat(updatedProduct.getStock().getQuantity()).isEqualTo(17);

        // then - 잔액 차감 검증 (이벤트로 처리됨)
        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updatedUser.getBalance().getAmount()).isEqualTo(70000L);

        // then - 결제 생성 검증 (이벤트로 처리됨)
        Payment payment = paymentRepository.findByOrderId(order.getId()).orElseThrow();
        assertThat(payment).isNotNull();
    }

    @Test
    @DisplayName("Orchestration 패턴: 재고 부족 시 보상 트랜잭션이 동작한다")
    void orchestration_StockInsufficient_CompensationWorks() {
        // given
        User user = createUser("이영희", "lee@test.com", "010-3456-7890");
        user.chargeBalance(new Money(100000L));
        userRepository.save(user);

        Category category = categoryRepository.save(new Category("전자제품"));
        Product product = createProduct(category, "키보드", 15000L, 5);
        productRepository.save(product);

        CreateOrderRequest request = new CreateOrderRequest(
                List.of(new CreateOrderRequest.OrderItemRequest(product.getId(), 10)),
                null,
                "이영희",
                "서울시 강남구",
                "010-3456-7890"
        );

        // when & then - 재고 부족으로 예외 발생
        assertThatThrownBy(() -> orchestrationPlaceOrderUseCase.execute(user.getPublicId(), request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("재고가 부족합니다");

        // then - 롤백 검증 (재고, 잔액 변경 없음)
        Product unchangedProduct = productRepository.findById(product.getId()).orElseThrow();
        assertThat(unchangedProduct.getStock().getQuantity()).isEqualTo(5);

        User unchangedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(unchangedUser.getBalance().getAmount()).isEqualTo(100000L);
    }

    @Test
    @DisplayName("두 패턴 모두 동일한 비즈니스 결과를 보장한다")
    void bothPatterns_ProduceSameBusinessResult() throws InterruptedException {
        // given - Orchestration용 데이터
        User user1 = createUser("패턴1", "pattern1@test.com", "010-1111-1111");
        user1.chargeBalance(new Money(50000L));
        userRepository.save(user1);

        Category category1 = categoryRepository.save(new Category("카테고리1"));
        Product product1 = createProduct(category1, "상품1", 10000L, 100);
        productRepository.save(product1);

        // given - Choreography용 데이터
        User user2 = createUser("패턴2", "pattern2@test.com", "010-2222-2222");
        user2.chargeBalance(new Money(50000L));
        userRepository.save(user2);

        Category category2 = categoryRepository.save(new Category("카테고리2"));
        Product product2 = createProduct(category2, "상품2", 10000L, 100);
        productRepository.save(product2);

        CreateOrderRequest request1 = new CreateOrderRequest(
                List.of(new CreateOrderRequest.OrderItemRequest(product1.getId(), 5)),
                null,
                "패턴1",
                "서울시 강남구",
                "010-1111-1111"
        );

        CreateOrderRequest request2 = new CreateOrderRequest(
                List.of(new CreateOrderRequest.OrderItemRequest(product2.getId(), 5)),
                null,
                "패턴2",
                "서울시 강남구",
                "010-2222-2222"
        );

        // when - 두 패턴으로 주문 생성
        Order order1 = orchestrationPlaceOrderUseCase.execute(user1.getPublicId(), request1);
        Order order2 = choreographyPlaceOrderUseCase.execute(user2.getPublicId(), request2);

        // 비동기 처리 대기
        Thread.sleep(2000);

        // then - 두 주문의 최종 상태가 동일한지 검증
        Order finalOrder1 = orderRepository.findById(order1.getId()).orElseThrow();
        Order finalOrder2 = orderRepository.findById(order2.getId()).orElseThrow();

        assertThat(finalOrder1.getStatus()).isEqualTo(finalOrder2.getStatus());
        assertThat(finalOrder1.getTotalAmount().getAmount()).isEqualTo(finalOrder2.getTotalAmount().getAmount());

        // then - 재고 차감 결과 동일
        Product finalProduct1 = productRepository.findById(product1.getId()).orElseThrow();
        Product finalProduct2 = productRepository.findById(product2.getId()).orElseThrow();

        assertThat(finalProduct1.getStock().getQuantity()).isEqualTo(95);
        assertThat(finalProduct2.getStock().getQuantity()).isEqualTo(95);

        // then - 잔액 차감 결과 동일
        User finalUser1 = userRepository.findById(user1.getId()).orElseThrow();
        User finalUser2 = userRepository.findById(user2.getId()).orElseThrow();

        assertThat(finalUser1.getBalance().getAmount()).isEqualTo(0L);
        assertThat(finalUser2.getBalance().getAmount()).isEqualTo(0L);
    }

    // Helper methods
    private User createUser(String name, String email, String phone) {
        return new User(name, new Email(email), new Phone(phone));
    }

    private Product createProduct(Category category, String name, Long price, int stock) {
        return new Product(category, name, "상품 설명", new Money(price), new Stock(stock));
    }
}
