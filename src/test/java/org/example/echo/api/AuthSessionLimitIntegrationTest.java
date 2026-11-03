package org.example.echo.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.echo.entity.SessionEntity;
import org.example.echo.mail.InMemoryEmailDeliveryGateway;
import org.example.echo.repository.EmailOutboxRepository;
import org.example.echo.repository.SessionRepository;
import org.example.echo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.example.echo.support.EmailTestSupport.awaitFirstMessage;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.auth.session.max-active-per-user=2")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthSessionLimitIntegrationTest {
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

    @BeforeEach
    void cleanData() {
        inMemoryEmailDeliveryGateway.clear();
        emailOutboxRepository.deleteAll();
        sessionRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void loginDeactivatesOldestSessionsAboveConfiguredLimit() throws Exception {
        registerAndVerifyUser("session-limit@example.com", "session_limit_user", "pass12345");

        login("session-limit@example.com", "pass12345");
        login("session-limit@example.com", "pass12345");
        login("session-limit@example.com", "pass12345");

        List<SessionEntity> activeSessions = sessionRepository.findAllByUserUserIdAndIsActiveTrueOrderByCreatedAtAsc(
            userRepository.findByEmailIgnoreCase("session-limit@example.com").orElseThrow().getUserId()
        );

        assertThat(activeSessions).hasSize(2);
        assertThat(sessionRepository.findAll()).hasSize(4);
        assertThat(sessionRepository.findAll().stream().filter(SessionEntity::getIsActive).count()).isEqualTo(2);
    }

    private void registerAndVerifyUser(String email, String username, String password) throws Exception {
        mockMvc.perform(post("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of(
                    "email", email,
                    "username", username,
                    "password", password
                ))))
            .andExpect(status().isCreated());

        String verificationToken = extractToken(awaitFirstMessage(inMemoryEmailDeliveryGateway).text());

        mockMvc.perform(post("/v1/auth/email-verification/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of("token", verificationToken))))
            .andExpect(status().isOk());
    }

    private void login(String email, String password) throws Exception {
        mockMvc.perform(post("/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of(
                    "email", email,
                    "password", password
                ))))
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
