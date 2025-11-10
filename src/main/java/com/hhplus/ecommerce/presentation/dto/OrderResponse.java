package com.hhplus.ecommerce.presentation.dto;

import com.hhplus.ecommerce.domain.entity.Order;

import java.time.LocalDateTime;

public record OrderResponse(
        String orderNumber,
        String userPublicId,
        Long totalAmount,
        Long discountAmount,
        Long finalAmount,
        String status,
        LocalDateTime createdAt
) {
    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getOrderNumber(),
                order.getUser().getPublicId(),
                order.getTotalAmount().getAmount(),
                order.getDiscountAmount().getAmount(),
                order.getFinalAmount().getAmount(),
                order.getStatus().name(),
                order.getCreatedAt()
        );
    }
}
