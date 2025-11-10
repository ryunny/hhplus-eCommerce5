package com.hhplus.ecommerce.application.usecase.cart;

import com.hhplus.ecommerce.domain.service.CartService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장바구니 전체 비우기 UseCase
 *
 * User Story: "사용자가 장바구니를 전체 비운다"
 */
@Service
public class ClearCartUseCase {

    private final CartService cartService;

    public ClearCartUseCase(CartService cartService) {
        this.cartService = cartService;
    }

    @Transactional
    public void execute(String publicId) {
        cartService.clearCartByPublicId(publicId);
    }
}
