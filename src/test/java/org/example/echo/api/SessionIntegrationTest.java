package org.example.echo.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.echo.mail.InMemoryEmailDeliveryGateway;
import org.example.echo.entity.SessionEntity;
import org.example.echo.repository.EmailOutboxRepository;
import org.example.echo.repository.SessionRepository;
import org.example.echo.repository.UserRepository;
import org.example.echo.service.SessionTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.example.echo.support.EmailTestSupport.awaitNextMessage;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionIntegrationTest {
    private static final Pattern TOKEN_PATTERN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailOutboxRepository emailOutboxRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private SessionTokenService sessionTokenService;

    @Autowired
    private InMemoryEmailDeliveryGateway inMemoryEmailDeliveryGateway;

    @BeforeEach
    void cleanData() {
        inMemoryEmailDeliveryGateway.clear();
        emailOutboxRepository.deleteAll();
        sessionRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void meReturnsCurrentUserForActiveSession() throws Exception {
        JsonNode tokens = registerAndReadTokens("session@example.com", "session_user", "pass12345");

        mockMvc.perform(get("/v1/me")
                .header("Authorization", bearer(tokens.path("accessToken").asText())))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.email").value("session@example.com"))
            .andExpect(jsonPath("$.username").value("session_user"));
    }

    @Test
    void publicUserProfileDoesNotExposeSensitiveFields() throws Exception {
        JsonNode registerPayload = registerAndReadRegisterPayload("public-user@example.com", "public_user", "pass12345");
        String userId = registerPayload.path("user").path("userId").asText();

        mockMvc.perform(get("/v1/users/{userId}", userId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(userId))
            .andExpect(jsonPath("$.username").value("public_user"))
            .andExpect(jsonPath("$.email").doesNotExist())
            .andExpect(jsonPath("$.role").doesNotExist())
            .andExpect(jsonPath("$.isActive").doesNotExist())
            .andExpect(jsonPath("$.createdAt").doesNotExist())
            .andExpect(jsonPath("$.updatedAt").doesNotExist());
    }

    @Test
    void meReturnsUnauthorizedWithoutAuthorizationHeader() throws Exception {
        mockMvc.perform(get("/v1/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.error.message").value("Missing Authorization header"));
    }

    @Test
    void logoutDeactivatesCurrentSessionEvenIfAccessTokenExpired() throws Exception {
        JsonNode tokens = registerAndReadTokens("logout@example.com", "logout_user", "pass12345");
        String accessToken = tokens.path("accessToken").asText();

        SessionEntity session = sessionRepository.findByAccessTokenHash(sessionTokenService.hashToken(accessToken)).orElseThrow();
        session.setAccessExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        sessionRepository.saveAndFlush(session);

        mockMvc.perform(post("/v1/auth/logout")
                .header("Authorization", bearer(accessToken)))
            .andExpect(status().isNoContent());

        SessionEntity loggedOutSession = sessionRepository.findByAccessTokenHash(sessionTokenService.hashToken(accessToken)).orElseThrow();
        assertThat(loggedOutSession.getIsActive()).isFalse();
        assertThat(loggedOutSession.getDeactivatedAt()).isNotNull();

        mockMvc.perform(get("/v1/me")
                .header("Authorization", bearer(accessToken)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.error.message").value("Invalid session"));
    }

    @Test
    void refreshRotatesTokensIntoNewSessionRow() throws Exception {
        JsonNode tokens = registerAndReadTokens("refresh@example.com", "refresh_user", "pass12345");
        String oldAccessToken = tokens.path("accessToken").asText();
        String oldRefreshToken = tokens.path("refreshToken").asText();

        MvcResult result = mockMvc.perform(post("/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of("refreshToken", oldRefreshToken))))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.accessToken").isString())
            .andExpect(jsonPath("$.refreshToken").isString())
            .andReturn();

        JsonNode refreshedTokens = objectMapper.readTree(result.getResponse().getContentAsString());
        String newAccessToken = refreshedTokens.path("accessToken").asText();
        String newRefreshToken = refreshedTokens.path("refreshToken").asText();

        assertThat(newAccessToken).isNotBlank().isNotEqualTo(oldAccessToken);
        assertThat(newRefreshToken).isNotBlank().isNotEqualTo(oldRefreshToken);

        SessionEntity oldSession = sessionRepository.findByRefreshTokenHash(sessionTokenService.hashToken(oldRefreshToken)).orElseThrow();
        assertThat(oldSession.getIsActive()).isFalse();
        assertThat(oldSession.getDeactivatedAt()).isNotNull();

        SessionEntity newSession = sessionRepository.findByRefreshTokenHash(sessionTokenService.hashToken(newRefreshToken)).orElseThrow();
        assertThat(newSession.getIsActive()).isTrue();
        assertThat(newSession.getId()).isNotEqualTo(oldSession.getId());
        assertThat(sessionRepository.count()).isEqualTo(2);

        mockMvc.perform(post("/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of("refreshToken", oldRefreshToken))))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.error.message").value("Invalid session"));
    }

    @Test
    void tamperedAccessTokenIsRejected() throws Exception {
        JsonNode tokens = registerAndReadTokens("tampered-access@example.com", "tampered_access", "pass12345");

        mockMvc.perform(get("/v1/me")
                .header("Authorization", bearer(tamperToken(tokens.path("accessToken").asText()))))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.error.message").value("Invalid token"));
    }

    @Test
    void accessTokenCannotBeUsedAsRefreshToken() throws Exception {
        JsonNode tokens = registerAndReadTokens("wrong-type@example.com", "wrong_type", "pass12345");

        mockMvc.perform(post("/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of("refreshToken", tokens.path("accessToken").asText()))))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.error.message").value("Invalid refresh token"));
    }

    @Test
    void expiredAccessTokenIsRejectedForCurrentUserLookup() throws Exception {
        JsonNode tokens = registerAndReadTokens("expired-access@example.com", "expired_access", "pass12345");
        String accessToken = tokens.path("accessToken").asText();

        SessionEntity session = sessionRepository.findByAccessTokenHash(sessionTokenService.hashToken(accessToken)).orElseThrow();
        session.setAccessExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        sessionRepository.saveAndFlush(session);

        mockMvc.perform(get("/v1/me")
                .header("Authorization", bearer(accessToken)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.error.message").value("Invalid session"));
    }

    @Test
    void expiredRefreshTokenDeactivatesSession() throws Exception {
        JsonNode tokens = registerAndReadTokens("expired-refresh@example.com", "expired_refresh", "pass12345");
        String refreshToken = tokens.path("refreshToken").asText();

        SessionEntity session = sessionRepository.findByRefreshTokenHash(sessionTokenService.hashToken(refreshToken)).orElseThrow();
        session.setRefreshExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        sessionRepository.saveAndFlush(session);

        mockMvc.perform(post("/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of("refreshToken", refreshToken))))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.error.message").value("Invalid session"));

        SessionEntity expiredSession = sessionRepository.findByRefreshTokenHash(sessionTokenService.hashToken(refreshToken)).orElseThrow();
        assertThat(expiredSession.getIsActive()).isFalse();
        assertThat(expiredSession.getDeactivatedAt()).isNotNull();
    }

    @Test
    void refreshTokenReuseCompromisesWholeSessionFamily() throws Exception {
        JsonNode tokens = registerAndReadTokens("reuse@example.com", "reuse_user", "pass12345");
        String oldRefreshToken = tokens.path("refreshToken").asText();

        MvcResult refreshResult = mockMvc.perform(post("/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of("refreshToken", oldRefreshToken))))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode refreshedTokens = objectMapper.readTree(refreshResult.getResponse().getContentAsString());
        String newAccessToken = refreshedTokens.path("accessToken").asText();
        String newRefreshToken = refreshedTokens.path("refreshToken").asText();

        mockMvc.perform(post("/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of("refreshToken", oldRefreshToken))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.error.message").value("Invalid session"));

        mockMvc.perform(get("/v1/me")
                .header("Authorization", bearer(newAccessToken)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.error.message").value("Invalid session"));

        mockMvc.perform(post("/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of("refreshToken", newRefreshToken))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.error.message").value("Invalid session"));
    }

    @Test
    void logoutRejectsCompromisedSessionFamily() throws Exception {
        JsonNode tokens = registerAndReadTokens("logout-compromised@example.com", "logout_compromised", "pass12345");
        String oldRefreshToken = tokens.path("refreshToken").asText();

        JsonNode refreshedTokens = objectMapper.readTree(mockMvc.perform(post("/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of("refreshToken", oldRefreshToken))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());

        String newAccessToken = refreshedTokens.path("accessToken").asText();

        mockMvc.perform(post("/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of("refreshToken", oldRefreshToken))))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/v1/auth/logout")
                .header("Authorization", bearer(newAccessToken)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.error.message").value("Invalid session"));
    }

    @Test
    void refreshingLoggedOutSessionCompromisesWholeSessionFamily() throws Exception {
        JsonNode tokens = registerAndReadTokens("logout-reuse@example.com", "logout_reuse", "pass12345");
        String oldRefreshToken = tokens.path("refreshToken").asText();

        JsonNode refreshedTokens = objectMapper.readTree(mockMvc.perform(post("/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of("refreshToken", oldRefreshToken))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());

        String newAccessToken = refreshedTokens.path("accessToken").asText();
        String newRefreshToken = refreshedTokens.path("refreshToken").asText();
        SessionEntity activeSession = sessionRepository.findByRefreshTokenHash(sessionTokenService.hashToken(newRefreshToken)).orElseThrow();
        UUID tokenFamilyId = activeSession.getTokenFamilyId();

        mockMvc.perform(post("/v1/auth/logout")
                .header("Authorization", bearer(newAccessToken)))
            .andExpect(status().isNoContent());

        mockMvc.perform(post("/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of("refreshToken", newRefreshToken))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.error.message").value("Invalid session"));

        List<SessionEntity> familySessions = sessionRepository.findAllByTokenFamilyId(tokenFamilyId);
        assertThat(familySessions).hasSize(2);
        assertThat(familySessions).allMatch(session -> session.getCompromisedAt() != null);
    }

    private JsonNode registerAndReadTokens(String email, String username, String password) throws Exception {
        int previousMessageCount = inMemoryEmailDeliveryGateway.messages().size();
        registerAndReadRegisterPayload(email, username, password);

        String verificationToken = extractToken(awaitNextMessage(inMemoryEmailDeliveryGateway, previousMessageCount).text());

        MvcResult result = mockMvc.perform(post("/v1/auth/email-verification/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of("token", verificationToken))))
            .andExpect(status().isOk())
            .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode registerAndReadRegisterPayload(String email, String username, String password) throws Exception {
        MvcResult registerResult = mockMvc.perform(post("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of(
                    "email", email,
                    "username", username,
                    "password", password
                ))))
            .andExpect(status().isCreated())
            .andReturn();

        return objectMapper.readTree(registerResult.getResponse().getContentAsString());
    }

    private String asJson(Map<String, Object> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String extractToken(String body) {
        Matcher matcher = TOKEN_PATTERN.matcher(body);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private String tamperToken(String token) {
        char replacement = token.charAt(token.length() - 1) == 'a' ? 'b' : 'a';
        return token.substring(0, token.length() - 1) + replacement;
    }
}
