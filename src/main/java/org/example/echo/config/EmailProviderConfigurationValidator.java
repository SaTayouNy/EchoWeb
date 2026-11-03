package org.example.echo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!dev & !test")
public class EmailProviderConfigurationValidator implements SmartLifecycle {
    private final String provider;
    private boolean running;

    public EmailProviderConfigurationValidator(@Value("${app.email.provider}") String provider) {
        this.provider = provider;
    }

    @Override
    public void start() {
        if ("in-memory".equalsIgnoreCase(provider)) {
            throw new IllegalStateException("app.email.provider must be explicitly configured outside dev/test profiles");
        }
        running = true;
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
