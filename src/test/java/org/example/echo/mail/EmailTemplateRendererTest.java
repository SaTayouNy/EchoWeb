package org.example.echo.mail;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EmailTemplateRendererTest {
    private final EmailTemplateRenderer renderer = new EmailTemplateRenderer(new DefaultResourceLoader());

    @Test
    void renderSanitizesControlCharactersInTemplateVariables() {
        EmailTemplate template = renderer.render(
            "mail/test-subject.txt",
            "mail/test-body.txt",
            Map.of(
                "name", "User\r\nBcc: target@evil.com",
                "link", "https://example.com/reset\r\nX-Injected: yes"
            )
        );

        assertThat(template.subject()).isEqualTo("Hello User  Bcc: target@evil.com");
        assertThat(template.subject()).doesNotContain("\r", "\n");
        assertThat(template.body()).doesNotContain("\r");
        assertThat(template.body()).doesNotContain("\nX-Injected:");
        assertThat(template.body()).contains("User  Bcc: target@evil.com");
    }
}
