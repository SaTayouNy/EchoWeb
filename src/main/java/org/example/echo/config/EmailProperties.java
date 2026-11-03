package org.example.echo.config;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.IllegalFormatException;

@Component
@Validated
@ConfigurationProperties(prefix = "app.email")
public class EmailProperties {
    @NotBlank
    @Email
    private String from = "no-reply@echo.local";

    @NotBlank
    private String verificationUrlTemplate = "http://localhost:3000/verify-email?token=%s";

    @NotBlank
    private String passwordRecoveryUrlTemplate = "http://localhost:3000/reset-password?token=%s";

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getVerificationUrlTemplate() {
        return verificationUrlTemplate;
    }

    public void setVerificationUrlTemplate(String verificationUrlTemplate) {
        this.verificationUrlTemplate = verificationUrlTemplate;
    }

    public String getPasswordRecoveryUrlTemplate() {
        return passwordRecoveryUrlTemplate;
    }

    public void setPasswordRecoveryUrlTemplate(String passwordRecoveryUrlTemplate) {
        this.passwordRecoveryUrlTemplate = passwordRecoveryUrlTemplate;
    }

    @PostConstruct
    void validateTemplates() {
        requireValidUrl("app.email.verification-url-template", verificationUrl("validation-token"));
        requireValidUrl("app.email.password-recovery-url-template", passwordRecoveryUrl("validation-token"));
    }

    public String verificationUrl(String token) {
        try {
            return verificationUrlTemplate.formatted(token);
        } catch (IllegalFormatException ex) {
            throw new IllegalStateException("Invalid app.email.verification-url-template", ex);
        }
    }

    public String passwordRecoveryUrl(String token) {
        try {
            return passwordRecoveryUrlTemplate.formatted(token);
        } catch (IllegalFormatException ex) {
            throw new IllegalStateException("Invalid app.email.password-recovery-url-template", ex);
        }
    }

    private void requireValidUrl(String propertyName, String value) {
        URI uri = URI.create(value);
        if (!uri.isAbsolute() || uri.getScheme() == null || uri.getHost() == null) {
            throw new IllegalStateException("Invalid " + propertyName);
        }
        String scheme = uri.getScheme().toLowerCase();
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalStateException("Invalid " + propertyName);
        }
    }
}
