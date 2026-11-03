package org.example.echo.config;

import org.example.echo.entity.MediaKind;
import org.example.echo.entity.PreferenceType;
import org.example.echo.model.ReviewSortField;
import org.example.echo.model.SortDirectionValue;
import org.example.echo.tmdb.TmdbTrendingWindow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final String[] allowedOrigins;

    public WebConfig(
        @Value("${app.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173,http://localhost:3000,http://127.0.0.1:3000}") String[] allowedOrigins
    ) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
            .allowedOrigins(allowedOrigins)
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            // Keep request headers explicit; wildcard headers make accidental credential/header exposure easier.
            .allowedHeaders("Authorization", "Content-Type", "Accept")
            .allowCredentials(false)
            .maxAge(3600);
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, MediaKind.class, MediaKind::from);
        registry.addConverter(String.class, PreferenceType.class, PreferenceType::from);
        registry.addConverter(String.class, TmdbTrendingWindow.class, TmdbTrendingWindow::from);
        registry.addConverter(String.class, ReviewSortField.class, ReviewSortField::from);
        registry.addConverter(String.class, SortDirectionValue.class, SortDirectionValue::from);
    }
}
