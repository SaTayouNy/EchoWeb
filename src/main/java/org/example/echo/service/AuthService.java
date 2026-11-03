package org.example.echo.service;

import org.example.echo.entity.Role;
import org.example.echo.entity.EmailVerificationTokenEntity;
import org.example.echo.entity.PasswordRecoveryTokenEntity;
import org.example.echo.entity.SessionEntity;
import org.example.echo.entity.UserEntity;
import org.example.echo.exception.ApiException;
import org.example.echo.mail.NotificationEmailService;
import org.example.echo.model.ApiModels;
import org.example.echo.security.AuthenticatedUserPrincipal;
import org.example.echo.service.SessionTokenService.SessionTokenClaims;
import org.example.echo.repository.EmailVerificationTokenRepository;
import org.example.echo.repository.PasswordRecoveryTokenRepository;
import org.example.echo.repository.SessionRepository;
import org.example.echo.repository.UserRepository;
import org.example.echo.util.StringNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordRecoveryTokenRepository passwordRecoveryTokenRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final SessionTokenService sessionTokenService;
    private final NotificationEmailService notificationEmailService;
    private final OpaqueTokenService opaqueTokenService;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;
    private final Duration emailVerificationTtl;
    private final Duration passwordRecoveryTtl;
    private final int maxActiveSessionsPerUser;
    private final String dummyPasswordHash;
    private final TransactionTemplate transactionTemplate;

    public AuthService(
        UserRepository userRepository,
        SessionRepository sessionRepository,
        EmailVerificationTokenRepository emailVerificationTokenRepository,
        PasswordRecoveryTokenRepository passwordRecoveryTokenRepository,
        BCryptPasswordEncoder passwordEncoder,
        SessionTokenService sessionTokenService,
        NotificationEmailService notificationEmailService,
        OpaqueTokenService opaqueTokenService,
        PlatformTransactionManager transactionManager,
        @Value("${app.auth.session.access-ttl:PT30M}") Duration accessTokenTtl,
        @Value("${app.auth.session.refresh-ttl:P30D}") Duration refreshTokenTtl,
        @Value("${app.auth.email-verification.ttl:PT1H}") Duration emailVerificationTtl,
        @Value("${app.auth.password-recovery.ttl:PT30M}") Duration passwordRecoveryTtl,
        @Value("${app.auth.session.max-active-per-user:20}") int maxActiveSessionsPerUser
    ) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.passwordRecoveryTokenRepository = passwordRecoveryTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionTokenService = sessionTokenService;
        this.notificationEmailService = notificationEmailService;
        this.opaqueTokenService = opaqueTokenService;
        this.accessTokenTtl = accessTokenTtl;
        this.refreshTokenTtl = refreshTokenTtl;
        this.emailVerificationTtl = emailVerificationTtl;
        this.passwordRecoveryTtl = passwordRecoveryTtl;
        this.maxActiveSessionsPerUser = maxActiveSessionsPerUser;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing-equalization");
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public ApiModels.RegisterResponse register(ApiModels.RegisterRequest request) {
        String normalizedEmail = normalizeOptionalEmail(request.email());
        String normalizedUsername = normalizeUsername(request.username());
        String passwordHash = passwordEncoder.encode(request.password());

        UserEntity existingByEmail = normalizedEmail == null ? null : userRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);
        if (existingByEmail != null) {
            handleDuplicateEmailRegistration(existingByEmail.getUserId());
            return duplicateRegisterResponse(normalizedEmail, normalizedUsername);
        }
        if (userRepository.findByUsernameNormalized(normalizedUsername.toLowerCase(Locale.ROOT)).isPresent()) {
            throw usernameTaken();
        }

        UserEntity user = new UserEntity();
        user.setEmail(normalizedEmail);
        user.setUsername(normalizedUsername);
        user.setPasswordHash(passwordHash);
        user.setRole(Role.USER);
        user.setIsActive(true);
        user.setEmailVerified(normalizedEmail == null);
        try {
            return Objects.requireNonNull(transactionTemplate.execute(status -> {
                userRepository.saveAndFlush(user);
                if (normalizedEmail == null) {
                    ApiModels.AuthTokensResponse tokens = issueTokens(user, nowUtc());
                    return new ApiModels.RegisterResponse(ApiModels.toUserResponse(user), tokens, false);
                }
                issueEmailVerification(user);
                return new ApiModels.RegisterResponse(ApiModels.toUserResponse(user), null, true);
            }));
        } catch (DataIntegrityViolationException ex) {
            return resolveRegistrationConflict(normalizedEmail, normalizedUsername, ex);
        }
    }

    @Transactional
    public ApiModels.AuthTokensResponse login(ApiModels.LoginRequest request) {
        UserEntity user = findLoginUser(request);
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            throw invalidCredentials();
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw inactiveUser();
        }
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            throw emailNotVerified();
        }

        return issueTokens(user, nowUtc());
    }

    @Transactional(noRollbackFor = ApiException.class)
    public ApiModels.AuthTokensResponse refresh(String refreshToken) {
        OffsetDateTime now = nowUtc();
        SessionLookup lookup = lookupRefreshSession(refreshToken);
        SessionEntity existingSession = lookup.session();

        ensureActiveUser(existingSession);
        ensureVerifiedUser(existingSession);
        ensureSessionFamilyIsNotCompromised(existingSession);

        if (existingSession.getRefreshUsedAt() != null) {
            compromiseSessionFamily(existingSession.getTokenFamilyId(), now);
            throw invalidSession();
        }
        if (!Boolean.TRUE.equals(existingSession.getIsActive())) {
            compromiseSessionFamily(existingSession.getTokenFamilyId(), now);
            throw invalidSession();
        }
        if (isExpired(lookup.claims().expiresAt(), now) || isExpired(existingSession.getRefreshExpiresAt(), now)) {
            deactivateIfActive(existingSession, now);
            throw invalidSession();
        }

        // The old session deactivation and the rotated session creation below are committed in the same transaction.
        existingSession.setRefreshUsedAt(now);
        deactivateSession(existingSession, now);
        sessionRepository.save(existingSession);
        return issueTokens(existingSession.getUser(), now, existingSession.getTokenFamilyId(), existingSession.getId());
    }

    @Transactional
    public void requestEmailVerification(ApiModels.EmailVerificationRequest request) {
        UserEntity user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
            .orElse(null);
        if (user == null || !Boolean.TRUE.equals(user.getIsActive()) || Boolean.TRUE.equals(user.getEmailVerified())) {
            return;
        }

        issueEmailVerification(user);
    }

    @Transactional
    public ApiModels.AuthTokensResponse confirmEmailVerification(ApiModels.EmailVerificationConfirmRequest request) {
        OffsetDateTime now = nowUtc();
        EmailVerificationTokenEntity token = emailVerificationTokenRepository.findByTokenHash(opaqueTokenService.hashToken(request.token()))
            .orElseThrow(this::invalidEmailVerificationToken);

        if (isExpired(token.getExpiresAt(), now)) {
            emailVerificationTokenRepository.delete(token);
            throw invalidEmailVerificationToken();
        }

        UserEntity user = token.getUser();
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw inactiveUser();
        }

        user.setEmailVerified(true);
        userRepository.save(user);
        emailVerificationTokenRepository.deleteByUserUserId(user.getUserId());
        return issueTokens(user, now);
    }

    @Transactional
    public void requestPasswordRecovery(ApiModels.PasswordRecoveryRequest request) {
        UserEntity user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
            .orElse(null);
        if (user == null || !Boolean.TRUE.equals(user.getIsActive()) || !Boolean.TRUE.equals(user.getEmailVerified())) {
            return;
        }

        OffsetDateTime expiresAt = nowUtc().plus(passwordRecoveryTtl);
        String recoveryToken = opaqueTokenService.generateToken();

        passwordRecoveryTokenRepository.deleteByUserUserId(user.getUserId());

        PasswordRecoveryTokenEntity token = new PasswordRecoveryTokenEntity();
        token.setUser(user);
        token.setTokenHash(opaqueTokenService.hashToken(recoveryToken));
        token.setExpiresAt(expiresAt);
        passwordRecoveryTokenRepository.save(token);

        notificationEmailService.sendPasswordRecovery(user, recoveryToken, expiresAt);
    }

    @Transactional
    public void confirmPasswordRecovery(ApiModels.PasswordRecoveryConfirmRequest request) {
        OffsetDateTime now = nowUtc();
        PasswordRecoveryTokenEntity token = passwordRecoveryTokenRepository.findByTokenHash(opaqueTokenService.hashToken(request.token()))
            .orElseThrow(this::invalidPasswordRecoveryToken);

        if (isExpired(token.getExpiresAt(), now)) {
            passwordRecoveryTokenRepository.delete(token);
            throw invalidPasswordRecoveryToken();
        }

        UserEntity user = token.getUser();
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw inactiveUser();
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        passwordRecoveryTokenRepository.deleteByUserUserId(user.getUserId());
        deactivateAllSessions(user, now);
    }

    @Transactional(readOnly = true)
    public AuthenticatedUserPrincipal authenticateAccessToken(String accessToken) {
        return toPrincipal(requireAccessSession(accessToken, nowUtc()));
    }

    @Transactional(readOnly = true)
    public AuthenticatedUserPrincipal authenticateLogoutAccessToken(String accessToken) {
        return toPrincipal(findSessionForLogout(accessToken));
    }

    @Transactional(readOnly = true)
    public UserEntity requireUser(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(this::invalidSession);
    }

    @Transactional
    public void logoutCurrentSession(UUID sessionId) {
        SessionEntity session = sessionRepository.findById(sessionId)
            .orElseThrow(this::invalidSession);
        deactivateIfActive(session, nowUtc());
    }

    public void checkOwnership(AuthenticatedUserPrincipal actor, UUID ownerId) {
        if (actor.role() == Role.USER && !actor.userId().equals(ownerId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this action");
        }
        if (actor.role() == Role.ADMIN && !actor.userId().equals(ownerId)) {
            log.info("Admin {} accessed resource owned by {}", actor.userId(), ownerId);
        }
    }

    private ApiModels.AuthTokensResponse issueTokens(UserEntity user, OffsetDateTime now) {
        return issueTokens(user, now, UUID.randomUUID(), null);
    }

    private ApiModels.AuthTokensResponse issueTokens(
        UserEntity user,
        OffsetDateTime now,
        UUID tokenFamilyId,
        UUID rotatedFromSessionId
    ) {
        GeneratedTokens generatedTokens = generateTokens(now);
        enforceActiveSessionLimit(user, now);

        SessionEntity session = new SessionEntity();
        session.setUser(user);
        session.setTokenFamilyId(tokenFamilyId);
        session.setRotatedFromSessionId(rotatedFromSessionId);
        session.setIsActive(true);
        session.setDeactivatedAt(null);
        session.setRefreshUsedAt(null);
        session.setCompromisedAt(null);
        session.setAccessExpiresAt(generatedTokens.accessExpiresAt());
        session.setRefreshExpiresAt(generatedTokens.refreshExpiresAt());
        session.setAccessTokenHash(generatedTokens.accessTokenHash());
        session.setRefreshTokenHash(generatedTokens.refreshTokenHash());
        sessionRepository.save(session);

        return new ApiModels.AuthTokensResponse(
            sessionTokenService.authorizationScheme(),
            generatedTokens.accessToken(),
            generatedTokens.refreshToken()
        );
    }

    private ApiModels.RegisterResponse duplicateRegisterResponse(String normalizedEmail, String normalizedUsername) {
        OffsetDateTime now = nowUtc();
        return new ApiModels.RegisterResponse(
            new ApiModels.UserResponse(
                UUID.randomUUID(),
                normalizedEmail,
                normalizedUsername,
                null,
                null,
                Role.USER.name(),
                true,
                now,
                now
            ),
            null,
            normalizedEmail != null
        );
    }

    private GeneratedTokens generateTokens(OffsetDateTime now) {
        OffsetDateTime accessExpiresAt = now.plus(accessTokenTtl);
        String accessToken = sessionTokenService.createAccessToken(accessExpiresAt);
        OffsetDateTime refreshExpiresAt = now.plus(refreshTokenTtl);
        String refreshToken = sessionTokenService.createRefreshToken(refreshExpiresAt);

        return new GeneratedTokens(
            accessToken,
            sessionTokenService.hashToken(accessToken),
            refreshToken,
            sessionTokenService.hashToken(refreshToken),
            accessExpiresAt,
            refreshExpiresAt
        );
    }

    private SessionEntity requireAccessSession(String accessToken, OffsetDateTime now) {
        SessionTokenClaims claims = sessionTokenService.verifyAccessToken(accessToken);
        SessionEntity session = sessionRepository.findByAccessTokenHash(sessionTokenService.hashToken(accessToken))
            .orElseThrow(this::invalidAccessToken);

        ensureActiveUser(session);
        ensureVerifiedUser(session);
        ensureSessionFamilyIsNotCompromised(session);
        ensureSessionIsActive(session);
        if (isExpired(claims.expiresAt(), now) || isExpired(session.getAccessExpiresAt(), now) || isExpired(session.getRefreshExpiresAt(), now)) {
            throw invalidSession();
        }

        return session;
    }

    private SessionLookup lookupRefreshSession(String refreshToken) {
        SessionTokenClaims claims = sessionTokenService.verifyRefreshToken(refreshToken);
        String refreshTokenHash = sessionTokenService.hashToken(refreshToken);
        UUID tokenFamilyId = sessionRepository.findTokenFamilyIdByRefreshTokenHash(refreshTokenHash)
            .orElseThrow(this::invalidRefreshToken);
        List<SessionEntity> familySessions = lockSessionFamily(tokenFamilyId);
        SessionEntity session = familySessions.stream()
            .filter(candidate -> refreshTokenHash.equals(candidate.getRefreshTokenHash()))
            .findFirst()
            .orElseThrow(this::invalidRefreshToken);

        return new SessionLookup(session, claims);
    }

    private SessionEntity findSessionForLogout(String accessToken) {
        sessionTokenService.verifyAccessToken(accessToken);
        SessionEntity session = sessionRepository.findByAccessTokenHash(sessionTokenService.hashToken(accessToken))
            .orElseThrow(this::invalidAccessToken);
        // Logout keeps relaxed expiry checks so a client can explicitly invalidate an almost-expired session.
        ensureActiveUser(session);
        ensureVerifiedUser(session);
        ensureSessionFamilyIsNotCompromised(session);
        return session;
    }

    private void ensureActiveUser(SessionEntity session) {
        if (!Boolean.TRUE.equals(session.getUser().getIsActive())) {
            throw inactiveUser();
        }
    }

    private void ensureVerifiedUser(SessionEntity session) {
        if (!Boolean.TRUE.equals(session.getUser().getEmailVerified())) {
            throw emailNotVerified();
        }
    }

    private void ensureSessionIsActive(SessionEntity session) {
        if (!Boolean.TRUE.equals(session.getIsActive())) {
            throw invalidSession();
        }
    }

    private void ensureSessionFamilyIsNotCompromised(SessionEntity session) {
        if (session.getCompromisedAt() != null) {
            throw invalidSession();
        }
    }

    private void deactivateIfActive(SessionEntity session, OffsetDateTime now) {
        if (!Boolean.TRUE.equals(session.getIsActive())) {
            return;
        }
        deactivateSession(session, now);
        sessionRepository.save(session);
    }

    private void deactivateSession(SessionEntity session, OffsetDateTime now) {
        session.setIsActive(false);
        session.setDeactivatedAt(now);
    }

    private void compromiseSessionFamily(UUID tokenFamilyId, OffsetDateTime now) {
        List<SessionEntity> familySessions = lockSessionFamily(tokenFamilyId);
        for (SessionEntity familySession : familySessions) {
            familySession.setCompromisedAt(now);
            if (Boolean.TRUE.equals(familySession.getIsActive())) {
                deactivateSession(familySession, now);
            }
        }
        sessionRepository.saveAll(familySessions);
    }

    private List<SessionEntity> lockSessionFamily(UUID tokenFamilyId) {
        return sessionRepository.findAllByTokenFamilyIdForUpdate(tokenFamilyId);
    }

    private void deactivateAllSessions(UserEntity user, OffsetDateTime now) {
        List<SessionEntity> sessions = sessionRepository.findAllByUserUserIdAndIsActiveTrue(user.getUserId());
        for (SessionEntity session : sessions) {
            deactivateSession(session, now);
        }
        sessionRepository.saveAll(sessions);
    }

    private void enforceActiveSessionLimit(UserEntity user, OffsetDateTime now) {
        if (maxActiveSessionsPerUser <= 0) {
            return;
        }

        List<SessionEntity> activeSessions = sessionRepository.findAllByUserUserIdAndIsActiveTrueOrderByCreatedAtAsc(user.getUserId());
        int sessionsToDeactivate = activeSessions.size() - maxActiveSessionsPerUser + 1;
        if (sessionsToDeactivate <= 0) {
            return;
        }

        for (int i = 0; i < sessionsToDeactivate; i++) {
            deactivateSession(activeSessions.get(i), now);
        }
        sessionRepository.saveAll(activeSessions.subList(0, sessionsToDeactivate));
    }

    private AuthenticatedUserPrincipal toPrincipal(SessionEntity session) {
        UserEntity user = session.getUser();
        return new AuthenticatedUserPrincipal(
            user.getUserId(),
            session.getId(),
            user.getEmail(),
            user.getUsername(),
            user.getRole()
        );
    }

    private boolean isExpired(OffsetDateTime expiresAt, OffsetDateTime now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }

    private void issueEmailVerification(UserEntity user) {
        OffsetDateTime expiresAt = nowUtc().plus(emailVerificationTtl);
        String verificationToken = opaqueTokenService.generateToken();

        emailVerificationTokenRepository.deleteByUserUserId(user.getUserId());

        EmailVerificationTokenEntity token = new EmailVerificationTokenEntity();
        token.setUser(user);
        token.setTokenHash(opaqueTokenService.hashToken(verificationToken));
        token.setExpiresAt(expiresAt);
        emailVerificationTokenRepository.save(token);

        notificationEmailService.sendEmailVerification(user, verificationToken, expiresAt);
    }

    private ApiModels.RegisterResponse resolveRegistrationConflict(
        String normalizedEmail,
        String normalizedUsername,
        DataIntegrityViolationException ex
    ) {
        UserEntity existingByEmail = normalizedEmail == null ? null : userRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);
        if (existingByEmail != null) {
            handleDuplicateEmailRegistration(existingByEmail.getUserId());
            return duplicateRegisterResponse(normalizedEmail, normalizedUsername);
        }
        if (userRepository.findByUsernameNormalized(normalizedUsername.toLowerCase(Locale.ROOT)).isPresent()) {
            throw usernameTaken();
        }
        throw ex;
    }

    private void handleDuplicateEmailRegistration(UUID userId) {
        transactionTemplate.executeWithoutResult(status -> userRepository.findById(userId)
            .ifPresent(this::handleDuplicateEmailRegistrationInTransaction));
    }

    private void handleDuplicateEmailRegistrationInTransaction(UserEntity existingUser) {
        if (!Boolean.TRUE.equals(existingUser.getIsActive())) {
            return;
        }
        if (Boolean.TRUE.equals(existingUser.getEmailVerified())) {
            notificationEmailService.sendDuplicateRegistrationNotice(existingUser);
            return;
        }
        issueEmailVerification(existingUser);
    }

    private OffsetDateTime nowUtc() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private String normalizeEmail(String email) {
        // Email normalization must be locale-independent to avoid Turkish-I style case-folding bugs.
        return StringNormalizer.trim(email).toLowerCase(Locale.ROOT);
    }

    private String normalizeOptionalEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return normalizeEmail(email);
    }

    private UserEntity findLoginUser(ApiModels.LoginRequest request) {
        String normalizedUsername = request.username() == null ? "" : normalizeUsername(request.username());
        if (!normalizedUsername.isBlank()) {
            return userRepository.findByUsernameNormalized(normalizedUsername.toLowerCase(Locale.ROOT)).orElse(null);
        }
        if (request.email() == null || request.email().isBlank()) {
            return null;
        }
        return userRepository.findByEmailIgnoreCase(normalizeEmail(request.email())).orElse(null);
    }

    private String normalizeUsername(String username) {
        return StringNormalizer.trim(username);
    }

    private ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid credentials");
    }

    private ApiException usernameTaken() {
        return new ApiException(HttpStatus.CONFLICT, "USERNAME_TAKEN", "Username is already taken",
            List.of(new ApiModels.ErrorDetail("username", "is already taken")));
    }

    private ApiException inactiveUser() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "User is inactive");
    }

    private ApiException emailNotVerified() {
        return new ApiException(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED", "Email is not verified");
    }

    private ApiException invalidSession() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid session");
    }

    private ApiException invalidAccessToken() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid token");
    }

    private ApiException invalidRefreshToken() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Invalid refresh token");
    }

    private ApiException invalidEmailVerificationToken() {
        return new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Invalid or expired email verification token");
    }

    private ApiException invalidPasswordRecoveryToken() {
        return new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Invalid or expired password recovery token");
    }

    private record GeneratedTokens(
        String accessToken,
        String accessTokenHash,
        String refreshToken,
        String refreshTokenHash,
        OffsetDateTime accessExpiresAt,
        OffsetDateTime refreshExpiresAt
    ) {
    }

    private record SessionLookup(SessionEntity session, SessionTokenClaims claims) {
    }
}
