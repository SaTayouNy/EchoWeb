package org.example.echo.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.echo.entity.EmailOutboxStatus;
import org.example.echo.entity.SessionEntity;
import org.example.echo.entity.UserEntity;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.example.echo.support.EmailTestSupport.awaitFirstMessage;
import static org.example.echo.support.EmailTestSupport.awaitMessages;
import static org.example.echo.support.EmailTestSupport.awaitNextMessage;
import static org.example.echo.support.EmailTestSupport.awaitOutboxStatusCount;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIntegrationTest {
    private static final Pattern TOKEN_PATTERN = Pattern.compile("token=([A-Za-z0-9_-]+)");
    private static final String EMAIL_VERIFICATION_SUBJECT = "Подтвердите свой адрес электронной почты";
    private static final String DUPLICATE_REGISTRATION_SUBJECT = "Попытка повторной регистрации";

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
    private BCryptPasswordEncoder passwordEncoder;

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
    void registerAllowsViteDevOriginPreflight() throws Exception {
        mockMvc.perform(options("/v1/auth/register")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
    }

    @Test
    void registerCreatesUserWithHashedPasswordAndHashedSessionTokens() throws Exception {
        String rawPassword = "mySecret123";
        mockMvc.perform(post("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of(
                    "email", "NewUser@example.com",
                    "username", "  NewUser  ",
                    "password", rawPassword
                ))))
            .andExpect(status().isCreated())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.user.email").value("newuser@example.com"))
            .andExpect(jsonPath("$.user.username").value("NewUser"))
            .andExpect(jsonPath("$.user.role").value("USER"))
            .andExpect(jsonPath("$.emailVerificationRequired").value(true))
            .andExpect(jsonPath("$.tokens").doesNotExist());

        UserEntity savedUser = userRepository.findByEmailIgnoreCase("newuser@example.com").orElseThrow();
        assertThat(savedUser.getPasswordHash()).isNotEqualTo(rawPassword);
        assertThat(passwordEncoder.matches(rawPassword, savedUser.getPasswordHash())).isTrue();
        assertThat(savedUser.getEmailVerified()).isFalse();

        assertThat(sessionRepository.count()).isZero();

        assertThat(awaitMessages(inMemoryEmailDeliveryGateway, 1)).hasSize(1);
        assertThat(awaitFirstMessage(inMemoryEmailDeliveryGateway).to()).isEqualTo("newuser@example.com");
        assertThat(awaitFirstMessage(inMemoryEmailDeliveryGateway).subject()).isEqualTo(EMAIL_VERIFICATION_SUBJECT);
    }

    @Test
    void registerWithoutEmailReturnsTokensAndAllowsUsernameLogin() throws Exception {
        String rawPassword = "pass12345";
        mockMvc.perform(post("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of(
                    "username", "username_only_user",
                    "password", rawPassword
                ))))
            .andExpect(status().isCreated())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.user.email").isEmpty())
            .andExpect(jsonPath("$.user.username").value("username_only_user"))
            .andExpect(jsonPath("$.emailVerificationRequired").value(false))
            .andExpect(jsonPath("$.tokens.accessToken").isString())
            .andExpect(jsonPath("$.tokens.refreshToken").isString());

        UserEntity savedUser = userRepository.findByUsernameIgnoreCase("username_only_user").orElseThrow();
        assertThat(savedUser.getEmail()).isNull();
        assertThat(savedUser.getEmailVerified()).isTrue();
        assertThat(inMemoryEmailDeliveryGateway.messages()).isEmpty();

        mockMvc.perform(post("/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of(
                    "username", "username_only_user",
                    "password", rawPassword
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isString())
            .andExpect(jsonPath("$.refreshToken").isString());
    }

    @Test
    void loginReturnsTokensForValidCredentials() throws Exception {
        registerAndVerifyUser("login@example.com", "login_user", "pass12345");

        mockMvc.perform(post("/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of(
                    "email", "LOGIN@example.com",
                    "password", "pass12345"
                ))))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.accessToken").isString())
            .andExpect(jsonPath("$.refreshToken").isString());

        assertThat(sessionRepository.count()).isEqualTo(2);
    }

    @Test
    void loginRejectsUnverifiedEmail() throws Exception {
        registerUser("pending@example.com", "pending_user", "pass12345");

        mockMvc.perform(post("/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of(
                    "email", "pending@example.com",
                    "password", "pass12345"
                ))))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.error.code").value("EMAIL_NOT_VERIFIED"))
            .andExpect(jsonPath("$.error.message").value("Email is not verified"));
    }

    @Test
    void loginReturnsUnauthorizedForInvalidPassword() throws Exception {
        registerAndVerifyUser("auth@example.com", "auth_user", "pass12345");

        mockMvc.perform(post("/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of(
                    "email", "auth@example.com",
                    "password", "wrong-pass"
                ))))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.error.message").value("Invalid credentials"));
    }

    @Test
    void emailVerificationConfirmsEmailAndReturnsTokens() throws Exception {
        registerUser("verify@example.com", "verify_user", "pass12345");

        String verificationToken = extractToken(awaitFirstMessage(inMemoryEmailDeliveryGateway).text());

        MvcResult result = mockMvc.perform(post("/v1/auth/email-verification/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of("token", verificationToken))))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.accessToken").isString())
            .andExpect(jsonPath("$.refreshToken").isString())
            .andReturn();

        JsonNode payload = objectMapper.readTree(result.getResponse().getContentAsString());
        String accessToken = payload.path("accessToken").asText();
        String refreshToken = payload.path("refreshToken").asText();

        UserEntity savedUser = userRepository.findByEmailIgnoreCase("verify@example.com").orElseThrow();
        assertThat(savedUser.getEmailVerified()).isTrue();

        SessionEntity savedSession = sessionRepository.findByAccessTokenHash(sessionTokenService.hashToken(accessToken)).orElseThrow();
        assertThat(savedSession.getUser().getUserId()).isEqualTo(savedUser.getUserId());
        assertThat(savedSession.getAccessTokenHash()).isEqualTo(sessionTokenService.hashToken(accessToken));
        assertThat(savedSession.getRefreshTokenHash()).isEqualTo(sessionTokenService.hashToken(refreshToken));
    }

    @Test
    void emailVerificationTokenCannotBeReused() throws Exception {
        registerUser("single-use@example.com", "single_use_user", "pass12345");

        String verificationToken = extractToken(awaitFirstMessage(inMemoryEmailDeliveryGateway).text());

        mockMvc.perform(post("/v1/auth/email-verification/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of("token", verificationToken))))
            .andExpect(status().isOk());

        mockMvc.perform(post("/v1/auth/email-verification/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of("token", verificationToken))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.error.message").value("Invalid or expired email verification token"));
    }

    @Test
    void emailVerificationRequestResendsEmailForPendingAccount() throws Exception {
        registerUser("resend@example.com", "resend_user", "pass12345");
        inMemoryEmailDeliveryGateway.clear();

        mockMvc.perform(post("/v1/auth/email-verification/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of("email", "resend@example.com"))))
            .andExpect(status().isNoContent());

        assertThat(awaitMessages(inMemoryEmailDeliveryGateway, 1)).hasSize(1);
        assertThat(awaitFirstMessage(inMemoryEmailDeliveryGateway).subject()).isEqualTo(EMAIL_VERIFICATION_SUBJECT);
    }

    @Test
    void registerReturnsCreatedForDuplicateEmailAndSendsNotice() throws Exception {
        registerAndVerifyUser("dup@example.com", "first_user", "pass12345");
        int previousMessageCount = inMemoryEmailDeliveryGateway.messages().size();

        mockMvc.perform(post("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of(
                    "email", "DUP@example.com",
                    "username", "second_user",
                    "password", "pass12345"
                ))))
            .andExpect(status().isCreated())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.user.email").value("dup@example.com"))
            .andExpect(jsonPath("$.user.username").value("second_user"))
            .andExpect(jsonPath("$.emailVerificationRequired").value(true));

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(awaitNextMessage(inMemoryEmailDeliveryGateway, previousMessageCount).subject()).isEqualTo(DUPLICATE_REGISTRATION_SUBJECT);
    }

    @Test
    void registerRejectsDuplicateUsernameWithoutCreatingUser() throws Exception {
        registerUser("username-owner@example.com", "shared_user", "pass12345");
        awaitMessages(inMemoryEmailDeliveryGateway, 1);
        int previousMessageCount = inMemoryEmailDeliveryGateway.messages().size();

        mockMvc.perform(post("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of(
                    "email", "new-email@example.com",
                    "username", "shared_user",
                    "password", "pass12345"
                ))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("USERNAME_TAKEN"))
            .andExpect(jsonPath("$.user").doesNotExist());

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(userRepository.findByEmailIgnoreCase("new-email@example.com")).isEmpty();
        assertThat(inMemoryEmailDeliveryGateway.messages()).hasSize(previousMessageCount);
    }

    @Test
    void registerRejectsJsonBodyAboveConfiguredLimit() throws Exception {
        mockMvc.perform(post("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of(
                    "email", "large-body@example.com",
                    "username", "large_body_user",
                    "password", "x".repeat(70000)
                ))))
            .andExpect(status().isPayloadTooLarge())
            .andExpect(jsonPath("$.error.code").value("PAYLOAD_TOO_LARGE"));
    }

    @Test
    void registerReturnsValidationErrorForInvalidPayload() throws Exception {
        mockMvc.perform(post("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of(
                    "email", "bad-email",
                    "username", "   ",
                    "password", "123"
                ))))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.message").value("Validation failed"))
            .andExpect(jsonPath("$.error.details").isArray())
            .andExpect(jsonPath("$.error.details[0].field").isString());
    }

    @Test
    void registerRejectsUsernameWithControlCharacters() throws Exception {
        mockMvc.perform(post("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of(
                    "email", "control@example.com",
                    "username", "bad\r\nBcc:evil@example.com",
                    "password", "pass12345"
                ))))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.error.details[*].field").value(hasItem("username")));

        assertThat(userRepository.findByEmailIgnoreCase("control@example.com")).isEmpty();
    }

    @Test
    void registerSucceedsAndKeepsFailedEmailInOutboxWhenDeliveryFails() throws Exception {
        inMemoryEmailDeliveryGateway.failNextDelivery();

        mockMvc.perform(post("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of(
                    "email", "mail-fail@example.com",
                    "username", "mail_fail_user",
                    "password", "pass12345"
                ))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.emailVerificationRequired").value(true));

        assertThat(userRepository.findByEmailIgnoreCase("mail-fail@example.com")).isPresent();
        assertThat(sessionRepository.count()).isZero();
        assertThat(inMemoryEmailDeliveryGateway.messages()).isEmpty();
        awaitOutboxStatusCount(emailOutboxRepository, EmailOutboxStatus.FAILED, 1);
    }

    private void registerUser(String email, String username, String password) throws Exception {
        mockMvc.perform(post("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of(
                    "email", email,
                    "username", username,
                    "password", password
                ))))
            .andExpect(status().isCreated());
    }

    private void registerAndVerifyUser(String email, String username, String password) throws Exception {
        int previousMessageCount = inMemoryEmailDeliveryGateway.messages().size();
        registerUser(email, username, password);
        String verificationToken = extractToken(awaitNextMessage(inMemoryEmailDeliveryGateway, previousMessageCount).text());

        mockMvc.perform(post("/v1/auth/email-verification/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of("token", verificationToken))))
            .andExpect(status().isOk());
    }

    private String extractToken(String body) {
        Matcher matcher = TOKEN_PATTERN.matcher(body);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private String asJson(Map<String, Object> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }
}
