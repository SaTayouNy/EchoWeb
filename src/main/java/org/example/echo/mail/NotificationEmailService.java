package org.example.echo.mail;

import org.example.echo.entity.UserEntity;

import java.time.OffsetDateTime;

public interface NotificationEmailService {
    void sendEmailVerification(UserEntity user, String verificationToken, OffsetDateTime expiresAt);

    void sendPasswordRecovery(UserEntity user, String recoveryToken, OffsetDateTime expiresAt);

    void sendDuplicateRegistrationNotice(UserEntity user);
}
