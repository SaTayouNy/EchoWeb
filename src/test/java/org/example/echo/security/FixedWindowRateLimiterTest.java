package org.example.echo.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FixedWindowRateLimiterTest {
    @Test
    void rejectsUnsupportedBackend() {
        assertThatThrownBy(() -> new FixedWindowRateLimiter("redis"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Unsupported app.security.rate-limit.backend: redis");
    }
}
