package com.shopflow.gateway.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds app.rate-limit.* from application.yml. The checks run while Spring binds the
 * values, so a nonsensical configuration stops the gateway at startup with a clear message
 * instead of silently blocking (or allowing) all traffic.
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(Limit auth, Limit api) {

    public RateLimitProperties {
        if (auth == null || api == null) {
            throw new IllegalArgumentException("app.rate-limit.auth and app.rate-limit.api must both be configured");
        }
    }

    /**
     * @param replenishRate   tokens added to the bucket per second
     * @param burstCapacity   maximum tokens the bucket can hold (the allowed burst)
     * @param requestedTokens tokens one request costs
     */
    public record Limit(int replenishRate, int burstCapacity, int requestedTokens) {

        public Limit {
            if (replenishRate < 1) {
                throw new IllegalArgumentException("replenish-rate must be at least 1");
            }
            if (requestedTokens < 1) {
                throw new IllegalArgumentException("requested-tokens must be at least 1");
            }
            if (burstCapacity < requestedTokens) {
                throw new IllegalArgumentException(
                        "burst-capacity must be >= requested-tokens, otherwise no request could ever pass");
            }
        }
    }
}
