package org.example.echo.mail;

import jakarta.mail.MessagingException;
import org.example.echo.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(name = "app.email.provider", havingValue = "smtp")
public class SmtpEmailDeliveryGateway implements EmailDeliveryGateway {
    private static final Logger log = LoggerFactory.getLogger(SmtpEmailDeliveryGateway.class);
    private static final String ENCODING = StandardCharsets.UTF_8.name();

    private final JavaMailSender javaMailSender;

    public SmtpEmailDeliveryGateway(JavaMailSender javaMailSender) {
        this.javaMailSender = javaMailSender;
    }

    @Override
    public void send(EmailMessage message) {
        try {
            var mailMessage = javaMailSender.createMimeMessage();
            var helper = new MimeMessageHelper(mailMessage, false, ENCODING);
            helper.setFrom(message.from());
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(message.text(), false);

            javaMailSender.send(mailMessage);
        } catch (MessagingException | MailException ex) {
            log.warn("SMTP email delivery failed for {}", message.to(), ex);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "EMAIL_DELIVERY_FAILED", "Unable to send email");
        }
    }
}
