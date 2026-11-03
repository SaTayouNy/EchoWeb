package org.example.echo.service;

import org.example.echo.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionTokenServiceTest {
    private final SessionTokenService sessionTokenService = new SessionTokenService("session-token-test-secret-1234567890");

    @Test
    void createsAndVerifiesAccessToken() {
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(15).withNano(0);

        String token = sessionTokenService.createAccessToken(expiresAt);
        SessionTokenService.SessionTokenClaims claims = sessionTokenService.verifyAccessToken(token);

        assertThat(claims.tokenKind()).isEqualTo(SessionTokenKind.ACCESS);
        assertThat(claims.expiresAt().toEpochSecond()).isEqualTo(expiresAt.toEpochSecond());
    }

    @Test
    void rejectsAccessTokenWhenRefreshTokenIsExpected() {
        String accessToken = sessionTokenService.createAccessToken(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(15));

        assertThatThrownBy(() -> sessionTokenService.verifyRefreshToken(accessToken))
            .isInstanceOf(ApiException.class)
            .hasMessage("Invalid refresh token");
    }

    @Test
    void rejectsTokensWithUnexpectedExtraPart() {
        String accessToken = sessionTokenService.createAccessToken(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(15));

        assertThatThrownBy(() -> sessionTokenService.verifyAccessToken(accessToken + ".extra"))
            .isInstanceOf(ApiException.class)
            .hasMessage("Invalid token");
    }

    @Test
    void hashesTokensDeterministically() {
        String token = sessionTokenService.createRefreshToken(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(15));

        String firstHash = sessionTokenService.hashToken(token);
        String secondHash = sessionTokenService.hashToken(token);

        assertThat(firstHash).isEqualTo(secondHash);
        assertThat(firstHash).hasSize(64);
        assertThat(firstHash).isNotEqualTo(token);
    }

    @Test
    void rejectsTooShortSecret() {
        assertThatThrownBy(() -> new SessionTokenService("too-short-secret"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("app.auth.token.secret must be at least 32 bytes");
    }

    @Test
    void rejectsLowDiversitySecret() {
        assertThatThrownBy(() -> new SessionTokenService("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("app.auth.token.secret must have sufficient character diversity");
    }
}
