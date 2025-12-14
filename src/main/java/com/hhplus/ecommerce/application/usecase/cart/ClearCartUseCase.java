package com.hhplus.ecommerce.application.usecase.cart;

import com.hhplus.ecommerce.domain.service.CartService;
import org.springframework.stereotype.Service;

/**
 * 장바구니 전체 비우기 UseCase
 *
 * User Story: "사용자가 장바구니를 전체 비운다"
 *
 * UseCase는 여러 Service를 조합하는 계층이므로 트랜잭션은 Service에서 관리합니다.
 */
@Service
public class ClearCartUseCase {

    private final CartService cartService;

    public ClearCartUseCase(CartService cartService) {
        this.cartService = cartService;
    }

    public void execute(String publicId) {
        cartService.clearCartByPublicId(publicId);
    }
}
