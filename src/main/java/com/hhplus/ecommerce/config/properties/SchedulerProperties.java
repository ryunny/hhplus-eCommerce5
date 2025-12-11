package com.hhplus.ecommerce.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "scheduler")
public class SchedulerProperties {

    private Queue queue = new Queue();
    private Outbox outbox = new Outbox();
    private Ranking ranking = new Ranking();

    @Getter
    @Setter
    public static class Queue {
        private long fixedDelay = 1000L;  // 1초
        private int batchSize = 10;
    }

    @Getter
    @Setter
    public static class Outbox {
        private long fixedDelay = 5000L;      // 5초
        private long initialDelay = 5000L;     // 5초
    }

    @Getter
    @Setter
    public static class Ranking {
        private long fixedDelay = 3600000L;    // 1시간
        private int topDays = 7;                // 최근 7일
        private int topCount = 5;               // 상위 5개
        private int calculationDays = 3;        // 최근 3일
        private int defaultQueryDays = 1;       // API 조회 기본값: 1일
        private int keyTtlDays = 10;            // 랭킹 키 TTL: 10일 (7일보다 여유)
        private long tempKeyTtlSeconds = 60;    // 임시 키 TTL: 60초
    }
}
