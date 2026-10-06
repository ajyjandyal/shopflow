package com.shopflow.gateway.ratelimit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitPropertiesTest {

    @Test
    void sensibleLimitsAreAccepted() {
        assertThatCode(() -> new RateLimitProperties.Limit(1, 60, 6)).doesNotThrowAnyException();
    }

    @Test
    void burstSmallerThanRequestCostIsRejected() {
        // A bucket that can hold 5 tokens can never pay for a request costing 6.
        assertThatThrownBy(() -> new RateLimitProperties.Limit(1, 5, 6))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("burst-capacity");
    }

    @Test
    void zeroRatesAreRejected() {
        assertThatThrownBy(() -> new RateLimitProperties.Limit(0, 10, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimitProperties.Limit(1, 10, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void bothLimitsMustBeConfigured() {
        RateLimitProperties.Limit limit = new RateLimitProperties.Limit(10, 20, 1);
        assertThatThrownBy(() -> new RateLimitProperties(null, limit)).isInstanceOf(IllegalArgumentException.class);
    }
}
