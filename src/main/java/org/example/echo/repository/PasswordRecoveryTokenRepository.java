package org.example.echo.repository;

import jakarta.persistence.LockModeType;
import org.example.echo.entity.PasswordRecoveryTokenEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface PasswordRecoveryTokenRepository extends JpaRepository<PasswordRecoveryTokenEntity, UUID> {
    @EntityGraph(attributePaths = {"user"})
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordRecoveryTokenEntity> findByTokenHash(String tokenHash);

    void deleteByUserUserId(UUID userId);
}
