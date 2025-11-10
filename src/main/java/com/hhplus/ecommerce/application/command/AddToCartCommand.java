package com.hhplus.ecommerce.application.command;

/**
 * 장바구니 추가 Command
 */
public record AddToCartCommand(
        String publicId,
        Long productId,
        Integer quantity
) {
}
