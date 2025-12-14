package com.hhplus.ecommerce.domain.service;

import com.hhplus.ecommerce.config.properties.SchedulerProperties;
import com.hhplus.ecommerce.domain.entity.OrderItem;
import com.hhplus.ecommerce.domain.entity.Product;
import com.hhplus.ecommerce.domain.repository.ProductRepository;
import com.hhplus.ecommerce.infrastructure.redis.RedisKeyGenerator;
import com.hhplus.ecommerce.presentation.dto.PopularProductResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 상품 랭킹 관리 서비스 (Redis Sorted Set 기반 - 날짜별 키 분리)
 *
 * 실시간 인기 상품 순위를 관리합니다.
 * - 주문 시: 날짜별 Redis Sorted Set에 판매량 기록 (TTL 10일)
 * - 조회 시: 최근 N일 키를 UNION하여 Top 조회
 * - 장점: 실시간 랭킹, 빠른 조회, 자동 정리 (크론 불필요), DB 부하 없음
 */
@Slf4j
@Service
public class ProductRankingService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ProductRepository productRepository;
    private final SchedulerProperties schedulerProperties;

    public ProductRankingService(RedisTemplate<String, String> redisTemplate,
                                 ProductRepository productRepository,
                                 SchedulerProperties schedulerProperties) {
        this.redisTemplate = redisTemplate;
        this.productRepository = productRepository;
        this.schedulerProperties = schedulerProperties;
    }

    /**
     * 주문 시 판매량을 오늘 날짜 Redis Sorted Set에 기록
     *
     * @param orderItems 주문 아이템 목록
     */
    public void recordSales(List<OrderItem> orderItems) {
        LocalDate today = LocalDate.now();
        String key = RedisKeyGenerator.productRankingByDate(today);

        for (OrderItem item : orderItems) {
            String member = "product:" + item.getProduct().getId();
            double score = item.getQuantity().getValue();

            redisTemplate.opsForZSet().incrementScore(key, member, score);

            log.debug("판매량 기록: date={}, productId={}, quantity={}", today, item.getProduct().getId(), score);
        }

        // TTL 설정 (여유있게 10일)
        int ttlDays = schedulerProperties.getRanking().getKeyTtlDays();
        redisTemplate.expire(key, Duration.ofDays(ttlDays));

        log.debug("랭킹 키 TTL 설정: key={}, ttl={}일", key, ttlDays);
    }

    /**
     * 인기 상품 Top N 조회 (최근 N일 데이터 UNION)
     *
     * @param days 기간 (1 또는 7)
     * @param limit 조회할 상품 개수
     * @return 인기 상품 목록
     */
    public List<PopularProductResponse> getTopProducts(int days, int limit) {
        if (days == 1) {
            return getTopProductsSingleDay(limit);
        } else {
            return getTopProductsMultipleDays(days, limit);
        }
    }

    /**
     * 1일 기준 인기 상품 조회 (오늘 데이터만)
     */
    private List<PopularProductResponse> getTopProductsSingleDay(int limit) {
        LocalDate today = LocalDate.now();
        String key = RedisKeyGenerator.productRankingByDate(today);

        Set<ZSetOperations.TypedTuple<String>> topProducts =
                redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, limit - 1);

        if (topProducts == null || topProducts.isEmpty()) {
            log.warn("1일 기준 인기 상품이 없습니다. (오늘 판매 데이터 없음)");
            return Collections.emptyList();
        }

        return convertToResponse(topProducts);
    }

    /**
     * N일 기준 인기 상품 조회 (최근 N일 키 UNION)
     */
    private List<PopularProductResponse> getTopProductsMultipleDays(int days, int limit) {
        LocalDate today = LocalDate.now();
        List<String> keys = new ArrayList<>();

        // 최근 N일 키 생성
        for (int i = 0; i < days; i++) {
            LocalDate date = today.minusDays(i);
            keys.add(RedisKeyGenerator.productRankingByDate(date));
        }

        // 임시 키에 UNION 결과 저장
        String tempKey = RedisKeyGenerator.productRankingTempKey();

        try {
            redisTemplate.opsForZSet().unionAndStore(
                    keys.get(0),
                    keys.subList(1, keys.size()),
                    tempKey
            );

            // 임시 키에 TTL 설정 (60초 후 자동 삭제)
            long tempTtl = schedulerProperties.getRanking().getTempKeyTtlSeconds();
            redisTemplate.expire(tempKey, Duration.ofSeconds(tempTtl));

            // Top N 조회
            Set<ZSetOperations.TypedTuple<String>> topProducts =
                    redisTemplate.opsForZSet().reverseRangeWithScores(tempKey, 0, limit - 1);

            if (topProducts == null || topProducts.isEmpty()) {
                log.warn("{}일 기준 인기 상품이 없습니다. (최근 {}일 판매 데이터 없음)", days, days);
                return Collections.emptyList();
            }

            return convertToResponse(topProducts);

        } finally {
            // 임시 키 즉시 삭제 (TTL 대기하지 않고)
            redisTemplate.delete(tempKey);
        }
    }

    /**
     * Redis 결과를 PopularProductResponse로 변환
     */
    private List<PopularProductResponse> convertToResponse(Set<ZSetOperations.TypedTuple<String>> topProducts) {
        // 상품 ID 추출
        List<Long> productIds = topProducts.stream()
                .map(tuple -> extractProductId(tuple.getValue()))
                .toList();

        // DB에서 상품 상세 정보 조회 (JOIN FETCH로 Category도 함께 조회)
        List<Product> products = productRepository.findAllByIdWithCategory(productIds);
        Map<Long, Product> productMap = products.stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        // 조합해서 반환
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
                .filter(Objects::nonNull)
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
     * 특정 날짜의 랭킹 초기화
     *
     * @param date 날짜
     * @deprecated TTL 자동 만료 방식으로 변경되어 수동 초기화 불필요. 테스트 용도로만 사용.
     */
    @Deprecated
    public void clearRankingByDate(LocalDate date) {
        String key = RedisKeyGenerator.productRankingByDate(date);
        redisTemplate.delete(key);
        log.info("{}일자 랭킹 초기화 완료", date);
    }

    /**
     * 최근 N일 랭킹 모두 초기화
     *
     * @param days 기간
     * @deprecated TTL 자동 만료 방식으로 변경되어 수동 초기화 불필요. 테스트 용도로만 사용.
     */
    @Deprecated
    public void clearRankingsForDays(int days) {
        LocalDate today = LocalDate.now();
        for (int i = 0; i < days; i++) {
            LocalDate date = today.minusDays(i);
            clearRankingByDate(date);
        }
        log.info("최근 {}일 랭킹 초기화 완료", days);
    }
}
