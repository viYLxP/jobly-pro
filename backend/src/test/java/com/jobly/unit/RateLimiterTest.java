package com.jobly.unit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.jobly.security.RateLimiter;

class RateLimiterTest {

    @Test
    @DisplayName("allows N hits per window, blocks the next, and opens again when the window ends")
    void windowBehaviour() {
        AtomicLong now = new AtomicLong(0);
        RateLimiter limiter = new RateLimiter(2, Duration.ofSeconds(60), now::get);

        assertThat(limiter.hit("ip")).isZero();
        assertThat(limiter.hit("ip")).isZero();
        assertThat(limiter.hit("ip")).isEqualTo(60);
        // Another visitor is unaffected. / زائر ثاني ما يتأثر.
        assertThat(limiter.hit("other")).isZero();

        now.set(30_000);
        assertThat(limiter.hit("ip")).isEqualTo(30);

        now.set(60_000);
        assertThat(limiter.hit("ip")).isZero();
    }
}
