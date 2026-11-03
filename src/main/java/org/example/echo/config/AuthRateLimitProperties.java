package org.example.echo.config;

import jakarta.annotation.PostConstruct;
import org.example.echo.util.IpAddressUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

@Component
@Validated
@ConfigurationProperties(prefix = "app.security.rate-limit")
public class AuthRateLimitProperties {
    private EndpointLimit login = new EndpointLimit(10, Duration.ofMinutes(1));
    private EndpointLimit register = new EndpointLimit(10, Duration.ofMinutes(5));
    private EndpointLimit emailVerificationRequest = new EndpointLimit(5, Duration.ofMinutes(15));
    private EndpointLimit emailVerificationConfirm = new EndpointLimit(10, Duration.ofMinutes(15));
    private EndpointLimit passwordRecoveryRequest = new EndpointLimit(5, Duration.ofMinutes(15));
    private EndpointLimit passwordRecoveryConfirm = new EndpointLimit(10, Duration.ofMinutes(15));
    private EndpointLimit mediaLookup = new EndpointLimit(30, Duration.ofMinutes(1));
    private EndpointLimit mediaSearch = new EndpointLimit(20, Duration.ofMinutes(1));
    private Set<String> trustedProxies = new HashSet<>();

    public EndpointLimit getLogin() {
        return login;
    }

    public void setLogin(EndpointLimit login) {
        this.login = login;
    }

    public EndpointLimit getRegister() {
        return register;
    }

    public void setRegister(EndpointLimit register) {
        this.register = register;
    }

    public EndpointLimit getEmailVerificationRequest() {
        return emailVerificationRequest;
    }

    public void setEmailVerificationRequest(EndpointLimit emailVerificationRequest) {
        this.emailVerificationRequest = emailVerificationRequest;
    }

    public EndpointLimit getEmailVerificationConfirm() {
        return emailVerificationConfirm;
    }

    public void setEmailVerificationConfirm(EndpointLimit emailVerificationConfirm) {
        this.emailVerificationConfirm = emailVerificationConfirm;
    }

    public EndpointLimit getPasswordRecoveryRequest() {
        return passwordRecoveryRequest;
    }

    public void setPasswordRecoveryRequest(EndpointLimit passwordRecoveryRequest) {
        this.passwordRecoveryRequest = passwordRecoveryRequest;
    }

    public EndpointLimit getPasswordRecoveryConfirm() {
        return passwordRecoveryConfirm;
    }

    public void setPasswordRecoveryConfirm(EndpointLimit passwordRecoveryConfirm) {
        this.passwordRecoveryConfirm = passwordRecoveryConfirm;
    }

    public EndpointLimit getMediaLookup() {
        return mediaLookup;
    }

    public void setMediaLookup(EndpointLimit mediaLookup) {
        this.mediaLookup = mediaLookup;
    }

    public EndpointLimit getMediaSearch() {
        return mediaSearch;
    }

    public void setMediaSearch(EndpointLimit mediaSearch) {
        this.mediaSearch = mediaSearch;
    }

    public Set<String> getTrustedProxies() {
        return trustedProxies;
    }

    public void setTrustedProxies(Set<String> trustedProxies) {
        this.trustedProxies = trustedProxies;
    }

    @PostConstruct
    void validate() {
        validateEndpoint("login", login);
        validateEndpoint("register", register);
        validateEndpoint("emailVerificationRequest", emailVerificationRequest);
        validateEndpoint("emailVerificationConfirm", emailVerificationConfirm);
        validateEndpoint("passwordRecoveryRequest", passwordRecoveryRequest);
        validateEndpoint("passwordRecoveryConfirm", passwordRecoveryConfirm);
        validateEndpoint("mediaLookup", mediaLookup);
        validateEndpoint("mediaSearch", mediaSearch);

        for (String proxy : trustedProxies) {
            if (!IpAddressUtils.isIpLiteral(proxy)) {
                throw new IllegalStateException("Invalid app.security.rate-limit.trusted-proxies entry: " + proxy);
            }
        }
    }

    private void validateEndpoint(String name, EndpointLimit endpointLimit) {
        if (endpointLimit == null || endpointLimit.capacity <= 0 || endpointLimit.window == null
            || endpointLimit.window.isZero() || endpointLimit.window.isNegative()) {
            throw new IllegalStateException("Invalid app.security.rate-limit." + name + " configuration");
        }
    }

    public static class EndpointLimit {
        private int capacity;
        private Duration window;

        public EndpointLimit() {
        }

        public EndpointLimit(int capacity, Duration window) {
            this.capacity = capacity;
            this.window = window;
        }

        public int getCapacity() {
            return capacity;
        }

        public void setCapacity(int capacity) {
            this.capacity = capacity;
        }

        public Duration getWindow() {
            return window;
        }

        public void setWindow(Duration window) {
            this.window = window;
        }
    }
}
