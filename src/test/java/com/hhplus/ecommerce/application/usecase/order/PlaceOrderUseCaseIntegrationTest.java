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
 * PlaceOrderUseCase 통합 테스트
 *
 * 이커머스의 핵심 비즈니스 플로우인 주문 생성 전체 과정을 검증합니다.
 * - 사용자 조회
 * - 상품 조회 및 재고 검증
 * - 주문 금액 계산
 * - 쿠폰 적용 (선택적)
 * - 잔액 검증 및 차감
 * - 주문 생성
 * - 재고 차감
 * - 결제 생성
 * - 주문 상태 변경
 */
class PlaceOrderUseCaseIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private PlaceOrderUseCase placeOrderUseCase;

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

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Test
    @DisplayName("주문 생성 전체 플로우가 정상 동작한다")
    void placeOrder_Success() {
        // given - 사용자 생성 및 잔액 충전
        User user = createUser("홍길동", "hong@test.com", "010-1234-5678");
        user.chargeBalance(new Money(100000L));
        userRepository.save(user);

        // given - 카테고리 및 상품 생성
        Category category = categoryRepository.save(new Category("전자제품"));
        Product product1 = createProduct(category, "노트북", 50000L, 10);
        Product product2 = createProduct(category, "마우스", 10000L, 20);
        productRepository.save(product1);
        productRepository.save(product2);

        // given - 주문 요청 생성
        CreateOrderRequest request = new CreateOrderRequest(
                List.of(
                        new CreateOrderRequest.OrderItemRequest(product1.getId(), 1),
                        new CreateOrderRequest.OrderItemRequest(product2.getId(), 2)
                ),
                null, // 쿠폰 미사용
                "홍길동",
                "서울시 강남구",
                "010-1234-5678"
        );

        // when - 주문 생성
        Order order = placeOrderUseCase.execute(user.getPublicId(), request);

        // then - 주문 검증
        assertThat(order).isNotNull();
        assertThat(order.getOrderNumber()).isNotNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getTotalAmount().getAmount()).isEqualTo(70000L); // 50000 + 10000*2
        assertThat(order.getDiscountAmount().getAmount()).isEqualTo(0L);
        assertThat(order.getFinalAmount().getAmount()).isEqualTo(70000L);

        // then - 주문 아이템 검증
        List<OrderItem> orderItems = orderItemRepository.findByOrderId(order.getId());
        assertThat(orderItems).hasSize(2);
        assertThat(orderItems.get(0).getQuantity().getValue()).isEqualTo(1);
        assertThat(orderItems.get(1).getQuantity().getValue()).isEqualTo(2);

        // then - 재고 차감 검증
        Product updatedProduct1 = productRepository.findById(product1.getId()).orElseThrow();
        Product updatedProduct2 = productRepository.findById(product2.getId()).orElseThrow();
        assertThat(updatedProduct1.getStock().getQuantity()).isEqualTo(9); // 10 - 1
        assertThat(updatedProduct2.getStock().getQuantity()).isEqualTo(18); // 20 - 2

        // then - 잔액 차감 검증
        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updatedUser.getBalance().getAmount()).isEqualTo(30000L); // 100000 - 70000

        // then - 결제 생성 검증
        Payment payment = paymentRepository.findByOrderId(order.getId()).orElseThrow();
        assertThat(payment).isNotNull();
        assertThat(payment.getPaymentId()).isNotNull();
        assertThat(payment.getPaidAmount().getAmount()).isEqualTo(70000L);
    }

    @Test
    @DisplayName("쿠폰을 사용한 주문이 정상 처리된다")
    void placeOrderWithCoupon_Success() {
        // given - 사용자 생성 및 잔액 충전
        User user = createUser("김철수", "kim@test.com", "010-2345-6789");
        user.chargeBalance(new Money(100000L));
        userRepository.save(user);

        // given - 상품 생성
        Category category = categoryRepository.save(new Category("전자제품"));
        Product product = createProduct(category, "노트북", 50000L, 10);
        productRepository.save(product);

        // given - 쿠폰 생성 및 발급
        Coupon coupon = createPercentageCoupon("10% 할인", 10);
        couponRepository.save(coupon);
        UserCoupon userCoupon = userCouponRepository.save(
                new UserCoupon(user, coupon, com.hhplus.ecommerce.domain.enums.CouponStatus.UNUSED, java.time.LocalDateTime.now().plusDays(7))
        );

        // given - 주문 요청 생성
        CreateOrderRequest request = new CreateOrderRequest(
                List.of(new CreateOrderRequest.OrderItemRequest(product.getId(), 1)),
                userCoupon.getId(), // 쿠폰 사용
                "김철수",
                "서울시 강남구",
                "010-2345-6789"
        );

        // when - 주문 생성
        Order order = placeOrderUseCase.execute(user.getPublicId(), request);

        // then - 할인 금액 검증
        assertThat(order.getTotalAmount().getAmount()).isEqualTo(50000L);
        assertThat(order.getDiscountAmount().getAmount()).isEqualTo(5000L); // 10% 할인
        assertThat(order.getFinalAmount().getAmount()).isEqualTo(45000L); // 50000 - 5000

        // then - 잔액 차감 검증
        User updatedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(updatedUser.getBalance().getAmount()).isEqualTo(55000L); // 100000 - 45000

        // then - 쿠폰 사용 검증
        UserCoupon updatedUserCoupon = userCouponRepository.findById(userCoupon.getId()).orElseThrow();
        assertThat(updatedUserCoupon.getStatus()).isEqualTo(com.hhplus.ecommerce.domain.enums.CouponStatus.USED);
    }

    @Test
    @DisplayName("재고가 부족하면 주문이 실패한다")
    void placeOrder_FailByInsufficientStock() {
        // given - 사용자 생성 및 잔액 충전
        User user = createUser("이영희", "lee@test.com", "010-3456-7890");
        user.chargeBalance(new Money(100000L));
        userRepository.save(user);

        // given - 재고가 부족한 상품 생성
        Category category = categoryRepository.save(new Category("전자제품"));
        Product product = createProduct(category, "노트북", 50000L, 5); // 재고 5개
        productRepository.save(product);

        // given - 재고보다 많은 수량 주문 시도
        CreateOrderRequest request = new CreateOrderRequest(
                List.of(new CreateOrderRequest.OrderItemRequest(product.getId(), 10)), // 10개 주문
                null,
                "이영희",
                "서울시 강남구",
                "010-3456-7890"
        );

        // when & then - 재고 부족으로 예외 발생
        assertThatThrownBy(() -> placeOrderUseCase.execute(user.getPublicId(), request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("재고가 부족합니다");

        // then - 재고는 변경되지 않아야 함 (롤백)
        Product unchangedProduct = productRepository.findById(product.getId()).orElseThrow();
        assertThat(unchangedProduct.getStock().getQuantity()).isEqualTo(5);

        // then - 잔액도 변경되지 않아야 함 (롤백)
        User unchangedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(unchangedUser.getBalance().getAmount()).isEqualTo(100000L);

        // then - 주문이 생성되지 않아야 함
        List<Order> orders = orderRepository.findByUserId(user.getId());
        assertThat(orders).isEmpty();
    }

    @Test
    @DisplayName("잔액이 부족하면 주문이 실패한다")
    void placeOrder_FailByInsufficientBalance() {
        // given - 잔액이 부족한 사용자
        User user = createUser("박민수", "park@test.com", "010-4567-8901");
        user.chargeBalance(new Money(10000L)); // 잔액 10,000원
        userRepository.save(user);

        // given - 상품 생성
        Category category = categoryRepository.save(new Category("전자제품"));
        Product product = createProduct(category, "노트북", 50000L, 10);
        productRepository.save(product);

        // given - 잔액보다 비싼 상품 주문 시도
        CreateOrderRequest request = new CreateOrderRequest(
                List.of(new CreateOrderRequest.OrderItemRequest(product.getId(), 1)),
                null,
                "박민수",
                "서울시 강남구",
                "010-4567-8901"
        );

        // when & then - 잔액 부족으로 예외 발생
        assertThatThrownBy(() -> placeOrderUseCase.execute(user.getPublicId(), request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("잔액이 부족합니다");

        // then - 재고는 변경되지 않아야 함 (롤백)
        Product unchangedProduct = productRepository.findById(product.getId()).orElseThrow();
        assertThat(unchangedProduct.getStock().getQuantity()).isEqualTo(10);

        // then - 잔액도 변경되지 않아야 함
        User unchangedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(unchangedUser.getBalance().getAmount()).isEqualTo(10000L);
    }

    @Test
    @DisplayName("여러 상품을 주문하면 모든 재고가 정확히 차감된다")
    void placeOrderMultipleProducts_StockDecreasedCorrectly() {
        // given - 사용자 생성 및 잔액 충전
        User user = createUser("최지은", "choi@test.com", "010-5678-9012");
        user.chargeBalance(new Money(200000L));
        userRepository.save(user);

        // given - 여러 상품 생성
        Category category = categoryRepository.save(new Category("전자제품"));
        Product product1 = createProduct(category, "노트북", 50000L, 100);
        Product product2 = createProduct(category, "마우스", 10000L, 200);
        Product product3 = createProduct(category, "키보드", 15000L, 150);
        productRepository.save(product1);
        productRepository.save(product2);
        productRepository.save(product3);

        // given - 여러 상품 주문
        CreateOrderRequest request = new CreateOrderRequest(
                List.of(
                        new CreateOrderRequest.OrderItemRequest(product1.getId(), 2),
                        new CreateOrderRequest.OrderItemRequest(product2.getId(), 3),
                        new CreateOrderRequest.OrderItemRequest(product3.getId(), 1)
                ),
                null,
                "최지은",
                "서울시 강남구",
                "010-5678-9012"
        );

        // when - 주문 생성
        Order order = placeOrderUseCase.execute(user.getPublicId(), request);

        // then - 총 금액 검증
        assertThat(order.getTotalAmount().getAmount()).isEqualTo(145000L); // 100000 + 30000 + 15000

        // then - 모든 재고가 정확히 차감되었는지 검증
        Product updated1 = productRepository.findById(product1.getId()).orElseThrow();
        Product updated2 = productRepository.findById(product2.getId()).orElseThrow();
        Product updated3 = productRepository.findById(product3.getId()).orElseThrow();
        assertThat(updated1.getStock().getQuantity()).isEqualTo(98);  // 100 - 2
        assertThat(updated2.getStock().getQuantity()).isEqualTo(197); // 200 - 3
        assertThat(updated3.getStock().getQuantity()).isEqualTo(149); // 150 - 1

        // then - 주문 아이템 수 검증
        List<OrderItem> orderItems = orderItemRepository.findByOrderId(order.getId());
        assertThat(orderItems).hasSize(3);
    }

    // Helper methods
    private User createUser(String name, String email, String phone) {
        return new User(name, new Email(email), new Phone(phone));
    }

    private Product createProduct(Category category, String name, Long price, int stock) {
        return new Product(category, name, "상품 설명", new Money(price), new Stock(stock));
    }

    private Coupon createPercentageCoupon(String name, int discountRate) {
        return new Coupon(
                name,
                "PERCENTAGE",
                new DiscountRate(discountRate),
                null,
                new Money(0L),
                100,
                java.time.LocalDateTime.now().minusDays(1),
                java.time.LocalDateTime.now().plusDays(30)
        );
    }
}
