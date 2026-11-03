package org.example.echo.mail;

import org.example.echo.config.EmailProperties;
import org.example.echo.entity.UserEntity;
import org.example.echo.service.EmailOutboxService;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
public class TemplateNotificationEmailService implements NotificationEmailService {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final EmailOutboxService emailOutboxService;
    private final EmailTemplateRenderer emailTemplateRenderer;
    private final EmailProperties emailProperties;

    public TemplateNotificationEmailService(
        EmailOutboxService emailOutboxService,
        EmailTemplateRenderer emailTemplateRenderer,
        EmailProperties emailProperties
    ) {
        this.emailOutboxService = emailOutboxService;
        this.emailTemplateRenderer = emailTemplateRenderer;
        this.emailProperties = emailProperties;
    }

    @Override
    public void sendEmailVerification(UserEntity user, String verificationToken, OffsetDateTime expiresAt) {
        EmailTemplate template = emailTemplateRenderer.render(
            "mail/email-verification-subject.txt",
            "mail/email-verification-body.txt",
            Map.of(
                "username", user.getUsername(),
                "verificationUrl", emailProperties.verificationUrl(verificationToken),
                "expiresAt", DATE_TIME_FORMATTER.format(expiresAt)
            )
        );
        emailOutboxService.enqueue(new EmailMessage(emailProperties.getFrom(), user.getEmail(), template.subject(), template.body()));
    }

    @Override
    public void sendPasswordRecovery(UserEntity user, String recoveryToken, OffsetDateTime expiresAt) {
        EmailTemplate template = emailTemplateRenderer.render(
            "mail/password-recovery-subject.txt",
            "mail/password-recovery-body.txt",
            Map.of(
                "username", user.getUsername(),
                "recoveryUrl", emailProperties.passwordRecoveryUrl(recoveryToken),
                "expiresAt", DATE_TIME_FORMATTER.format(expiresAt)
            )
        );
        emailOutboxService.enqueue(new EmailMessage(emailProperties.getFrom(), user.getEmail(), template.subject(), template.body()));
    }

    @Override
    public void sendDuplicateRegistrationNotice(UserEntity user) {
        EmailTemplate template = emailTemplateRenderer.render(
            "mail/duplicate-registration-subject.txt",
            "mail/duplicate-registration-body.txt",
            Map.of("username", user.getUsername())
        );
        emailOutboxService.enqueue(new EmailMessage(emailProperties.getFrom(), user.getEmail(), template.subject(), template.body()));
    }
}
