package org.example.echo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "sessions")
public class SessionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private UserEntity user;

    @Column(name = "access_token_hash", nullable = false, unique = true, length = 64)
    private String accessTokenHash;

    @Column(name = "refresh_token_hash", nullable = false, unique = true, length = 64)
    private String refreshTokenHash;

    @Column(name = "token_family_id", nullable = false)
    private UUID tokenFamilyId;

    @Column(name = "rotated_from_session_id")
    private UUID rotatedFromSessionId;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "access_expires_at")
    private OffsetDateTime accessExpiresAt;

    @Column(name = "refresh_expires_at")
    private OffsetDateTime refreshExpiresAt;

    @Column(name = "deactivated_at")
    private OffsetDateTime deactivatedAt;

    @Column(name = "refresh_used_at")
    private OffsetDateTime refreshUsedAt;

    @Column(name = "compromised_at")
    private OffsetDateTime compromisedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        if (isActive == null) {
            isActive = true;
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UserEntity getUser() {
        return user;
    }

    public void setUser(UserEntity user) {
        this.user = user;
    }

    public String getAccessTokenHash() {
        return accessTokenHash;
    }

    public void setAccessTokenHash(String accessTokenHash) {
        this.accessTokenHash = accessTokenHash;
    }

    public String getRefreshTokenHash() {
        return refreshTokenHash;
    }

    public void setRefreshTokenHash(String refreshTokenHash) {
        this.refreshTokenHash = refreshTokenHash;
    }

    public UUID getTokenFamilyId() {
        return tokenFamilyId;
    }

    public void setTokenFamilyId(UUID tokenFamilyId) {
        this.tokenFamilyId = tokenFamilyId;
    }

    public UUID getRotatedFromSessionId() {
        return rotatedFromSessionId;
    }

    public void setRotatedFromSessionId(UUID rotatedFromSessionId) {
        this.rotatedFromSessionId = rotatedFromSessionId;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }

    public OffsetDateTime getAccessExpiresAt() {
        return accessExpiresAt;
    }

    public void setAccessExpiresAt(OffsetDateTime accessExpiresAt) {
        this.accessExpiresAt = accessExpiresAt;
    }

    public OffsetDateTime getRefreshExpiresAt() {
        return refreshExpiresAt;
    }

    public void setRefreshExpiresAt(OffsetDateTime refreshExpiresAt) {
        this.refreshExpiresAt = refreshExpiresAt;
    }

    public OffsetDateTime getDeactivatedAt() {
        return deactivatedAt;
    }

    public void setDeactivatedAt(OffsetDateTime deactivatedAt) {
        this.deactivatedAt = deactivatedAt;
    }

    public OffsetDateTime getRefreshUsedAt() {
        return refreshUsedAt;
    }

    public void setRefreshUsedAt(OffsetDateTime refreshUsedAt) {
        this.refreshUsedAt = refreshUsedAt;
    }

    public OffsetDateTime getCompromisedAt() {
        return compromisedAt;
    }

    public void setCompromisedAt(OffsetDateTime compromisedAt) {
        this.compromisedAt = compromisedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
