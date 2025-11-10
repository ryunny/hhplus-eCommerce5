package com.hhplus.ecommerce.application.usecase.cart;

import com.hhplus.ecommerce.application.command.AddToCartCommand;
import com.hhplus.ecommerce.domain.entity.CartItem;
import com.hhplus.ecommerce.domain.service.CartService;
import com.hhplus.ecommerce.domain.vo.Quantity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장바구니에 상품 추가 UseCase
 *
 * User Story: "사용자가 상품을 장바구니에 담는다"
 */
@Service
public class AddToCartUseCase {

    private final CartService cartService;

    public AddToCartUseCase(CartService cartService) {
        this.cartService = cartService;
    }

    @Transactional
    public CartItem execute(AddToCartCommand command) {
        Quantity quantity = new Quantity(command.quantity());
        return cartService.addToCartByPublicId(command.publicId(), command.productId(), quantity);
    }
}
