package org.example.echo.mail;

public interface EmailDeliveryGateway {
    void send(EmailMessage message);
}
