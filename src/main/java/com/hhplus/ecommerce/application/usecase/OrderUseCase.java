package com.hhplus.ecommerce.application.usecase;

import com.hhplus.ecommerce.domain.entity.*;
import com.hhplus.ecommerce.domain.enums.DataTransmissionStatus;
import com.hhplus.ecommerce.domain.enums.OrderStatus;
import com.hhplus.ecommerce.domain.enums.PaymentStatus;
import com.hhplus.ecommerce.domain.repository.*;
import com.hhplus.ecommerce.domain.vo.Money;
import com.hhplus.ecommerce.domain.vo.Phone;
import com.hhplus.ecommerce.domain.vo.Quantity;
import com.hhplus.ecommerce.presentation.dto.CreateOrderRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
public class OrderUseCase {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final UserCouponRepository userCouponRepository;

    // 상품별 락 객체를 관리하는 Map (재고 동시성 제어)
    private final ConcurrentHashMap<Long, ReentrantLock> productLocks = new ConcurrentHashMap<>();

    // 사용자별 락 객체를 관리하는 Map (잔액 동시성 제어)
    private final ConcurrentHashMap<Long, ReentrantLock> userLocks = new ConcurrentHashMap<>();

    private static final long LOCK_TIMEOUT_SECONDS = 10;

    public OrderUseCase(OrderRepository orderRepository,
                        OrderItemRepository orderItemRepository,
                        PaymentRepository paymentRepository,
                        UserRepository userRepository,
                        ProductRepository productRepository,
                        UserCouponRepository userCouponRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.userCouponRepository = userCouponRepository;
    }

    public Order createOrderAndPay(Long userId, CreateOrderRequest request) {
        // 사용자별 락 획득 (잔액 동시성 제어)
        ReentrantLock userLock = userLocks.computeIfAbsent(userId, k -> new ReentrantLock(true)); // fair lock

        // 보상 트랜잭션을 위한 실행 추적 리스트
        // 각 단계를 성공할 때마다 추가하여 "실제로 실행된 작업"만 추적
        List<String> executedSteps = new ArrayList<>();

        // 보상 트랜잭션용 데이터
        User user = null;
        UserCoupon usedCoupon = null;
        Order createdOrder = null;
        List<Product> stockDecreasedProducts = new ArrayList<>();
        List<Quantity> decreasedQuantities = new ArrayList<>();
        Money deductedAmount = Money.zero();

        try {
            // 타임아웃과 함께 락 획득 시도
            if (!userLock.tryLock(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("주문 처리 중 타임아웃이 발생했습니다. 다시 시도해주세요.");
            }

            try {
                // 1. 사용자 조회
                user = userRepository.findById(userId)
                        .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));

                // 2. 재고 조회 및 검증 (상품별 락으로 보호)
                List<Product> products = new ArrayList<>();
                List<Long> productIds = new ArrayList<>();
                List<ReentrantLock> acquiredProductLocks = new ArrayList<>();
                Money totalAmount = Money.zero();

                try {
                    for (CreateOrderRequest.OrderItemRequest itemRequest : request.items()) {
                        Long productId = itemRequest.productId();
                        productIds.add(productId);

                        // 상품별 락 획득 (재고 동시성 제어)
                        ReentrantLock productLock = productLocks.computeIfAbsent(productId, k -> new ReentrantLock(true));

                        if (!productLock.tryLock(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("상품 처리 중 타임아웃이 발생했습니다. 다시 시도해주세요.");
                        }
                        acquiredProductLocks.add(productLock);

                        try {
                            Product product = productRepository.findById(productId)
                                    .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + productId));

                            Quantity quantity = new Quantity(itemRequest.quantity());

                            // 재고 확인 (락 안에서 검증)
                            if (!product.hasSufficientStock(quantity)) {
                                throw new IllegalStateException("재고가 부족합니다: " + product.getName());
                            }

                            products.add(product);
                            Money itemTotal = quantity.multiply(product.getPrice());
                            totalAmount = totalAmount.add(itemTotal);
                        } finally {
                            productLock.unlock();
                        }
                    }
                } finally {
                    // 획득한 모든 상품 락 해제
                    for (ReentrantLock lock : acquiredProductLocks) {
                        if (lock.isHeldByCurrentThread()) {
                            lock.unlock();
                        }
                    }
                }

                // 3. 쿠폰 적용 (선택적)
                Money discountAmount = Money.zero();
                UserCoupon userCoupon = null;

                if (request.userCouponId() != null) {
                    userCoupon = userCouponRepository.findById(request.userCouponId())
                            .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다: " + request.userCouponId()));

                    // 쿠폰 검증
                    if (!userCoupon.getUser().getId().equals(userId)) {
                        throw new IllegalArgumentException("다른 사용자의 쿠폰입니다.");
                    }

                    // 쿠폰 사용
                    userCoupon.use();
                    userCouponRepository.save(userCoupon);

                    // 저장 성공 후에만 추적 리스트에 추가
                    usedCoupon = userCoupon;
                    executedSteps.add("COUPON_USED");

                    // 할인 금액 계산
                    Coupon coupon = userCoupon.getCoupon();
                    discountAmount = coupon.calculateDiscount(totalAmount);
                }

                Money finalAmount = totalAmount.subtract(discountAmount);

                // 4. 주문 생성
                Phone shippingPhone = new Phone(request.shippingPhone());
                Order order = new Order(
                        user,
                        userCoupon,
                        request.recipientName(),
                        request.shippingAddress(),
                        shippingPhone,
                        totalAmount,
                        discountAmount,
                        finalAmount,
                        OrderStatus.PENDING
                );
                orderRepository.save(order);

                // 저장 성공 후에만 추적
                createdOrder = order;
                executedSteps.add("ORDER_CREATED");

                // 5. 주문 아이템 생성 및 재고 차감 (상품별 락으로 보호)
                acquiredProductLocks.clear();
                try {
                    for (int i = 0; i < request.items().size(); i++) {
                        CreateOrderRequest.OrderItemRequest itemRequest = request.items().get(i);
                        Product product = products.get(i);
                        Long productId = productIds.get(i);

                        // 상품별 락 획득 (재고 차감 동시성 제어)
                        ReentrantLock productLock = productLocks.computeIfAbsent(productId, k -> new ReentrantLock(true));

                        if (!productLock.tryLock(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("재고 차감 중 타임아웃이 발생했습니다. 다시 시도해주세요.");
                        }
                        acquiredProductLocks.add(productLock);

                        try {
                            Quantity quantity = new Quantity(itemRequest.quantity());

                            // 재고 차감 (락 안에서 처리)
                            product.decreaseStock(quantity);
                            productRepository.save(product);

                            // 저장 성공 후에만 추적
                            stockDecreasedProducts.add(product);
                            decreasedQuantities.add(quantity);
                            executedSteps.add("STOCK_DECREASED:" + product.getId());

                            // 주문 아이템 생성
                            OrderItem orderItem = OrderItem.create(order, product, quantity);
                            orderItemRepository.save(orderItem);
                        } finally {
                            productLock.unlock();
                        }
                    }
                } finally {
                    // 획득한 모든 상품 락 해제
                    for (ReentrantLock lock : acquiredProductLocks) {
                        if (lock.isHeldByCurrentThread()) {
                            lock.unlock();
                        }
                    }
                }

                // 6. 잔액 차감 (userLock 안에서 처리)
                user.deductBalance(finalAmount);
                userRepository.save(user);

                // 저장 성공 후에만 추적
                deductedAmount = finalAmount;
                executedSteps.add("BALANCE_DEDUCTED");

                // 7. 결제 생성
                Payment payment = new Payment(
                        order,
                        finalAmount,
                        PaymentStatus.COMPLETED,
                        DataTransmissionStatus.PENDING
                );
                paymentRepository.save(payment);

                // 저장 성공 후에만 추적
                executedSteps.add("PAYMENT_CREATED");

                // 8. 주문 상태 변경
                order.updateStatus(OrderStatus.PAID);
                orderRepository.save(order);

                // 9. 데이터 플랫폼 전송 (비동기 처리로 시뮬레이션)
                sendToDataPlatform(payment);

                return order;
            } catch (Exception e) {
                // 보상 트랜잭션 수행 (Compensation Transaction)
                // executedSteps를 확인하여 "실제로 실행된 작업만" 복구
                compensateTransaction(executedSteps, user, usedCoupon, createdOrder,
                                      stockDecreasedProducts, decreasedQuantities, deductedAmount);
                throw e; // 원래 예외를 다시 던짐
            } finally {
                userLock.unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("주문 처리가 중단되었습니다.", e);
        }
    }

    /**
     * 보상 트랜잭션 수행 (Compensation Transaction)
     * 주문 처리 중 예외 발생 시 "실제로 실행된 작업만" 롤백합니다.
     *
     * @param executedSteps 실행된 단계 리스트 (예: ["COUPON_USED", "ORDER_CREATED", "STOCK_DECREASED:1"])
     */
    private void compensateTransaction(List<String> executedSteps, User user, UserCoupon usedCoupon,
                                        Order createdOrder, List<Product> stockDecreasedProducts,
                                        List<Quantity> decreasedQuantities, Money deductedAmount) {
        try {
            // 역순으로 복구 (나중에 실행된 것부터 롤백)
            for (int i = executedSteps.size() - 1; i >= 0; i--) {
                String step = executedSteps.get(i);

                try {
                    if (step.equals("BALANCE_DEDUCTED")) {
                        // 1. 잔액 복구
                        if (user != null) {
                            user.chargeBalance(deductedAmount);
                            userRepository.save(user);
                            log.info("[보상 트랜잭션] 잔액 복구: {}", deductedAmount.getAmount());
                        }

                    } else if (step.startsWith("STOCK_DECREASED:")) {
                        // 2. 재고 복구 (상품 ID로 찾아서 복구)
                        String productIdStr = step.split(":")[1];
                        Long productId = Long.parseLong(productIdStr);

                        // 해당 상품 찾기
                        for (int j = 0; j < stockDecreasedProducts.size(); j++) {
                            Product product = stockDecreasedProducts.get(j);
                            if (product.getId().equals(productId)) {
                                Quantity quantity = decreasedQuantities.get(j);

                                // 상품별 락 획득하여 재고 복구
                                ReentrantLock productLock = productLocks.computeIfAbsent(productId,
                                        k -> new ReentrantLock(true));
                                try {
                                    if (productLock.tryLock(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                                        try {
                                            product.increaseStock(quantity);
                                            productRepository.save(product);
                                            log.info("[보상 트랜잭션] 재고 복구: 상품 ID={}, 수량={}",
                                                    productId, quantity.getValue());
                                        } finally {
                                            productLock.unlock();
                                        }
                                    }
                                } catch (InterruptedException ie) {
                                    Thread.currentThread().interrupt();
                                }
                                break;
                            }
                        }

                    } else if (step.equals("ORDER_CREATED")) {
                        // 3. 주문 상태를 CANCELLED로 변경
                        if (createdOrder != null) {
                            createdOrder.updateStatus(OrderStatus.CANCELLED);
                            orderRepository.save(createdOrder);
                            log.info("[보상 트랜잭션] 주문 취소: 주문 ID={}", createdOrder.getId());
                        }

                    } else if (step.equals("COUPON_USED")) {
                        // 4. 쿠폰 복구
                        if (usedCoupon != null) {
                            usedCoupon.cancel();
                            userCouponRepository.save(usedCoupon);
                            log.info("[보상 트랜잭션] 쿠폰 복구: 쿠폰 ID={}", usedCoupon.getId());
                        }
                    }

                } catch (Exception stepException) {
                    // 개별 단계 복구 실패 시 로그만 기록하고 계속 진행
                    log.error("[보상 트랜잭션] 단계 복구 실패: {}", step, stepException);
                }
            }

        } catch (Exception compensationException) {
            // 보상 트랜잭션 전체 실패 시 로그 기록
            // 실제 환경에서는 별도의 보상 대기열이나 수동 처리를 위한 알림이 필요
            log.error("[보상 트랜잭션] 전체 실패", compensationException);
        }
    }

    private void sendToDataPlatform(Payment payment) {
        try {
            payment.updateDataTransmissionStatus(DataTransmissionStatus.SUCCESS);
            paymentRepository.save(payment);
        } catch (Exception e) {
            payment.updateDataTransmissionStatus(DataTransmissionStatus.FAILED);
            paymentRepository.save(payment);
        }
    }

    // 단순 조회는 @Transactional 불필요
    public List<Order> getUserOrders(Long userId) {
        return orderRepository.findByUserId(userId);
    }

    // 단순 조회는 @Transactional 불필요
    public Order getOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다: " + orderId));
    }
}
