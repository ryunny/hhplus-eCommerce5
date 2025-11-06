package com.hhplus.ecommerce.application.usecase;

import com.hhplus.ecommerce.domain.entity.OrderItem;
import com.hhplus.ecommerce.domain.entity.Product;
import com.hhplus.ecommerce.domain.repository.OrderItemRepository;
import com.hhplus.ecommerce.domain.repository.ProductRepository;
import com.hhplus.ecommerce.presentation.dto.PopularProductResponse;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProductUseCase {

    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;

    public ProductUseCase(ProductRepository productRepository, OrderItemRepository orderItemRepository) {
        this.productRepository = productRepository;
        this.orderItemRepository = orderItemRepository;
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public Product getProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + productId));
    }

    public List<Product> getProductsByCategory(Long categoryId) {
        return productRepository.findByCategoryId(categoryId);
    }

    /**
     * 인기 상품 통계 조회 (최근 3일, Top 5)
     *
     * 최근 3일간 주문된 상품 중 판매량 기준 상위 5개 상품의 통계를 반환합니다.
     *
     * @return 인기 상품 목록 (상품 정보 + 총 판매 수량)
     */
    public List<PopularProductResponse> getPopularProducts() {
        // 1. 최근 3일 Top 5 상품의 OrderItem 조회
        List<OrderItem> topOrderItems = orderItemRepository.getOrderItemByTopFive();

        // 2. 상품별 총 판매 수량 집계
        Map<Product, Integer> productSalesMap = topOrderItems.stream()
                .collect(Collectors.groupingBy(
                        OrderItem::getProduct,
                        LinkedHashMap::new,
                        Collectors.summingInt(item -> item.getQuantity().getValue())
                ));

        // 3. 판매량 기준 내림차순 정렬 후 Top 5 추출
        return productSalesMap.entrySet().stream()
                .sorted(Map.Entry.<Product, Integer>comparingByValue().reversed())
                .limit(5)
                .map(entry -> PopularProductResponse.of(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }
}
