package org.example.echo.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.echo.exception.ApiException;
import org.example.echo.service.AuthService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

@Component
public class SessionAuthenticationFilter extends OncePerRequestFilter {
    private static final String AUTHORIZATION_SCHEME = "Bearer";

    private final AuthService authService;
    private final HandlerExceptionResolver handlerExceptionResolver;
    private final RequestMatcher logoutMatcher = new AntPathRequestMatcher("/v1/auth/logout", "POST");

    public SessionAuthenticationFilter(
        AuthService authService,
        @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver
    ) {
        this.authService = authService;
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || authHeader.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String accessToken = extractBearer(authHeader);
            AuthenticatedUserPrincipal principal = logoutMatcher.matches(request)
                ? authService.authenticateLogoutAccessToken(accessToken)
                : authService.authenticateAccessToken(accessToken);

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.authorities()
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (ApiException ex) {
            handlerExceptionResolver.resolveException(request, response, null, ex);
        }
    }

    private String extractBearer(String authHeader) {
        String schemePrefix = AUTHORIZATION_SCHEME + " ";
        if (!authHeader.regionMatches(true, 0, schemePrefix, 0, schemePrefix.length())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid Authorization header");
        }

        String token = authHeader.substring(schemePrefix.length()).trim();
        if (token.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid Authorization header");
        }
        return token;
    }
}
