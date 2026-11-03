package org.example.echo.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.echo.config.AuthRateLimitProperties;
import org.example.echo.exception.ApiException;
import org.example.echo.util.IpAddressUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private final List<RateLimitRule> rules;
    private final Set<String> trustedProxies;
    private final FixedWindowRateLimiter fixedWindowRateLimiter;
    private final HandlerExceptionResolver handlerExceptionResolver;

    public AuthRateLimitFilter(
        AuthRateLimitProperties authRateLimitProperties,
        FixedWindowRateLimiter fixedWindowRateLimiter,
        @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver
    ) {
        this.rules = List.of(
            new RateLimitRule(new AntPathRequestMatcher("/v1/auth/login", "POST"), "login", authRateLimitProperties.getLogin()),
            new RateLimitRule(new AntPathRequestMatcher("/v1/auth/register", "POST"), "register", authRateLimitProperties.getRegister()),
            new RateLimitRule(
                new AntPathRequestMatcher("/v1/auth/email-verification/request", "POST"),
                "email-verification-request",
                authRateLimitProperties.getEmailVerificationRequest()
            ),
            new RateLimitRule(
                new AntPathRequestMatcher("/v1/auth/email-verification/confirm", "POST"),
                "email-verification-confirm",
                authRateLimitProperties.getEmailVerificationConfirm()
            ),
            new RateLimitRule(
                new AntPathRequestMatcher("/v1/auth/password-recovery/request", "POST"),
                "password-recovery-request",
                authRateLimitProperties.getPasswordRecoveryRequest()
            ),
            new RateLimitRule(
                new AntPathRequestMatcher("/v1/auth/password-recovery/confirm", "POST"),
                "password-recovery-confirm",
                authRateLimitProperties.getPasswordRecoveryConfirm()
            ),
            new RateLimitRule(
                new AntPathRequestMatcher("/v1/media/search/movies", "GET"),
                "media-search",
                authRateLimitProperties.getMediaSearch()
            ),
            new RateLimitRule(
                new AntPathRequestMatcher("/v1/media/search/tv", "GET"),
                "media-search",
                authRateLimitProperties.getMediaSearch()
            ),
            new RateLimitRule(
                new AntPathRequestMatcher("/v1/media/discover/**", "GET"),
                "media-search",
                authRateLimitProperties.getMediaSearch()
            ),
            // Keep recommendation routes ahead of generic media lookup rules.
            new RateLimitRule(
                new AntPathRequestMatcher("/v1/media/movie/*/recommendations", "GET"),
                "media-search",
                authRateLimitProperties.getMediaSearch()
            ),
            new RateLimitRule(
                new AntPathRequestMatcher("/v1/media/tv/*/recommendations", "GET"),
                "media-search",
                authRateLimitProperties.getMediaSearch()
            ),
            new RateLimitRule(
                new AntPathRequestMatcher("/v1/media/movie/*", "GET"),
                "media-lookup",
                authRateLimitProperties.getMediaLookup()
            ),
            new RateLimitRule(
                new AntPathRequestMatcher("/v1/media/tv/*", "GET"),
                "media-lookup",
                authRateLimitProperties.getMediaLookup()
            )
        );
        this.trustedProxies = Set.copyOf(authRateLimitProperties.getTrustedProxies());
        this.fixedWindowRateLimiter = fixedWindowRateLimiter;
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        for (RateLimitRule rule : rules) {
            if (!rule.matcher.matches(request)) {
                continue;
            }

            String clientKey = rule.keyPrefix + ":" + resolveClientKey(request);
            boolean allowed = fixedWindowRateLimiter.tryConsume(
                clientKey,
                rule.endpointLimit.getCapacity(),
                rule.endpointLimit.getWindow()
            );
            if (!allowed) {
                handlerExceptionResolver.resolveException(
                    request,
                    response,
                    null,
                    new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Too many requests")
                );
                return;
            }
            break;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveClientKey(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (!trustedProxies.contains(remoteAddr)) {
            return remoteAddr;
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            int commaIndex = forwardedFor.indexOf(',');
            String forwardedClient = commaIndex >= 0 ? forwardedFor.substring(0, commaIndex).trim() : forwardedFor.trim();
            if (IpAddressUtils.isIpLiteral(forwardedClient)) {
                return forwardedClient;
            }
        }
        return remoteAddr;
    }

    private record RateLimitRule(
        RequestMatcher matcher,
        String keyPrefix,
        AuthRateLimitProperties.EndpointLimit endpointLimit
    ) {
    }
}
