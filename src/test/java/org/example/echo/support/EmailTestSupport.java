package org.example.echo.support;

import org.example.echo.mail.EmailMessage;
import org.example.echo.mail.InMemoryEmailDeliveryGateway;
import org.example.echo.entity.EmailOutboxStatus;
import org.example.echo.repository.EmailOutboxRepository;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public final class EmailTestSupport {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(10);

    private EmailTestSupport() {
    }

    public static EmailMessage awaitFirstMessage(InMemoryEmailDeliveryGateway gateway) {
        return awaitMessages(gateway, 1).getFirst();
    }

    public static EmailMessage awaitLastMessage(InMemoryEmailDeliveryGateway gateway) {
        return awaitMessages(gateway, 1).getLast();
    }

    public static EmailMessage awaitNextMessage(InMemoryEmailDeliveryGateway gateway, int previousMessageCount) {
        return awaitMessages(gateway, previousMessageCount + 1).getLast();
    }

    public static List<EmailMessage> awaitMessages(InMemoryEmailDeliveryGateway gateway, int minMessages) {
        long deadline = System.nanoTime() + DEFAULT_TIMEOUT.toNanos();
        List<EmailMessage> messages = gateway.messages();

        while (messages.size() < minMessages && System.nanoTime() < deadline) {
            sleepQuietly();
            messages = gateway.messages();
        }

        assertThat(messages).hasSizeGreaterThanOrEqualTo(minMessages);
        return messages;
    }

    public static void awaitOutboxStatusCount(
        EmailOutboxRepository repository,
        EmailOutboxStatus status,
        long expectedCount
    ) {
        long deadline = System.nanoTime() + DEFAULT_TIMEOUT.toNanos();
        long actualCount = repository.countByStatus(status);

        while (actualCount != expectedCount && System.nanoTime() < deadline) {
            sleepQuietly();
            actualCount = repository.countByStatus(status);
        }

        assertThat(actualCount).isEqualTo(expectedCount);
    }

    private static void sleepQuietly() {
        try {
            Thread.sleep(POLL_INTERVAL);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for email delivery", ex);
        }
    }
}
