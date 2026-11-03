package org.example.echo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.example.echo.security.ApiAccessDeniedHandler;
import org.example.echo.security.ApiAuthenticationEntryPoint;
import org.example.echo.security.AuthRateLimitFilter;
import org.example.echo.security.JsonRequestSizeLimitFilter;
import org.example.echo.security.SessionAuthenticationFilter;

@EnableMethodSecurity
@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        @Value("${spring.h2.console.enabled:false}") boolean h2ConsoleEnabled,
        SessionAuthenticationFilter sessionAuthenticationFilter,
        AuthRateLimitFilter authRateLimitFilter,
        JsonRequestSizeLimitFilter jsonRequestSizeLimitFilter,
        ApiAuthenticationEntryPoint apiAuthenticationEntryPoint,
        ApiAccessDeniedHandler apiAccessDeniedHandler
    ) throws Exception {
        http
            .cors(Customizer.withDefaults())
            // The API is authenticated with explicit bearer tokens, not browser sessions.
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .headers(headers -> {
                headers.referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER));
                if (h2ConsoleEnabled) {
                    headers.frameOptions(frame -> frame.sameOrigin());
                }
            })
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(apiAuthenticationEntryPoint)
                .accessDeniedHandler(apiAccessDeniedHandler)
            )
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                auth.requestMatchers("/error").permitAll();
                auth.requestMatchers(
                    "/v1/auth/register",
                    "/v1/auth/login",
                    "/v1/auth/refresh",
                    "/v1/auth/email-verification/request",
                    "/v1/auth/email-verification/confirm",
                    "/v1/auth/password-recovery/request",
                    "/v1/auth/password-recovery/confirm"
                ).permitAll();
                auth.requestMatchers(HttpMethod.GET, "/v1/media/**").permitAll();
                auth.requestMatchers(HttpMethod.GET, "/v1/reviews", "/v1/reviews/*", "/v1/reviews/*/answers", "/v1/users/**").permitAll();
                auth.requestMatchers("/v2/admin/**").hasRole("ADMIN");
                auth.requestMatchers("/v2/reviews/moderation", "/v2/reviews/*/publish").hasRole("ADMIN");
                auth.requestMatchers("/v2/reviews/mine").authenticated();
                auth.requestMatchers(HttpMethod.GET,
                    "/v2/rating-schemes/**", "/v2/users/*/ratings", "/v2/media/*/*/ratings",
                    "/v2/media/movie/*/details",
                    "/v2/reviews", "/v2/reviews/*"
                ).permitAll();
                auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/actuator/**").hasRole("ADMIN");
                if (h2ConsoleEnabled) {
                    auth.requestMatchers("/h2-console/**").permitAll();
                }
                auth.anyRequest().authenticated();
            });

        http.addFilterBefore(sessionAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(authRateLimitFilter, SessionAuthenticationFilter.class);
        http.addFilterBefore(jsonRequestSizeLimitFilter, AuthRateLimitFilter.class);

        return http.build();
    }
}
