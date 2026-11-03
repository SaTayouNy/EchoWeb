package org.example.echo.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.echo.entity.EmailOutboxStatus;
import org.example.echo.entity.SessionEntity;
import org.example.echo.mail.EmailMessage;
import org.example.echo.mail.InMemoryEmailDeliveryGateway;
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

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.example.echo.support.EmailTestSupport.awaitFirstMessage;
import static org.example.echo.support.EmailTestSupport.awaitNextMessage;
import static org.example.echo.support.EmailTestSupport.awaitOutboxStatusCount;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasswordRecoveryIntegrationTest {
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
    private InMemoryEmailDeliveryGateway inMemoryEmailDeliveryGateway;

    @Autowired
    private SessionTokenService sessionTokenService;

    @BeforeEach
    void cleanData() {
        inMemoryEmailDeliveryGateway.clear();
        emailOutboxRepository.deleteAll();
        sessionRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void passwordRecoveryResetsPasswordAndDeactivatesExistingSessions() throws Exception {
        JsonNode verificationPayload = registerAndVerifyUser("recovery@example.com", "recovery_user", "pass12345");
        String oldAccessToken = verificationPayload.path("accessToken").asText();

        inMemoryEmailDeliveryGateway.clear();

        mockMvc.perform(post("/v1/auth/password-recovery/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of("email", "recovery@example.com"))))
            .andExpect(status().isNoContent());

        EmailMessage emailMessage = awaitFirstMessage(inMemoryEmailDeliveryGateway);
        assertThat(emailMessage.to()).isEqualTo("recovery@example.com");

        String recoveryToken = extractToken(emailMessage.text());

        mockMvc.perform(post("/v1/auth/password-recovery/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of(
                    "token", recoveryToken,
                    "newPassword", "newPass12345"
                ))))
            .andExpect(status().isNoContent());

        mockMvc.perform(post("/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of(
                    "email", "recovery@example.com",
                    "password", "pass12345"
                ))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.message").value("Invalid credentials"));

        mockMvc.perform(post("/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of(
                    "email", "recovery@example.com",
                    "password", "newPass12345"
                ))))
            .andExpect(status().isOk());

        mockMvc.perform(get("/v1/me")
                .header("Authorization", bearer(oldAccessToken)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.message").value("Invalid session"));

        SessionEntity oldSession = sessionRepository.findByAccessTokenHash(sessionTokenService.hashToken(oldAccessToken)).orElseThrow();
        assertThat(oldSession.getIsActive()).isFalse();
    }

    @Test
    void passwordRecoveryRequestIgnoresUnknownEmail() throws Exception {
        mockMvc.perform(post("/v1/auth/password-recovery/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of("email", "missing@example.com"))))
            .andExpect(status().isNoContent());

        assertThat(inMemoryEmailDeliveryGateway.messages()).isEmpty();
    }

    @Test
    void passwordRecoveryConfirmRejectsInvalidToken() throws Exception {
        mockMvc.perform(post("/v1/auth/password-recovery/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of(
                    "token", "bad-token",
                    "newPassword", "newPass12345"
                ))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.error.message").value("Invalid or expired password recovery token"));
    }

    @Test
    void passwordRecoveryTokenCannotBeReused() throws Exception {
        registerAndVerifyUser("recovery-reuse@example.com", "recovery_reuse", "pass12345");
        inMemoryEmailDeliveryGateway.clear();

        mockMvc.perform(post("/v1/auth/password-recovery/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of("email", "recovery-reuse@example.com"))))
            .andExpect(status().isNoContent());

        String recoveryToken = extractToken(awaitFirstMessage(inMemoryEmailDeliveryGateway).text());

        mockMvc.perform(post("/v1/auth/password-recovery/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of(
                    "token", recoveryToken,
                    "newPassword", "newPass12345"
                ))))
            .andExpect(status().isNoContent());

        mockMvc.perform(post("/v1/auth/password-recovery/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of(
                    "token", recoveryToken,
                    "newPassword", "anotherPass12345"
                ))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.error.message").value("Invalid or expired password recovery token"));
    }

    @Test
    void passwordRecoveryRequestSucceedsAndKeepsFailedEmailInOutboxWhenDeliveryFails() throws Exception {
        registerAndVerifyUser("recovery-mail-fail@example.com", "recovery_mail_fail", "pass12345");
        inMemoryEmailDeliveryGateway.clear();
        inMemoryEmailDeliveryGateway.failNextDelivery();

        mockMvc.perform(post("/v1/auth/password-recovery/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of("email", "recovery-mail-fail@example.com"))))
            .andExpect(status().isNoContent());

        assertThat(inMemoryEmailDeliveryGateway.messages()).isEmpty();
        awaitOutboxStatusCount(emailOutboxRepository, EmailOutboxStatus.FAILED, 1);
    }

    private JsonNode registerAndVerifyUser(String email, String username, String password) throws Exception {
        int previousMessageCount = inMemoryEmailDeliveryGateway.messages().size();
        mockMvc.perform(post("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of(
                    "email", email,
                    "username", username,
                    "password", password
                ))))
            .andExpect(status().isCreated());

        String verificationToken = extractToken(awaitNextMessage(inMemoryEmailDeliveryGateway, previousMessageCount).text());

        MvcResult result = mockMvc.perform(post("/v1/auth/email-verification/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of("token", verificationToken))))
            .andExpect(status().isOk())
            .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String extractToken(String body) {
        Matcher matcher = TOKEN_PATTERN.matcher(body);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private String asJson(Map<String, Object> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
