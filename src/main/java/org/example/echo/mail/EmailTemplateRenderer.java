package org.example.echo.mail;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class EmailTemplateRenderer {
    private static final String CLASSPATH_PREFIX = "classpath:";
    private static final List<String> REQUIRED_TEMPLATES = List.of(
        "mail/email-verification-subject.txt",
        "mail/email-verification-body.txt",
        "mail/password-recovery-subject.txt",
        "mail/password-recovery-body.txt",
        "mail/duplicate-registration-subject.txt",
        "mail/duplicate-registration-body.txt"
    );

    private final ResourceLoader resourceLoader;
    private final Map<String, String> templateCache = new ConcurrentHashMap<>();

    public EmailTemplateRenderer(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    void validateTemplates() {
        REQUIRED_TEMPLATES.forEach(this::readResource);
    }

    public EmailTemplate render(String subjectPath, String bodyPath, Map<String, String> variables) {
        Map<String, String> sanitizedVariables = sanitizeVariables(variables);
        return new EmailTemplate(
            sanitizeHeaderValue(renderTemplate(subjectPath, sanitizedVariables).strip()),
            renderTemplate(bodyPath, sanitizedVariables)
        );
    }

    private String renderTemplate(String path, Map<String, String> variables) {
        String rendered = readResource(path);
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return rendered;
    }

    private String readResource(String path) {
        return templateCache.computeIfAbsent(path, this::loadResource);
    }

    void clearCacheForTests() {
        templateCache.clear();
    }

    private String loadResource(String path) {
        Resource resource = resourceLoader.getResource(CLASSPATH_PREFIX + path);
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load email template: " + path, ex);
        }
    }

    private Map<String, String> sanitizeVariables(Map<String, String> variables) {
        Map<String, String> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            sanitized.put(entry.getKey(), sanitizeVariableValue(entry.getValue()));
        }
        return sanitized;
    }

    private String sanitizeVariableValue(String value) {
        if (value == null) {
            return "";
        }

        StringBuilder sanitized = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == '\r' || current == '\n') {
                sanitized.append(' ');
                continue;
            }
            if (Character.isISOControl(current) && current != '\t') {
                continue;
            }
            sanitized.append(current);
        }
        return sanitized.toString();
    }

    private String sanitizeHeaderValue(String value) {
        return sanitizeVariableValue(value).strip();
    }
}
