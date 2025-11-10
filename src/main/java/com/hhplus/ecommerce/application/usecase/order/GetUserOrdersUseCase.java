package com.hhplus.ecommerce.application.usecase.order;

import com.hhplus.ecommerce.application.query.GetUserOrdersQuery;
import com.hhplus.ecommerce.domain.entity.Order;
import com.hhplus.ecommerce.domain.service.OrderService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 사용자 주문 목록 조회 UseCase
 *
 * User Story: "사용자가 자신의 주문 목록을 조회한다"
 */
@Service
public class GetUserOrdersUseCase {

    private final OrderService orderService;

    public GetUserOrdersUseCase(OrderService orderService) {
        this.orderService = orderService;
    }

    public List<Order> execute(GetUserOrdersQuery query) {
        return orderService.getUserOrdersByPublicId(query.publicId());
    }
}
