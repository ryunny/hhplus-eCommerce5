package com.hhplus.ecommerce.config.properties;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "spring.data.redis")
public class RedisProperties {

    private final String host;
    private final int port;
}