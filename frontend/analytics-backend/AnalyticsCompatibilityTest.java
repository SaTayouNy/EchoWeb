package org.example.echo.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Проверка: запросы фронтенда v10.40 (с полями аналитики) принимаются текущим бэкендом. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnalyticsCompatibilityTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void frontendAnalyticsFieldsAreAccepted() throws Exception {
        String anon = UUID.randomUUID().toString(), session = UUID.randomUUID().toString();
        String name = "an_" + UUID.randomUUID().toString().substring(0, 8);
        JsonNode reg = json.readTree(mvc.perform(post("/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("username", name, "password", "StrongPass123!", "anon_id", anon, "session_id", session))))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mvc.perform(post("/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("username", name, "password", "StrongPass123!", "anon_id", anon, "session_id", session))))
            .andExpect(status().isOk());
        String token = "Bearer " + reg.path("tokens").path("accessToken").asText();
        mvc.perform(put("/v2/me/ratings/movie/{id}", 88001).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("schemeVersion", "echo-form-impression-v1", "parameters", Map.of("dramaturgy_structure", 9, "characters_performance", 8,
                "visual_language", 7, "sound_music", 9, "impression", 9))))).andExpect(status().isOk());
        mvc.perform(post("/v2/reviews").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("tmdbId", 88001, "mediaType", "MOVIE", "title", "Заголовок", "content", "Текст рецензии",
                "draft", false, "spoiler", false, "form_instance_id", UUID.randomUUID().toString()))))
            .andExpect(status().isCreated());
        int events = mvc.perform(post("/events").contentType(MediaType.APPLICATION_JSON).content("[]")).andReturn().getResponse().getStatus();
        System.out.println("ANALYTICS_EVENTS_ENDPOINT_STATUS=" + events);
    }
}
