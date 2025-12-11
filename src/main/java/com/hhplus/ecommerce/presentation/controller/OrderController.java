package com.hhplus.ecommerce.presentation.controller;

import com.hhplus.ecommerce.application.query.GetOrderQuery;
import com.hhplus.ecommerce.application.query.GetUserOrdersQuery;
import com.hhplus.ecommerce.application.usecase.order.ChoreographyPlaceOrderUseCase;
import com.hhplus.ecommerce.application.usecase.order.GetOrderUseCase;
import com.hhplus.ecommerce.application.usecase.order.GetUserOrdersUseCase;
import com.hhplus.ecommerce.application.usecase.order.OrchestrationPlaceOrderUseCase;
import com.hhplus.ecommerce.domain.entity.Order;
import com.hhplus.ecommerce.presentation.dto.CreateOrderRequest;
import com.hhplus.ecommerce.presentation.dto.OrderResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrchestrationPlaceOrderUseCase orchestrationPlaceOrderUseCase;
    private final ChoreographyPlaceOrderUseCase choreographyPlaceOrderUseCase;
    private final GetOrderUseCase getOrderUseCase;
    private final GetUserOrdersUseCase getUserOrdersUseCase;

    /**
     * 주문 생성 - Orchestration 패턴 (기본)
     *
     * 특징:
     * - UseCase가 모든 로직을 동기적으로 처리
     * - 보상 트랜잭션을 UseCase에서 관리
     * - 즉시 PAID 상태로 완료
     * - 부가 기능만 이벤트로 비동기 처리
     */
    @PostMapping("/orchestration/{publicId}")
    public ResponseEntity<OrderResponse> createOrderOrchestration(
            @PathVariable String publicId,
            @RequestBody CreateOrderRequest request) {
        Order order = orchestrationPlaceOrderUseCase.execute(publicId, request);
        return ResponseEntity.ok(OrderResponse.from(order));
    }

    /**
     * 주문 생성 - Choreography 패턴
     *
     * 특징:
     * - UseCase는 주문 생성 + 이벤트 발행만
     * - 핵심 로직을 이벤트 핸들러가 비동기 처리
     * - PENDING 상태로 반환 → 이후 CONFIRMED/FAILED로 변경
     * - 보상 트랜잭션 자동화
     */
    @PostMapping("/choreography/{publicId}")
    public ResponseEntity<OrderResponse> createOrderChoreography(
            @PathVariable String publicId,
            @RequestBody CreateOrderRequest request) {
        Order order = choreographyPlaceOrderUseCase.execute(publicId, request);
        return ResponseEntity.ok(OrderResponse.from(order));
    }

    /**
     * 주문 생성 - 기본 엔드포인트 (Orchestration 사용)
     *
     * 하위 호환성을 위해 기본값은 Orchestration 패턴 사용
     */
    @PostMapping("/{publicId}")
    public ResponseEntity<OrderResponse> createOrder(
            @PathVariable String publicId,
            @RequestBody CreateOrderRequest request) {
        Order order = orchestrationPlaceOrderUseCase.execute(publicId, request);
        return ResponseEntity.ok(OrderResponse.from(order));
    }

    @GetMapping("/{orderNumber}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable String orderNumber) {
        GetOrderQuery query = new GetOrderQuery(orderNumber);
        Order order = getOrderUseCase.execute(query);
        return ResponseEntity.ok(OrderResponse.from(order));
    }

    @GetMapping("/user/{publicId}")
    public ResponseEntity<List<OrderResponse>> getUserOrders(@PathVariable String publicId) {
        GetUserOrdersQuery query = new GetUserOrdersQuery(publicId);
        List<Order> orders = getUserOrdersUseCase.execute(query);
        List<OrderResponse> response = orders.stream()
                .map(OrderResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }
}
