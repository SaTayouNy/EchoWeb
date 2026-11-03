package org.example.echo.mail;

import org.example.echo.exception.ApiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(name = "app.email.provider", havingValue = "in-memory")
public class InMemoryEmailDeliveryGateway implements EmailDeliveryGateway {
    private final CopyOnWriteArrayList<EmailMessage> messages = new CopyOnWriteArrayList<>();
    private final AtomicBoolean failNextDelivery = new AtomicBoolean(false);

    @Override
    public void send(EmailMessage message) {
        if (failNextDelivery.getAndSet(false)) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "EMAIL_DELIVERY_FAILED", "Unable to send email");
        }
        messages.add(message);
    }

    public List<EmailMessage> messages() {
        return List.copyOf(messages);
    }

    public void clear() {
        messages.clear();
        failNextDelivery.set(false);
    }

    public void failNextDelivery() {
        failNextDelivery.set(true);
    }
}
