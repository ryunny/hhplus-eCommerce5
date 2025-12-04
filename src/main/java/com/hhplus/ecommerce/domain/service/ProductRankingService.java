package com.hhplus.ecommerce.domain.service;

import com.hhplus.ecommerce.domain.entity.OrderItem;
import com.hhplus.ecommerce.domain.entity.Product;
import com.hhplus.ecommerce.domain.repository.ProductRepository;
import com.hhplus.ecommerce.infrastructure.redis.RedisKeyGenerator;
import com.hhplus.ecommerce.presentation.dto.PopularProductResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 상품 랭킹 관리 서비스 (Redis Sorted Set 기반)
 *
 * 실시간 인기 상품 순위를 관리합니다.
 * - 주문 시: Redis Sorted Set에 판매량 기록
 * - 조회 시: Redis에서 Top N 조회
 * - 장점: 실시간 랭킹, 빠른 조회, DB 부하 없음
 */
@Slf4j
@Service
public class ProductRankingService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ProductRepository productRepository;

    public ProductRankingService(RedisTemplate<String, String> redisTemplate,
                                 ProductRepository productRepository) {
        this.redisTemplate = redisTemplate;
        this.productRepository = productRepository;
    }

    /**
     * 주문 시 판매량을 Redis Sorted Set에 기록
     *
     * @param orderItems 주문 아이템 목록
     */
    public void recordSales(List<OrderItem> orderItems) {
        for (OrderItem item : orderItems) {
            String member = "product:" + item.getProduct().getId();
            double score = item.getQuantity().getValue();

            // 1일 기준 랭킹에 기록
            redisTemplate.opsForZSet().incrementScore(
                    RedisKeyGenerator.productRanking1Day(),
                    member,
                    score
            );

            // 7일 기준 랭킹에 기록
            redisTemplate.opsForZSet().incrementScore(
                    RedisKeyGenerator.productRanking7Days(),
                    member,
                    score
            );

            log.debug("판매량 기록: productId={}, quantity={}", item.getProduct().getId(), score);
        }
    }

    /**
     * 인기 상품 Top N 조회 (Redis Sorted Set)
     *
     * @param days 기간 (1 또는 7)
     * @param limit 조회할 상품 개수
     * @return 인기 상품 목록
     */
    public List<PopularProductResponse> getTopProducts(int days, int limit) {
        String key = RedisKeyGenerator.productRankingByDays(days);

        // 1. Redis Sorted Set에서 Top N 조회 (점수 높은 순)
        Set<ZSetOperations.TypedTuple<String>> topProducts =
                redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, limit - 1);

        if (topProducts == null || topProducts.isEmpty()) {
            log.warn("{}일 기준 인기 상품이 없습니다. (Redis에 데이터 없음)", days);
            return Collections.emptyList();
        }

        // 2. 상품 ID 추출
        List<Long> productIds = topProducts.stream()
                .map(tuple -> extractProductId(tuple.getValue()))
                .toList();

        // 3. DB에서 상품 상세 정보 조회 (JOIN FETCH로 Category도 함께 조회하여 N+1 문제 해결)
        List<Product> products = productRepository.findAllByIdWithCategory(productIds);
        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        // 4. 조합해서 반환
        return topProducts.stream()
                .map(tuple -> {
                    Long productId = extractProductId(tuple.getValue());
                    Product product = productMap.get(productId);

                    if (product == null) {
                        log.warn("상품을 찾을 수 없습니다: productId={}", productId);
                        return null;
                    }

                    Integer salesCount = tuple.getScore().intValue();

                    return new PopularProductResponse(
                            product.getId(),
                            product.getName(),
                            product.getPrice().getAmount(),
                            salesCount,
                            product.getCategory().getName()
                    );
                })
                .filter(response -> response != null) // null 제거
                .toList();
    }

    /**
     * Redis member에서 상품 ID 추출
     * "product:123" → 123
     */
    private Long extractProductId(String member) {
        return Long.parseLong(member.replace("product:", ""));
    }

    /**
     * 특정 기간의 랭킹 초기화
     *
     * @param days 기간 (1 또는 7)
     */
    public void clearRanking(int days) {
        String key = RedisKeyGenerator.productRankingByDays(days);
        redisTemplate.delete(key);
        log.info("{}일 기준 랭킹 초기화 완료", days);
    }

    /**
     * 모든 랭킹 초기화
     */
    public void clearAllRankings() {
        clearRanking(1);
        clearRanking(7);
        log.info("모든 랭킹 초기화 완료");
    }
}
