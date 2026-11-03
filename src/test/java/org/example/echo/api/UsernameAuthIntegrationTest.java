package org.example.echo.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.echo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UsernameAuthIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;

    @Test
    void usernameOnlyAccountCanLoginReadProfileRefreshAndLogout() throws Exception {
        String name = "Account_" + UUID.randomUUID();
        JsonNode registered = register(name, "CorrectPass123!");
        String id = registered.path("user").path("userId").asText();
        mvc.perform(get("/v1/me").header("Authorization", "Bearer " + registered.path("tokens").path("accessToken").asText()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(id));
        var login = mvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("username", " " + name.toLowerCase(java.util.Locale.ROOT) + " ", "password", "CorrectPass123!"))))
            .andExpect(status().isOk()).andReturn();
        JsonNode old = json.readTree(login.getResponse().getContentAsString());
        var refreshed = mvc.perform(post("/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("refreshToken", old.path("refreshToken").asText()))))
            .andExpect(status().isOk()).andReturn();
        JsonNode next = json.readTree(refreshed.getResponse().getContentAsString());
        mvc.perform(get("/v1/me").header("Authorization", "Bearer " + old.path("accessToken").asText())).andExpect(status().isUnauthorized());
        mvc.perform(get("/v1/me").header("Authorization", "Bearer " + next.path("accessToken").asText()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(id));
        mvc.perform(post("/v1/auth/logout").header("Authorization", "Bearer " + next.path("accessToken").asText())).andExpect(status().isNoContent());
        mvc.perform(get("/v1/me").header("Authorization", "Bearer " + next.path("accessToken").asText())).andExpect(status().isUnauthorized());
        mvc.perform(post("/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("refreshToken", next.path("refreshToken").asText())))).andExpect(status().isUnauthorized());
    }

    static Stream<String> invalidRegistrationBodies() {
        return Stream.of("{}", "{\"username\":null,\"password\":null}",
            "{\"username\":\"   \",\"password\":\"Valid123!\"}",
            "{\"username\":\"valid\",\"password\":\"short\"}",
            "{\"username\":\"" + "x".repeat(81) + "\",\"password\":\"Valid123!\"}",
            "{\"username\":\"valid\",\"password\":\"" + "a".repeat(73) + "\"}",
            "{\"username\":\"valid\",\"password\":\"" + "я".repeat(37) + "\"}");
    }
    @ParameterizedTest @MethodSource("invalidRegistrationBodies")
    void invalidRegistrationReturnsValidationError(String body) throws Exception {
        mvc.perform(post("/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void passwordBoundaryIsMeasuredInBytesAndSuffixCannotBeIgnored() throws Exception {
        String name = "Utf8_" + UUID.randomUUID();
        register(name, "я".repeat(36)); // 72 UTF-8 bytes.
        mvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("username", name, "password", "я".repeat(36)))))
            .andExpect(status().isOk());
        mvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("username", name, "password", "я".repeat(36) + "different"))))
            .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/v1/auth/password-recovery/confirm").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("token", "invalid", "newPassword", "я".repeat(37)))))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void loginRequiresIdentifierAndRejectsMalformedJsonWithoutServerError() throws Exception {
        mvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"Valid123!\"}"))
            .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":{}}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("{broken"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void concurrentCaseVariantsCannotCreateTwoAccounts() throws Exception {
        String name = "Race_" + UUID.randomUUID();
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var calls = List.of(name.toUpperCase(java.util.Locale.ROOT), name.toLowerCase(java.util.Locale.ROOT)).stream()
                .map(candidate -> pool.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    return mvc.perform(post("/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", candidate, "password", "Valid123!"))))
                        .andReturn().getResponse().getStatus();
                })).toList();
            start.countDown();
            assertThat(List.of(calls.get(0).get(15, TimeUnit.SECONDS), calls.get(1).get(15, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(201, 409);
            assertThat(users.findByUsernameNormalized(name.toLowerCase(java.util.Locale.ROOT))).isPresent();
        }
    }

    private JsonNode register(String name, String password) throws Exception {
        var result = mvc.perform(post("/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("username", name, "password", password))))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.emailVerificationRequired").value(false))
            .andExpect(jsonPath("$.tokens.accessToken").isString()).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }
}
