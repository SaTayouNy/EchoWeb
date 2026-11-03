package org.example.echo.service;

import org.example.echo.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class SessionTokenService {
    private static final int MIN_SECRET_BYTES = 32;
    private static final int MIN_SECRET_DISTINCT_CHARS = 4;
    private static final String TOKEN_VERSION = "v1";
    private static final String AUTHORIZATION_SCHEME = "Bearer";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String TOKEN_HASH_ALGORITHM = "SHA-256";
    private static final int TOKEN_PART_COUNT = 5;
    private static final int TOKEN_SPLIT_LIMIT = TOKEN_PART_COUNT + 1;
    private static final int NONCE_LENGTH = 16;

    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKeySpec secretKey;

    public SessionTokenService(@Value("${app.auth.token.secret}") String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("app.auth.token.secret must not be blank");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException("app.auth.token.secret must be at least 32 bytes");
        }
        if (hasLowDiversity(secret)) {
            throw new IllegalArgumentException("app.auth.token.secret must have sufficient character diversity");
        }
        this.secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
    }

    public String authorizationScheme() {
        return AUTHORIZATION_SCHEME;
    }

    public String createAccessToken(OffsetDateTime expiresAt) {
        return createToken(SessionTokenKind.ACCESS, expiresAt);
    }

    public String createRefreshToken(OffsetDateTime expiresAt) {
        return createToken(SessionTokenKind.REFRESH, expiresAt);
    }

    public SessionTokenClaims verifyAccessToken(String token) {
        return verify(token, SessionTokenKind.ACCESS);
    }

    public SessionTokenClaims verifyRefreshToken(String token) {
        return verify(token, SessionTokenKind.REFRESH);
    }

    public String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance(TOKEN_HASH_ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to hash session token", ex);
        }
    }

    private String createToken(SessionTokenKind tokenKind, OffsetDateTime expiresAt) {
        String nonce = generateNonce();
        String payload = payload(tokenKind, expiresAt.toEpochSecond(), nonce);
        return payload + "." + sign(payload);
    }

    private SessionTokenClaims verify(String token, SessionTokenKind expectedKind) {
        if (token == null || token.isBlank()) {
            throw invalidToken(expectedKind);
        }

        String[] parts = token.split("\\.", TOKEN_SPLIT_LIMIT);
        if (parts.length != TOKEN_PART_COUNT) {
            throw invalidToken(expectedKind);
        }
        if (!TOKEN_VERSION.equals(parts[0])) {
            throw invalidToken(expectedKind);
        }

        SessionTokenKind actualKind;
        try {
            actualKind = SessionTokenKind.fromCode(parts[1]);
        } catch (IllegalArgumentException ex) {
            throw invalidToken(expectedKind);
        }
        if (actualKind != expectedKind) {
            throw invalidToken(expectedKind);
        }

        long expiresAtEpochSeconds = parsePositiveLong(parts[2], expectedKind);
        String payload = payload(actualKind, expiresAtEpochSeconds, parts[3]);
        String actualSignature = parts[4];
        String expectedSignature = sign(payload);

        if (!MessageDigest.isEqual(
            expectedSignature.getBytes(StandardCharsets.US_ASCII),
            actualSignature.getBytes(StandardCharsets.US_ASCII)
        )) {
            throw invalidToken(expectedKind);
        }

        return new SessionTokenClaims(
            actualKind,
            OffsetDateTime.ofInstant(Instant.ofEpochSecond(expiresAtEpochSeconds), ZoneOffset.UTC)
        );
    }

    private String payload(SessionTokenKind tokenKind, long expiresAtEpochSeconds, String nonce) {
        return TOKEN_VERSION
            + "." + tokenKind.code()
            + "." + expiresAtEpochSeconds
            + "." + nonce;
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(secretKey);
            byte[] signature = mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to sign session token", ex);
        }
    }

    private String generateNonce() {
        byte[] nonce = new byte[NONCE_LENGTH];
        secureRandom.nextBytes(nonce);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);
    }

    private long parsePositiveLong(String value, SessionTokenKind tokenKind) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw invalidToken(tokenKind);
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw invalidToken(tokenKind);
        }
    }

    private ApiException invalidToken(SessionTokenKind tokenKind) {
        String message = tokenKind == SessionTokenKind.REFRESH ? "Invalid refresh token" : "Invalid token";
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }

    private boolean hasLowDiversity(String secret) {
        return secret.chars().distinct().count() < MIN_SECRET_DISTINCT_CHARS;
    }

    public record SessionTokenClaims(SessionTokenKind tokenKind, OffsetDateTime expiresAt) {
    }
}
