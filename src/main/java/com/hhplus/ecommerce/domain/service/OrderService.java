package com.hhplus.ecommerce.domain.service;

import com.hhplus.ecommerce.domain.entity.Order;
import com.hhplus.ecommerce.domain.entity.OrderItem;
import com.hhplus.ecommerce.domain.entity.Product;
import com.hhplus.ecommerce.domain.entity.User;
import com.hhplus.ecommerce.domain.entity.UserCoupon;
import com.hhplus.ecommerce.domain.enums.OrderStatus;
import com.hhplus.ecommerce.domain.repository.OrderItemRepository;
import com.hhplus.ecommerce.domain.repository.OrderRepository;
import com.hhplus.ecommerce.domain.vo.Money;
import com.hhplus.ecommerce.domain.vo.Phone;
import com.hhplus.ecommerce.domain.vo.Quantity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 주문 관련 비즈니스 로직을 처리하는 서비스
 */
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    public OrderService(OrderRepository orderRepository, OrderItemRepository orderItemRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
    }

    /**
     * 주문 총액 계산
     *
     * @return 총 주문 금액
     */
    public Money calculateTotalAmount(List<Product> products, List<Quantity> quantities) {
        if (products.size() != quantities.size()) {
            throw new IllegalArgumentException("상품과 수량의 개수가 일치하지 않습니다.");
        }

        Money total = Money.zero();
        for (int i = 0; i < products.size(); i++) {
            Product product = products.get(i);
            Quantity quantity = quantities.get(i);
            total = total.add(quantity.multiply(product.getPrice()));
        }
        return total;
    }

    /**
     * 주문 생성 (직접 배송 정보 입력)
     *
     * @param userCoupon 사용자 쿠폰 (optional, null 가능)
     * @return 생성된 주문
     */
    @Transactional
    public Order createOrder(User user, UserCoupon userCoupon,
                            String recipientName, String address, Phone phone,
                            Money totalAmount, Money discountAmount, Money finalAmount) {
        Order order = new Order(
                user,
                userCoupon,
                null, // ShippingAddress 미사용
                recipientName,
                address,
                phone,
                totalAmount,
                discountAmount,
                finalAmount,
                OrderStatus.PENDING
        );
        return orderRepository.save(order);
    }

    /**
     * 주문 아이템 생성
     *
     * @return 생성된 주문 아이템
     */
    @Transactional
    public OrderItem createOrderItem(Order order, Product product, Quantity quantity) {
        OrderItem orderItem = OrderItem.create(order, product, quantity);
        return orderItemRepository.save(orderItem);
    }

    /**
     * 주문 상태 변경
     */
    @Transactional
    public void updateOrderStatus(Long orderId, OrderStatus status) {
        Order order = orderRepository.findByIdOrThrow(orderId);
        order.updateStatus(status);
        // 더티 체킹으로 자동 저장 (save() 불필요)
    }

    /**
     * 주문 조회
     *
     * @return 주문
     */
    @Transactional(readOnly = true)
    public Order getOrder(Long orderId) {
        return orderRepository.findByIdOrThrow(orderId);
    }

    /**
     * 주문 조회 (Order Number 기반)
     *
     * @return 주문
     */
    @Transactional(readOnly = true)
    public Order getOrderByOrderNumber(String orderNumber) {
        return orderRepository.findByOrderNumberOrThrow(orderNumber);
    }

    /**
     * 사용자 주문 목록 조회
     *
     * @return 주문 목록
     */
    @Transactional(readOnly = true)
    public List<Order> getUserOrders(Long userId) {
        return orderRepository.findByUserId(userId);
    }

    /**
     * 사용자 주문 목록 조회 (Public ID 기반)
     *
     * @return 주문 목록
     */
    @Transactional(readOnly = true)
    public List<Order> getUserOrdersByPublicId(String publicId) {
        return orderRepository.findByUserPublicId(publicId);
    }

    /**
     * 주문 저장
     *
     * @return 저장된 주문
     */
    @Transactional
    public Order save(Order order) {
        return orderRepository.save(order);
    }
}
