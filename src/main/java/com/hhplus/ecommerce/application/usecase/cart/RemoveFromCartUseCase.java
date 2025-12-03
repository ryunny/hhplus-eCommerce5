package com.hhplus.ecommerce.application.usecase.cart;

import com.hhplus.ecommerce.domain.service.CartService;
import org.springframework.stereotype.Service;

/**
 * 장바구니에서 상품 제거 UseCase
 *
 * User Story: "사용자가 장바구니에서 상품을 제거한다"
 *
 * UseCase는 여러 Service를 조합하는 계층이므로 트랜잭션은 Service에서 관리합니다.
 */
@Service
public class RemoveFromCartUseCase {

    private final CartService cartService;

    public RemoveFromCartUseCase(CartService cartService) {
        this.cartService = cartService;
    }

    public void execute(Long cartItemId) {
        cartService.removeFromCart(cartItemId);
    }
}
