package com.hhplus.ecommerce.config.properties;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "cache")
public class CacheProperties {

    private final Duration defaultTtl;
    private final Duration productsTtl;
    private final Duration couponsTtl;
    private final Duration issuableCouponsTtl;
    private final Duration usersTtl;
    private final Duration topProductsTtl;

    public CacheProperties() {
        this.defaultTtl = Duration.ofMinutes(10);
        this.productsTtl = Duration.ofMinutes(30);
        this.couponsTtl = Duration.ofMinutes(10);
        this.issuableCouponsTtl = Duration.ofMinutes(5);
        this.usersTtl = Duration.ofMinutes(5);
        this.topProductsTtl = Duration.ofMinutes(60);
    }
}
