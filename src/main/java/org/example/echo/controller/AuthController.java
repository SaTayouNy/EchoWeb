package org.example.echo.controller;

import jakarta.validation.Valid;
import org.example.echo.model.ApiModels;
import org.example.echo.security.AuthenticatedUserPrincipal;
import org.example.echo.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiModels.RegisterResponse register(@Valid @RequestBody ApiModels.RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public ApiModels.AuthTokensResponse login(@Valid @RequestBody ApiModels.LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public ApiModels.AuthTokensResponse refresh(@Valid @RequestBody ApiModels.RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/email-verification/request")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void requestEmailVerification(@Valid @RequestBody ApiModels.EmailVerificationRequest request) {
        authService.requestEmailVerification(request);
    }

    @PostMapping("/email-verification/confirm")
    public ApiModels.AuthTokensResponse confirmEmailVerification(@Valid @RequestBody ApiModels.EmailVerificationConfirmRequest request) {
        return authService.confirmEmailVerification(request);
    }

    @PostMapping("/password-recovery/request")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void requestPasswordRecovery(@Valid @RequestBody ApiModels.PasswordRecoveryRequest request) {
        authService.requestPasswordRecovery(request);
    }

    @PostMapping("/password-recovery/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmPasswordRecovery(@Valid @RequestBody ApiModels.PasswordRecoveryConfirmRequest request) {
        authService.confirmPasswordRecovery(request);
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal AuthenticatedUserPrincipal actor) {
        authService.logoutCurrentSession(actor.sessionId());
    }
}
