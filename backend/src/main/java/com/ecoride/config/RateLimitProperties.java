package com.ecoride.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "ecoride.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    private int requestsPerMinute = 120;
    private int authRequestsPerMinute = 20;
}
