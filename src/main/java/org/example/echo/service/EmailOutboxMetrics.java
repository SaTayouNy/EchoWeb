package org.example.echo.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.example.echo.entity.EmailOutboxStatus;
import org.example.echo.repository.EmailOutboxRepository;
import org.springframework.stereotype.Component;

@Component
public class EmailOutboxMetrics {
    public EmailOutboxMetrics(MeterRegistry meterRegistry, EmailOutboxRepository emailOutboxRepository) {
        Gauge.builder("email.outbox.pending", emailOutboxRepository, repository -> repository.countByStatus(EmailOutboxStatus.PENDING))
            .description("Emails waiting to be claimed for delivery")
            .register(meterRegistry);
        Gauge.builder("email.outbox.processing", emailOutboxRepository, repository -> repository.countByStatus(EmailOutboxStatus.PROCESSING))
            .description("Emails currently claimed by a dispatcher worker")
            .register(meterRegistry);
        Gauge.builder("email.outbox.failed", emailOutboxRepository, repository -> repository.countByStatus(EmailOutboxStatus.FAILED))
            .description("Emails that failed delivery and are waiting for retry")
            .register(meterRegistry);
        Gauge.builder("email.outbox.exhausted", emailOutboxRepository, repository -> repository.countByStatus(EmailOutboxStatus.EXHAUSTED))
            .description("Emails that permanently failed after exhausting retry attempts")
            .register(meterRegistry);
    }
}
