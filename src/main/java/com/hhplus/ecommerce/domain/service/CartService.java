package com.hhplus.ecommerce.domain.service;

import com.hhplus.ecommerce.domain.entity.CartItem;
import com.hhplus.ecommerce.domain.entity.Product;
import com.hhplus.ecommerce.domain.entity.User;
import com.hhplus.ecommerce.domain.repository.CartItemRepository;
import com.hhplus.ecommerce.domain.vo.Quantity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 장바구니 관련 비즈니스 로직을 처리하는 서비스
 */
@Service
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final UserService userService;
    private final ProductService productService;

    public CartService(CartItemRepository cartItemRepository,
                      UserService userService,
                      ProductService productService) {
        this.cartItemRepository = cartItemRepository;
        this.userService = userService;
        this.productService = productService;
    }

    /**
     * 장바구니에 상품 추가
     *
     * @param userId 사용자 ID
     * @param productId 상품 ID
     * @param quantity 수량
     * @return 장바구니 아이템
     */
    @Transactional
    public CartItem addToCart(Long userId, Long productId, Quantity quantity) {
        User user = userService.getUser(userId);
        Product product = productService.getProduct(productId);

        // 재고 검증
        productService.validateStock(product, quantity);

        // 이미 장바구니에 있는 상품인지 확인
        Optional<CartItem> existingItem = cartItemRepository.findByUserIdAndProductId(userId, productId);
        if (existingItem.isPresent()) {
            CartItem item = existingItem.get();
            item.increaseQuantity(quantity);
            // 더티 체킹으로 자동 저장
            return item;
        }

        // 새로운 장바구니 아이템 생성
        CartItem cartItem = new CartItem(user, product, quantity);
        return cartItemRepository.save(cartItem);
    }

    /**
     * 장바구니에 상품 추가 (Public ID 기반)
     *
     * @param publicId 사용자 Public ID (UUID)
     * @param productId 상품 ID
     * @param quantity 수량
     * @return 장바구니 아이템
     */
    @Transactional
    public CartItem addToCartByPublicId(String publicId, Long productId, Quantity quantity) {
        User user = userService.getUserByPublicId(publicId);
        return addToCart(user.getId(), productId, quantity);
    }

    /**
     * 사용자의 장바구니 아이템 조회
     *
     * @param userId 사용자 ID
     * @return 장바구니 아이템 목록
     */
    public List<CartItem> getCartItems(Long userId) {
        return cartItemRepository.findByUserId(userId);
    }

    /**
     * 사용자의 장바구니 아이템 조회 (Public ID 기반)
     *
     * @param publicId 사용자 Public ID (UUID)
     * @return 장바구니 아이템 목록
     */
    public List<CartItem> getCartItemsByPublicId(String publicId) {
        User user = userService.getUserByPublicId(publicId);
        return getCartItems(user.getId());
    }

    /**
     * 장바구니에서 아이템 제거
     *
     * @param cartItemId 장바구니 아이템 ID
     */
    @Transactional
    public void removeFromCart(Long cartItemId) {
        cartItemRepository.deleteById(cartItemId);
    }

    /**
     * 장바구니 전체 비우기
     *
     * @param userId 사용자 ID
     */
    @Transactional
    public void clearCart(Long userId) {
        cartItemRepository.deleteByUserId(userId);
    }

    /**
     * 장바구니 전체 비우기 (Public ID 기반)
     *
     * @param publicId 사용자 Public ID (UUID)
     */
    @Transactional
    public void clearCartByPublicId(String publicId) {
        User user = userService.getUserByPublicId(publicId);
        clearCart(user.getId());
    }
}
