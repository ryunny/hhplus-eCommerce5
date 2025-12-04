package com.hhplus.ecommerce.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 분산 락 타임아웃 설정
 *
 * application.yml의 lock.timeout 값을 읽어옴
 */
@Configuration
@ConfigurationProperties(prefix = "lock.timeout")
@Getter
@Setter
public class LockTimeoutConfig {

    /**
     * 상품 재고 관련 락 타임아웃 (밀리초)
     */
    private long product = 5000;

    /**
     * 쿠폰 발급 관련 락 타임아웃 (밀리초)
     */
    private long coupon = 5000;

    /**
     * 배치 작업 관련 락 타임아웃 (밀리초)
     */
    private long batch = 10000;
}
