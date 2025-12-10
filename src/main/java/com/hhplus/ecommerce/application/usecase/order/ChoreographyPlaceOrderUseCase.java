package com.hhplus.ecommerce.application.usecase.order;

import com.hhplus.ecommerce.domain.entity.*;
import com.hhplus.ecommerce.domain.event.OrderCreatedEvent;
import com.hhplus.ecommerce.domain.service.*;
import com.hhplus.ecommerce.domain.vo.Money;
import com.hhplus.ecommerce.domain.vo.Phone;
import com.hhplus.ecommerce.domain.vo.Quantity;
import com.hhplus.ecommerce.presentation.dto.CreateOrderRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 주문 생성 UseCase - Choreography 패턴
 *
 * ============================================
 * 📋 패턴: Choreography (이벤트 중심 방식)
 * ============================================
 *
 * 특징:
 * 1. UseCase는 주문 생성 + 이벤트 발행만 담당
 * 2. 핵심 비즈니스 로직을 이벤트 핸들러들이 처리
 * 3. 각 도메인이 독립적으로 이벤트를 구독하고 반응
 * 4. 보상 트랜잭션도 이벤트로 자동 처리
 *
 * 장점:
 * ✅ 느슨한 결합 (Service 간 의존성 제거)
 * ✅ 확장성 (새로운 도메인 추가 시 이벤트만 구독)
 * ✅ 병렬 처리 (재고, 결제, 쿠폰 동시 처리)
 * ✅ 보상 트랜잭션 자동화
 *
 * 단점:
 * ❌ 복잡한 흐름 (여러 핸들러에 로직 분산)
 * ❌ 디버깅 어려움 (비동기 처리)
 * ❌ 최종 일관성 (Eventual Consistency)
 * ❌ 모니터링 복잡
 *
 * 이벤트 흐름:
 * 1. UseCase: 주문 생성 (PENDING) → OrderCreatedEvent 발행
 * 2. [병렬 처리] 각 핸들러가 독립적으로 처리:
 *    - StockEventHandler: 재고 차감 → StockReservedEvent 또는 StockReservationFailedEvent
 *    - PaymentEventHandler: 결제 처리 → PaymentCompletedEvent 또는 PaymentFailedEvent
 *    - CouponEventHandler: 쿠폰 사용 → CouponUsedEvent 또는 CouponUsageFailedEvent
 * 3. OrderSagaEventHandler: 모든 이벤트 수집
 *    - 모두 성공: OrderConfirmedEvent 발행 → 주문 CONFIRMED
 *    - 하나라도 실패: OrderFailedEvent 발행 → 각 핸들러가 보상 트랜잭션
 *
 * 상세 문서: docs/EVENT_FLOW.md 참고
 */
@Slf4j
@Service
public class ChoreographyPlaceOrderUseCase {

    private final OrderService orderService;
    private final ProductService productService;
    private final UserService userService;
    private final CouponService couponService;
    private final OutboxService outboxService;

    public ChoreographyPlaceOrderUseCase(OrderService orderService,
                                         ProductService productService,
                                         UserService userService,
                                         CouponService couponService,
                                         OutboxService outboxService) {
        this.orderService = orderService;
        this.productService = productService;
        this.userService = userService;
        this.couponService = couponService;
        this.outboxService = outboxService;
    }

    /**
     * 주문 생성 실행
     *
     * ⚠️ 주의: 이 메서드는 주문을 PENDING 상태로 생성하고 이벤트만 발행합니다.
     * 실제 재고 차감, 결제, 쿠폰 사용은 이벤트 핸들러가 비동기로 처리합니다.
     *
     * @param publicId 사용자 Public ID (UUID)
     * @param request 주문 요청 DTO
     * @return 생성된 주문 (PENDING 상태)
     */
    @Transactional
    public Order execute(String publicId, CreateOrderRequest request) {
        log.info("===== [Choreography] 주문 생성 시작 =====");
        log.info("사용자: {}, 상품 수: {}", publicId, request.items().size());

        // 1. 사용자 조회
        User user = userService.getUserByPublicId(publicId);
        log.info("[1/7] 사용자 조회 완료: userId={}", user.getId());

        // 2. 상품 조회 (productId로 정렬하여 데드락 방지)
        List<CreateOrderRequest.OrderItemRequest> sortedItems = request.items().stream()
                .sorted(java.util.Comparator.comparing(CreateOrderRequest.OrderItemRequest::productId))
                .toList();

        List<Long> productIds = sortedItems.stream()
                .map(CreateOrderRequest.OrderItemRequest::productId)
                .toList();
        List<Product> products = productService.getProducts(productIds);

        List<Quantity> quantities = sortedItems.stream()
                .map(item -> new Quantity(item.quantity()))
                .toList();

        log.info("[2/7] 상품 조회 완료: {} 개", products.size());

        // 3. 재고 사전 검증 (빠른 실패)
        for (int i = 0; i < products.size(); i++) {
            productService.validateStock(products.get(i), quantities.get(i));
        }
        log.info("[3/7] 재고 사전 검증 완료");

        // 4. 주문 금액 계산
        Money totalAmount = orderService.calculateTotalAmount(products, quantities);
        log.info("[4/7] 주문 금액 계산 완료: totalAmount={}", totalAmount.getAmount());

        // 5. 쿠폰 할인 계산 (쿠폰 사용은 이벤트 핸들러에서 처리)
        Money discountAmount = Money.zero();
        if (request.userCouponId() != null) {
            UserCoupon userCoupon = couponService.getUserCoupon(request.userCouponId());
            discountAmount = couponService.calculateDiscount(userCoupon, totalAmount);
            log.info("[5/7] 쿠폰 할인 계산 완료: discount={}", discountAmount.getAmount());
        } else {
            log.info("[5/7] 쿠폰 미사용");
        }

        Money finalAmount = totalAmount.subtract(discountAmount);

        // 6. 잔액 사전 검증 (빠른 실패)
        userService.validateBalance(user, finalAmount);
        log.info("[6/7] 잔액 사전 검증 완료");

        // 7. 주문 생성 (PENDING 상태)
        Phone shippingPhone = new Phone(request.shippingPhone());
        UserCoupon userCoupon = null;
        if (request.userCouponId() != null) {
            userCoupon = couponService.getUserCoupon(request.userCouponId());
        }

        Order order = orderService.createOrder(
                user, userCoupon,
                request.recipientName(),
                request.shippingAddress(),
                shippingPhone,
                totalAmount, discountAmount, finalAmount
        );

        log.info("[7/7] 주문 생성 완료: orderId={}, status=PENDING", order.getId());

        // ==========================================
        // 이벤트 발행 (핵심 로직은 이벤트 핸들러가 처리)
        // ==========================================

        // 8. 주문 생성 이벤트를 Outbox에 저장
        List<OrderCreatedEvent.OrderItem> eventItems = sortedItems.stream()
                .map(item -> new OrderCreatedEvent.OrderItem(
                        item.productId(),
                        item.quantity()
                ))
                .toList();

        try {
            OrderCreatedEvent event = new OrderCreatedEvent(
                    order.getId(),
                    user.getId(),
                    eventItems,
                    totalAmount,
                    discountAmount,
                    finalAmount,
                    request.userCouponId()
            );

            // Outbox에 저장 (트랜잭션과 함께 커밋됨)
            outboxService.saveEvent("ORDER_CREATED", order.getId(), event);

            log.info("===== OrderCreatedEvent Outbox 저장 완료 =====");
            log.info("→ 스케줄러가 Outbox를 폴링하여 이벤트 발행합니다");
            log.info("→ 다음 단계는 이벤트 핸들러들이 병렬로 처리합니다:");
            log.info("  ├─ StockEventHandler: 재고 차감");
            log.info("  ├─ PaymentEventHandler: 결제 처리 (잔액 차감)");
            log.info("  └─ CouponEventHandler: 쿠폰 사용");
            log.info("");
            log.info("→ OrderSagaEventHandler가 결과를 수집합니다:");
            log.info("  ├─ 모두 성공 → CONFIRMED");
            log.info("  └─ 하나라도 실패 → FAILED + 자동 보상 트랜잭션");

        } catch (Exception e) {
            log.error("❌ OrderCreatedEvent Outbox 저장 실패: orderId={}", order.getId(), e);

            // Outbox 저장 실패 시 주문 실패 처리
            try {
                order.markAsFailed("이벤트 저장 실패: " + e.getMessage());
                orderService.save(order);
            } catch (Exception updateError) {
                log.error("❌ 주문 상태 업데이트 실패: orderId={}", order.getId(), updateError);
            }

            throw new IllegalStateException("주문 처리 중 오류가 발생했습니다", e);
        }

        log.info("===== [Choreography] 주문 생성 응답 반환 =====");
        log.info("주문 ID: {}, 상태: PENDING", order.getId());
        log.info("⚠️ 주문은 비동기로 처리됩니다. 최종 상태는 주문 조회 API로 확인하세요.");

        return order;
    }
}
