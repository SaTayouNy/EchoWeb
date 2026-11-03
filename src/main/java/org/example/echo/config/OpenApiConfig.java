package org.example.echo.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI openAPI() {
        return new OpenAPI().components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
            .type(SecurityScheme.Type.HTTP).scheme("bearer")
            .description("Access token returned by /v1/auth/login or /v1/auth/register")))
            .info(new Info()
            .title("Echo Reviews API")
            .version("v2")
            .description("Echo API: movie details with versioned five-parameter ratings and moderated reviews; legacy v1 endpoints remain available."));
    }
}
