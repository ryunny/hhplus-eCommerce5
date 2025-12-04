package com.hhplus.ecommerce.presentation.controller;

import com.hhplus.ecommerce.application.query.GetProductQuery;
import com.hhplus.ecommerce.application.usecase.product.GetAllProductsUseCase;
import com.hhplus.ecommerce.application.usecase.product.GetPopularProductsUseCase;
import com.hhplus.ecommerce.application.usecase.product.GetProductUseCase;
import com.hhplus.ecommerce.domain.entity.Product;
import com.hhplus.ecommerce.presentation.dto.PopularProductResponse;
import com.hhplus.ecommerce.presentation.dto.ProductResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final GetAllProductsUseCase getAllProductsUseCase;
    private final GetProductUseCase getProductUseCase;
    private final GetPopularProductsUseCase getPopularProductsUseCase;

    @GetMapping
    public ResponseEntity<List<ProductResponse>> getAllProducts() {
        List<Product> products = getAllProductsUseCase.execute();
        List<ProductResponse> response = products.stream()
                .map(ProductResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable Long productId) {
        GetProductQuery query = new GetProductQuery(productId);
        Product product = getProductUseCase.execute(query);
        return ResponseEntity.ok(ProductResponse.from(product));
    }

    /**
     * 인기 상품 조회 API
     *
     * @param days 기간 (1 또는 7일, 기본값: 1일)
     * @return 인기 상품 목록 (상위 5개)
     *
     * 예시:
     * - GET /api/products/popular          → 1일 기준
     * - GET /api/products/popular?days=1   → 1일 기준
     * - GET /api/products/popular?days=7   → 7일 기준
     */
    @GetMapping("/popular")
    public ResponseEntity<List<PopularProductResponse>> getPopularProducts(
            @RequestParam(required = false, defaultValue = "1") Integer days) {
        List<PopularProductResponse> popularProducts = getPopularProductsUseCase.execute(days);
        return ResponseEntity.ok(popularProducts);
    }
}
