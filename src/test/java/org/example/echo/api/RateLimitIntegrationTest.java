package org.example.echo.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.echo.repository.SessionRepository;
import org.example.echo.repository.UserRepository;
import org.example.echo.security.FixedWindowRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "app.security.rate-limit.login.capacity=2",
    "app.security.rate-limit.login.window=PT1H",
    "app.security.rate-limit.register.capacity=100",
    "app.security.rate-limit.password-recovery-request.capacity=100",
    "app.security.rate-limit.password-recovery-confirm.capacity=100",
    "app.security.rate-limit.media-lookup.capacity=1",
    "app.security.rate-limit.media-lookup.window=PT1H",
    "app.security.rate-limit.media-search.capacity=2",
    "app.security.rate-limit.media-search.window=PT1H"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimitIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private FixedWindowRateLimiter fixedWindowRateLimiter;

    @BeforeEach
    void cleanData() {
        fixedWindowRateLimiter.clear();
        sessionRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void loginIsRateLimitedByIp() throws Exception {
        registerUser("rate@example.com", "rate_user", "pass12345");

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJson(Map.of(
                        "email", "rate@example.com",
                        "password", "wrong-pass"
                    ))))
                .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of(
                    "email", "rate@example.com",
                    "password", "wrong-pass"
                ))))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"))
            .andExpect(jsonPath("$.error.message").value("Too many requests"));
    }

    @Test
    void spoofedForwardedForDoesNotBypassRateLimitByDefault() throws Exception {
        registerUser("rate-xff@example.com", "rate_xff_user", "pass12345");

        mockMvc.perform(post("/v1/auth/login")
                .header("X-Forwarded-For", "198.51.100.10")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of(
                    "email", "rate-xff@example.com",
                    "password", "wrong-pass"
                ))))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/v1/auth/login")
                .header("X-Forwarded-For", "203.0.113.77")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of(
                    "email", "rate-xff@example.com",
                    "password", "wrong-pass"
                ))))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/v1/auth/login")
                .header("X-Forwarded-For", "192.0.2.55")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJson(Map.of(
                    "email", "rate-xff@example.com",
                    "password", "wrong-pass"
                ))))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
    }

    @Test
    void mediaLookupIsRateLimitedByIp() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/v1/media/movie/{tmdbId}", 101L))
            .andExpect(status().isOk());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/v1/media/movie/{tmdbId}", 101L))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
    }

    @Test
    void mediaSearchIsRateLimitedByIp() throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/v1/media/search/movies")
                    .param("query", "matrix"))
                .andExpect(status().isOk());
        }

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/v1/media/search/movies")
                .param("query", "matrix"))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
    }

    @Test
    void mediaDiscoverIsRateLimitedByIp() throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/v1/media/discover/movies/popular"))
                .andExpect(status().isOk());
        }

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/v1/media/discover/movies/popular"))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
    }

    @Test
    void mediaRecommendationsUseSearchRateLimitRuleBeforeLookupRule() throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/v1/media/movie/{tmdbId}/recommendations",
                    101L
                ))
                .andExpect(status().isOk());
        }

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                "/v1/media/movie/{tmdbId}/recommendations",
                101L
            ))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
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

    private String asJson(Map<String, Object> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }
}
