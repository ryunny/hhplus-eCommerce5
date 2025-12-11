package com.hhplus.ecommerce.application.usecase.order;

import com.hhplus.ecommerce.domain.entity.*;
import com.hhplus.ecommerce.domain.enums.OrderStatus;
import com.hhplus.ecommerce.domain.event.OrderCompletedEvent;
import com.hhplus.ecommerce.domain.service.*;
import com.hhplus.ecommerce.domain.vo.Money;
import com.hhplus.ecommerce.domain.vo.Phone;
import com.hhplus.ecommerce.domain.vo.Quantity;
import com.hhplus.ecommerce.presentation.dto.CreateOrderRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 주문 생성 UseCase - Orchestration + 이벤트 패턴
 *
 * ============================================
 * 📋 패턴: Orchestration (중앙 관리자 방식)
 * ============================================
 *
 * 특징:
 * 1. UseCase가 중앙 관리자(Orchestrator) 역할
 * 2. 핵심 비즈니스 로직을 동기적으로 순서대로 실행
 * 3. 보상 트랜잭션을 UseCase에서 직접 관리
 * 4. 부가 기능은 이벤트로 비동기 처리
 *
 * 장점:
 * ✅ 명확한 실행 순서 (코드로 흐름이 보임)
 * ✅ 디버깅 용이 (스택 트레이스로 추적 가능)
 * ✅ 트랜잭션 관리 단순 (보상 로직이 한 곳에)
 * ✅ 성능 예측 가능 (동기 처리)
 *
 * 단점:
 * ❌ Service 간 강한 결합
 * ❌ UseCase가 복잡해질 수 있음
 * ❌ 새로운 도메인 추가 시 UseCase 수정 필요
 *
 * 실행 흐름:
 * 1. 사용자 조회
 * 2. 상품 조회 및 재고 검증
 * 3. 주문 금액 계산
 * 4. 쿠폰 적용 (선택)
 * 5. 잔액 검증
 * 6. 주문 생성
 * 7. 재고 차감 ← 실패 시 여기서부터 보상
 * 8. 쿠폰 사용 ← 실패 시 재고 + 쿠폰 보상
 * 9. 잔액 차감 ← 실패 시 재고 + 쿠폰 + 잔액 보상
 * 10. 결제 생성
 * 11. 주문 상태 변경 (PAID)
 * 12. [비동기] 데이터 플랫폼 전송 (Outbox)
 * 13. [비동기] 랭킹 업데이트 (이벤트)
 */
@Slf4j
@Service
public class OrchestrationPlaceOrderUseCase {

    private final OrderService orderService;
    private final ProductService productService;
    private final UserService userService;
    private final CouponService couponService;
    private final PaymentService paymentService;
    private final OutboxService outboxService;
    private final ApplicationEventPublisher eventPublisher;

    public OrchestrationPlaceOrderUseCase(OrderService orderService,
                                          ProductService productService,
                                          UserService userService,
                                          CouponService couponService,
                                          PaymentService paymentService,
                                          OutboxService outboxService,
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
     * @param publicId 사용자 Public ID (UUID)
     * @param request 주문 요청 DTO
     * @return 생성된 주문 (PAID 상태)
     */
    public Order execute(String publicId, CreateOrderRequest request) {
        log.info("===== [Orchestration] 주문 생성 시작 =====");
        log.info("사용자: {}, 상품 수: {}", publicId, request.items().size());

        User user = userService.getUserByPublicId(publicId);
        log.info("[1/11] 사용자 조회 완료: userId={}", user.getId());

        // 상품 조회 및 수량 변환 (productId로 정렬하여 데드락 방지)
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

        log.info("[2/11] 상품 조회 완료: {} 개", products.size());

        // 재고 검증 (주문 전에 미리 검증 - 빠른 실패)
        for (int i = 0; i < products.size(); i++) {
            productService.validateStock(products.get(i), quantities.get(i));
        }
        log.info("[3/11] 재고 사전 검증 완료");

        // 주문 금액 계산
        Money totalAmount = orderService.calculateTotalAmount(products, quantities);
        log.info("[4/11] 주문 금액 계산 완료: totalAmount={}", totalAmount.getAmount());

        // 쿠폰 적용 (선택적)
        UserCoupon userCoupon = null;
        Money discountAmount = Money.zero();

        if (request.userCouponId() != null) {
            userCoupon = couponService.getUserCoupon(request.userCouponId());
            discountAmount = couponService.calculateDiscount(userCoupon, totalAmount);
            log.info("[5/11] 쿠폰 할인 계산 완료: discount={}", discountAmount.getAmount());
        } else {
            log.info("[5/11] 쿠폰 미사용");
        }

        Money finalAmount = totalAmount.subtract(discountAmount);

        // 잔액 검증 (결제 전에 미리 검증 - 빠른 실패)
        userService.validateBalance(user, finalAmount);
        log.info("[6/11] 잔액 사전 검증 완료");

        // 주문 생성 (초기 상태: PENDING)
        Phone shippingPhone = new Phone(request.shippingPhone());
        Order order = orderService.createOrder(
                user, userCoupon,
                request.recipientName(),
                request.shippingAddress(),
                shippingPhone,
                totalAmount, discountAmount, finalAmount
        );
        log.info("[7/11] 주문 생성 완료: orderId={}, status=PENDING", order.getId());

        // ==========================================
        // 핵심 트랜잭션 영역 (보상 트랜잭션 포함)
        // ==========================================

        List<OrderItem> orderItems = new ArrayList<>();
        List<Product> decreasedProducts = new ArrayList<>();
        List<Quantity> decreasedQuantities = new ArrayList<>();
        boolean couponUsed = false;
        boolean balanceDeducted = false;
        Payment payment;

        try {
            // 주문 아이템 생성 및 재고 차감
            for (int i = 0; i < products.size(); i++) {
                Product product = products.get(i);
                Quantity quantity = quantities.get(i);

                // 재고 차감 (각 Service에서 분산락 처리)
                productService.decreaseStock(product.getId(), quantity);
                decreasedProducts.add(product);
                decreasedQuantities.add(quantity);

                // 주문 아이템 생성
                OrderItem orderItem = orderService.createOrderItem(order, product, quantity);
                orderItems.add(orderItem);
            }
            log.info("[8/11] 재고 차감 및 주문 아이템 생성 완료: {} 개", orderItems.size());

            // 쿠폰 사용
            if (request.userCouponId() != null) {
                couponService.useCoupon(request.userCouponId(), user.getId());
                couponUsed = true;
                log.info("[9/11] 쿠폰 사용 완료: userCouponId={}", request.userCouponId());
            } else {
                log.info("[9/11] 쿠폰 미사용");
            }

            // 잔액 차감
            userService.deductBalanceByPublicId(publicId, finalAmount);
            balanceDeducted = true;
            log.info("[10/11] 잔액 차감 완료: amount={}", finalAmount.getAmount());

            // 결제 생성
            payment = paymentService.createPayment(order, finalAmount);
            log.info("[11/11] 결제 생성 완료: paymentId={}", payment.getId());

            // 주문 상태 변경 (PAID)
            orderService.updateOrderStatus(order.getId(), OrderStatus.PAID);
            log.info("주문 상태 변경 완료: orderId={}, status=PAID", order.getId());

        } catch (Exception e) {
            log.error("❌ 주문 처리 중 실패. 보상 트랜잭션 시작: orderId={}, user={}",
                    order.getId(), publicId, e);

            // ==========================================
            // 보상 트랜잭션 (Compensation Transaction)
            // ==========================================

            // 잔액 복구
            if (balanceDeducted) {
                try {
                    userService.chargeBalanceByPublicId(publicId, finalAmount);
                    log.info("✅ 잔액 복구 완료: amount={}", finalAmount.getAmount());
                } catch (Exception rollbackError) {
                    log.error("❌ 잔액 복구 실패 - 관리자 확인 필요: userId={}, amount={}",
                            user.getId(), finalAmount.getAmount(), rollbackError);
                }
            }

            // 쿠폰 복구
            if (couponUsed) {
                try {
                    couponService.cancelCoupon(request.userCouponId());
                    log.info("✅ 쿠폰 복구 완료: userCouponId={}", request.userCouponId());
                } catch (Exception rollbackError) {
                    log.error("❌ 쿠폰 복구 실패 - 관리자 확인 필요: userCouponId={}",
                            request.userCouponId(), rollbackError);
                }
            }

            // 재고 복구
            for (int i = 0; i < decreasedProducts.size(); i++) {
                try {
                    productService.increaseStock(
                            decreasedProducts.get(i).getId(),
                            decreasedQuantities.get(i)
                    );
                    log.info("✅ 재고 복구 완료: productId={}, quantity={}",
                            decreasedProducts.get(i).getId(),
                            decreasedQuantities.get(i).getValue());
                } catch (Exception rollbackError) {
                    log.error("❌ 재고 복구 실패 - 관리자 확인 필요: productId={}",
                            decreasedProducts.get(i).getId(), rollbackError);
                }
            }

            // 주문 상태 변경 (FAILED)
            try {
                orderService.updateOrderStatus(order.getId(), OrderStatus.FAILED);
                log.info("주문 상태 변경 완료: orderId={}, status=FAILED", order.getId());
            } catch (Exception statusError) {
                log.error("❌ 주문 상태 변경 실패: orderId={}", order.getId(), statusError);
            }

            throw new IllegalStateException("주문 처리 중 오류가 발생했습니다: " + e.getMessage(), e);
        }

        // ==========================================
        // 비동기 처리 영역 (이벤트 발행)
        // ==========================================

        // 데이터 플랫폼 전송 이벤트 저장 (Outbox Pattern)
        try {
            outboxService.savePaymentCompletedEvent(payment);
            log.info("📤 Outbox 이벤트 저장 완료: paymentId={} (스케줄러가 비동기 처리)", payment.getId());
        } catch (Exception e) {
            log.error("⚠️ Outbox 이벤트 저장 실패 (주문은 성공): paymentId={}", payment.getId(), e);
        }

        // 주문 완료 이벤트 발행 (비동기 - 랭킹 업데이트 등)
        try {
            eventPublisher.publishEvent(new OrderCompletedEvent(order.getId(), orderItems));
            log.info("📢 OrderCompletedEvent 발행 완료: orderId={} (비동기로 랭킹 업데이트)", order.getId());
        } catch (Exception e) {
            log.error("⚠️ 이벤트 발행 실패 (주문은 성공): orderId={}", order.getId(), e);
        }

        log.info("===== [Orchestration] 주문 생성 완료 =====");
        log.info("주문 ID: {}, 상태: PAID, 최종 금액: {}",
                order.getId(), finalAmount.getAmount());

        return order;
    }
}
