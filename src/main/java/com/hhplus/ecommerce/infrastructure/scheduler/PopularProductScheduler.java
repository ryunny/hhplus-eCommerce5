package com.hhplus.ecommerce.infrastructure.scheduler;

import com.hhplus.ecommerce.config.properties.SchedulerProperties;
import com.hhplus.ecommerce.domain.entity.PopularProduct;
import com.hhplus.ecommerce.domain.repository.PopularProductRepository;
import com.hhplus.ecommerce.domain.service.ProductRankingService;
import com.hhplus.ecommerce.infrastructure.redis.RedisKeyGenerator;
import com.hhplus.ecommerce.presentation.dto.PopularProductResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 인기 상품 갱신 스케줄러 (하이브리드 방식)
 *
 * 실시간 Redis 데이터를 주기적으로 DB에 백업합니다.
 * - 실행 주기: scheduler.ranking.fixed-delay
 * - 집계 기간/개수: scheduler.ranking.top-days, scheduler.ranking.top-count
 * - Redis 장애 시 Fallback으로 사용
 * - 오래된 Redis 데이터 정리 (TTL 설정)
 */
@Slf4j
@Component
public class PopularProductScheduler {

    private final ProductRankingService productRankingService;
    private final PopularProductRepository popularProductRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final SchedulerProperties schedulerProperties;

    public PopularProductScheduler(ProductRankingService productRankingService,
                                  PopularProductRepository popularProductRepository,
                                  RedisTemplate<String, String> redisTemplate,
                                  SchedulerProperties schedulerProperties) {
        this.productRankingService = productRankingService;
        this.popularProductRepository = popularProductRepository;
        this.redisTemplate = redisTemplate;
        this.schedulerProperties = schedulerProperties;
    }

    /**
     * Redis 데이터를 DB에 백업
     */
    @Scheduled(fixedDelayString = "${scheduler.ranking.fixed-delay}")
    @Transactional
    public void backupRedisRankingToDB() {
        log.info("인기 상품 DB 백업 시작 (Redis → DB)");

        try {
            int topDays = schedulerProperties.getRanking().getTopDays();
            int topCount = schedulerProperties.getRanking().getTopCount();

            List<PopularProductResponse> topProducts = productRankingService.getTopProducts(topDays, topCount);

            if (topProducts.isEmpty()) {
                log.warn("Redis에 7일 기준 인기 상품 데이터 없음 (백업 스킵)");
                return;
            }

            // 2. DB에 순위별로 저장
            for (int i = 0; i < topProducts.size(); i++) {
                int rank = i + 1;
                PopularProductResponse product = topProducts.get(i);

                Optional<PopularProduct> existingOpt = popularProductRepository.findByRank(rank);

                if (existingOpt.isPresent()) {
                    // 기존 데이터 업데이트
                    PopularProduct existing = existingOpt.get();
                    existing.update(
                            product.productId(),
                            product.productName(),
                            product.price(),
                            product.totalSalesQuantity(),
                            product.categoryName()
                    );
                    popularProductRepository.save(existing);
                    log.debug("DB 백업 (업데이트): 순위={}, 상품={}, 판매량={}",
                             rank, product.productName(), product.totalSalesQuantity());
                } else {
                    // 신규 생성
                    PopularProduct newPopular = new PopularProduct(
                            rank,
                            product.productId(),
                            product.productName(),
                            product.price(),
                            product.totalSalesQuantity(),
                            product.categoryName()
                    );
                    popularProductRepository.save(newPopular);
                    log.debug("DB 백업 (신규): 순위={}, 상품={}, 판매량={}",
                             rank, product.productName(), product.totalSalesQuantity());
                }
            }

            log.info("인기 상품 DB 백업 완료: {} 건", topProducts.size());

        } catch (Exception e) {
            log.error("인기 상품 DB 백업 실패", e);
        }
    }

    /**
     * 1일 랭킹 초기화 (매일 자정)
     * 1일 기준 데이터를 초기화하여 새로 집계 시작
     */
    @Scheduled(cron = "0 0 0 * * *") // 매일 00:00:00
    public void reset1DayRanking() {
        log.info("1일 기준 랭킹 초기화 시작");
        try {
            productRankingService.clearRanking(1);
            log.info("1일 기준 랭킹 초기화 완료");
        } catch (Exception e) {
            log.error("1일 기준 랭킹 초기화 실패", e);
        }
    }

    /**
     * 7일 랭킹 초기화 (매주 월요일 자정)
     * 7일 기준 데이터를 초기화하여 새로 집계 시작
     */
    @Scheduled(cron = "0 0 0 * * MON") // 매주 월요일 00:00:00
    public void reset7DaysRanking() {
        log.info("7일 기준 랭킹 초기화 시작");
        try {
            productRankingService.clearRanking(7);
            log.info("7일 기준 랭킹 초기화 완료");
        } catch (Exception e) {
            log.error("7일 기준 랭킹 초기화 실패", e);
        }
    }

    /**
     * Redis 키 TTL 설정 (초기화 대신 자동 만료)
     * 대안: 초기화 대신 TTL을 설정하여 자동으로 만료되도록 할 수 있습니다.
     */
    public void setRankingTTL() {
        // 1일 랭킹: 1일 후 만료
        redisTemplate.expire(RedisKeyGenerator.productRanking1Day(), Duration.ofDays(1));

        // 7일 랭킹: 7일 후 만료
        redisTemplate.expire(RedisKeyGenerator.productRanking7Days(), Duration.ofDays(7));

        log.info("Redis 랭킹 TTL 설정 완료");
    }
}
