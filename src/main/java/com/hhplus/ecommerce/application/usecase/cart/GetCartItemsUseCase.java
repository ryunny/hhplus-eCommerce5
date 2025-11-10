package com.hhplus.ecommerce.application.usecase.cart;

import com.hhplus.ecommerce.application.query.GetCartItemsQuery;
import com.hhplus.ecommerce.domain.entity.CartItem;
import com.hhplus.ecommerce.domain.service.CartService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 장바구니 조회 UseCase
 *
 * User Story: "사용자가 장바구니를 조회한다"
 */
@Service
public class GetCartItemsUseCase {

    private final CartService cartService;

    public GetCartItemsUseCase(CartService cartService) {
        this.cartService = cartService;
    }

    public List<CartItem> execute(GetCartItemsQuery query) {
        return cartService.getCartItemsByPublicId(query.publicId());
    }
}
