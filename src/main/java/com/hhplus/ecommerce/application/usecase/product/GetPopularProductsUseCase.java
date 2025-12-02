package com.hhplus.ecommerce.application.usecase.product;

import com.hhplus.ecommerce.domain.entity.PopularProduct;
import com.hhplus.ecommerce.domain.repository.PopularProductRepository;
import com.hhplus.ecommerce.domain.service.ProductRankingService;
import com.hhplus.ecommerce.presentation.dto.PopularProductResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 인기 상품 조회 UseCase (하이브리드 방식)
 *
 * User Story: "사용자가 인기 상품 목록을 조회한다"
 *
 * Redis Sorted Set을 우선 사용하고, 실패 시 DB에서 조회 (Fallback)
 * - 실시간 랭킹: Redis Sorted Set (주문 시마다 갱신)
 * - 백업: popular_products 테이블 (스케줄러가 1시간마다 갱신)
 * - 기간: 1일 또는 7일 선택 가능
 */
@Slf4j
@Service
public class GetPopularProductsUseCase {

    private final ProductRankingService productRankingService;
    private final PopularProductRepository popularProductRepository;

    public GetPopularProductsUseCase(ProductRankingService productRankingService,
                                     PopularProductRepository popularProductRepository) {
        this.productRankingService = productRankingService;
        this.popularProductRepository = popularProductRepository;
    }

    /**
     * 인기 상품 조회 (실시간 Redis 우선, 실패 시 DB Fallback)
     *
     * @param days 기간 (1 또는 7일)
     * @return 인기 상품 목록 (상위 5개)
     */
    public List<PopularProductResponse> execute(Integer days) {
        // 기본값: 1일
        int period = (days != null && days == 7) ? 7 : 1;

        try {
            // 1. Redis Sorted Set에서 조회 (실시간)
            List<PopularProductResponse> products = productRankingService.getTopProducts(period, 5);

            if (!products.isEmpty()) {
                log.debug("{}일 기준 인기 상품 조회 성공 (Redis): {} 건", period, products.size());
                return products;
            }

            // Redis에 데이터 없으면 DB Fallback
            log.warn("Redis에 {}일 기준 인기 상품 데이터 없음, DB로 Fallback", period);
            return getFallbackFromDB();

        } catch (Exception e) {
            // Redis 장애 시 DB Fallback
            log.error("Redis 조회 실패, DB로 Fallback: {}", e.getMessage());
            return getFallbackFromDB();
        }
    }

    /**
     * DB에서 인기 상품 조회 (Fallback)
     */
    @Transactional(readOnly = true)
    private List<PopularProductResponse> getFallbackFromDB() {
        List<PopularProduct> popularProducts = popularProductRepository.findAllOrderByRank();

        if (popularProducts.isEmpty()) {
            log.warn("DB에도 인기 상품 데이터 없음");
        }

        return popularProducts.stream()
                .map(popular -> new PopularProductResponse(
                        popular.getProductId(),
                        popular.getProductName(),
                        popular.getPrice(),
                        popular.getTotalSalesQuantity(),
                        popular.getCategoryName()
                ))
                .toList();
    }
}
