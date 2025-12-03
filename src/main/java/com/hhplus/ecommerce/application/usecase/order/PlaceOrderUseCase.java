package com.hhplus.ecommerce.application.usecase.order;

import com.hhplus.ecommerce.domain.entity.*;
import com.hhplus.ecommerce.domain.event.OrderCompletedEvent;
import com.hhplus.ecommerce.domain.service.*;
import com.hhplus.ecommerce.domain.vo.Money;
import com.hhplus.ecommerce.domain.vo.Phone;
import com.hhplus.ecommerce.domain.vo.Quantity;
import com.hhplus.ecommerce.presentation.dto.CreateOrderRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 주문 생성 및 결제 UseCase
 *
 * User Story: "사용자가 주문을 생성하고 결제한다"
 *
 * 여러 Service를 조합하여 주문 플로우를 구성합니다:
 * - UserService: 사용자 조회, 잔액 검증/차감
 * - ProductService: 상품 조회, 재고 검증/차감
 * - OrderService: 주문 생성, 주문 아이템 생성
 * - CouponService: 쿠폰 사용, 할인 계산
 * - PaymentService: 결제 생성
 * - OutboxService: 데이터 플랫폼 전송 이벤트 저장 (Outbox Pattern)
 * - EventPublisher: 트랜잭션 커밋 후 이벤트 발행 (랭킹 업데이트 등)
 */
@Slf4j
@Service
public class PlaceOrderUseCase {

    private final OrderService orderService;
    private final ProductService productService;
    private final UserService userService;
    private final CouponService couponService;
    private final PaymentService paymentService;
    private final com.hhplus.ecommerce.domain.service.OutboxService outboxService;
    private final ApplicationEventPublisher eventPublisher;

    public PlaceOrderUseCase(OrderService orderService,
                            ProductService productService,
                            UserService userService,
                            CouponService couponService,
                            PaymentService paymentService,
                            com.hhplus.ecommerce.domain.service.OutboxService outboxService,
                            ApplicationEventPublisher eventPublisher) {
        this.orderService = orderService;
        this.productService = productService;
        this.userService = userService;
        this.couponService = couponService;
        this.paymentService = paymentService;
        this.outboxService = outboxService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 주문 생성 및 결제 실행
     *
     * 주의: 여러 Service를 조합하여 원자적으로 실행해야 하는 복잡한 비즈니스 로직입니다.
     * 각 Service 메서드들이 개별 트랜잭션(REQUIRED 전파)을 가지므로,
     * UseCase에서 트랜잭션을 시작하여 전체를 하나의 트랜잭션으로 묶습니다.
     *
     * 향후 개선: OrderService에 전체 주문 생성 로직을 이동하고 UseCase는 조합만 담당하도록 리팩토링 필요
     *
     * @param publicId 사용자 Public ID (UUID)
     * @param request 주문 요청 DTO
     * @return 생성된 주문
     */
    @Transactional
    public Order execute(String publicId, CreateOrderRequest request) {
        // 1. 사용자 조회
        User user = userService.getUserByPublicId(publicId);

        // 2. 상품 조회 및 수량 변환
        List<Long> productIds = request.items().stream()
                .map(CreateOrderRequest.OrderItemRequest::productId)
                .toList();
        List<Product> products = productService.getProducts(productIds);

        List<Quantity> quantities = request.items().stream()
                .map(item -> new Quantity(item.quantity()))
                .toList();

        // 3. 재고 검증 (주문 전에 미리 검증)
        for (int i = 0; i < products.size(); i++) {
            productService.validateStock(products.get(i), quantities.get(i));
        }

        // 4. 주문 금액 계산
        Money totalAmount = orderService.calculateTotalAmount(products, quantities);

        // 5. 쿠폰 적용 (선택적)
        UserCoupon userCoupon = null;
        Money discountAmount = Money.zero();

        if (request.userCouponId() != null) {
            userCoupon = couponService.useCoupon(request.userCouponId(), user.getId());
            discountAmount = couponService.calculateDiscount(userCoupon, totalAmount);
        }

        Money finalAmount = totalAmount.subtract(discountAmount);

        // 6. 잔액 검증 (결제 전에 미리 검증)
        userService.validateBalance(user, finalAmount);

        // 7. 주문 생성
        Phone shippingPhone = new Phone(request.shippingPhone());
        Order order = orderService.createOrder(
                user, userCoupon,
                request.recipientName(),
                request.shippingAddress(),
                shippingPhone,
                totalAmount, discountAmount, finalAmount
        );

        // 8. 주문 아이템 생성 및 재고 차감
        List<OrderItem> orderItems = new ArrayList<>();
        for (int i = 0; i < products.size(); i++) {
            Product product = products.get(i);
            Quantity quantity = quantities.get(i);

            // 재고 차감 (Service에서 처리)
            productService.decreaseStock(product.getId(), quantity);

            // 주문 아이템 생성
            OrderItem orderItem = orderService.createOrderItem(order, product, quantity);
            orderItems.add(orderItem);
        }

        // 9. 잔액 차감
        userService.deductBalanceByPublicId(publicId, finalAmount);

        // 10. 결제 생성
        Payment payment = paymentService.createPayment(order, finalAmount);

        // 11. 주문 상태 변경
        orderService.updateOrderStatus(order.getId(), com.hhplus.ecommerce.domain.enums.OrderStatus.PAID);

        // 12. 데이터 플랫폼 전송 이벤트 저장 (Outbox Pattern)
        // 트랜잭션 커밋 후 스케줄러가 비동기로 처리합니다.
        outboxService.savePaymentCompletedEvent(payment);

        // 13. 주문 완료 이벤트 발행 (트랜잭션 커밋 후 비동기로 랭킹 업데이트)
        // Redis 장애가 발생해도 주문 트랜잭션은 성공 처리됩니다.
        eventPublisher.publishEvent(new OrderCompletedEvent(order.getId(), orderItems));

        log.info("주문 생성 완료: 주문 ID={}, 사용자 Public ID={}, 최종 금액={}",
                order.getId(), publicId, finalAmount.getAmount());

        return order;
    }
}
