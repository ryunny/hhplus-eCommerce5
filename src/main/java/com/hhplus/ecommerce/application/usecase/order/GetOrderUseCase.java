package com.hhplus.ecommerce.application.usecase.order;

import com.hhplus.ecommerce.application.query.GetOrderQuery;
import com.hhplus.ecommerce.domain.entity.Order;
import com.hhplus.ecommerce.domain.service.OrderService;
import org.springframework.stereotype.Service;

/**
 * 주문 상세 조회 UseCase
 *
 * User Story: "사용자가 주문 상세 정보를 조회한다"
 */
@Service
public class GetOrderUseCase {

    private final OrderService orderService;

    public GetOrderUseCase(OrderService orderService) {
        this.orderService = orderService;
    }

    public Order execute(GetOrderQuery query) {
        return orderService.getOrderByOrderNumber(query.orderNumber());
    }
}
