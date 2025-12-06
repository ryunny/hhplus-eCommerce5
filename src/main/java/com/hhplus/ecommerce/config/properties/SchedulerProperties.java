package com.hhplus.ecommerce.config.properties;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "scheduler.queue")
public class SchedulerProperties {

    private final long fixedDelay;
    private final int batchSize;

    public SchedulerProperties() {
        this.fixedDelay = 10000L; // 10초
        this.batchSize = 10;
    }
}
