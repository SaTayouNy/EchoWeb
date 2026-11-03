package org.example.echo.service;

import org.example.echo.entity.MediaCredit;
import org.example.echo.entity.MediaEntity;
import org.example.echo.entity.MediaKind;
import org.example.echo.entity.PreferenceType;
import org.example.echo.entity.UserEntity;
import org.example.echo.entity.UserPreferenceEntity;
import org.example.echo.exception.ApiException;
import org.example.echo.model.ApiModels;
import org.example.echo.model.PreferenceModels;
import org.example.echo.repository.UserPreferenceRepository;
import org.example.echo.repository.UserRepository;
import org.example.echo.security.AuthenticatedUserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

@Service
public class UserPreferenceService {
    private final UserPreferenceRepository preferences;
    private final UserRepository users;
    private final TmdbMediaService mediaService;
    private final TransactionTemplate transactions;

    public UserPreferenceService(UserPreferenceRepository preferences, UserRepository users,
                                 TmdbMediaService mediaService, PlatformTransactionManager transactionManager) {
        this.preferences = preferences;
        this.users = users;
        this.mediaService = mediaService;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public PreferenceModels.ListPreferencesResponse list(AuthenticatedUserPrincipal actor, PreferenceType type,
                                                         int page, int size) {
        PageRequest paging = PageRequest.of(page, size,
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("preferenceId")));
        Page<UserPreferenceEntity> result = type == null
            ? preferences.findByUserUserId(actor.userId(), paging)
            : preferences.findByUserUserIdAndType(actor.userId(), type, paging);
        return new PreferenceModels.ListPreferencesResponse(
            result.getContent().stream().map(PreferenceModels::toResponse).toList(),
            new PreferenceModels.PageMeta(page, size, result.getTotalElements(), result.getTotalPages())
        );
    }

    @Transactional(readOnly = true)
    public PreferenceModels.PreferenceResponse get(AuthenticatedUserPrincipal actor, PreferenceType type, Long tmdbId) {
        return preferences.findByUserUserIdAndTypeAndTmdbId(actor.userId(), type, tmdbId)
            .map(PreferenceModels::toResponse)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PREFERENCE_NOT_FOUND", "Preference not found"));
    }

    public PreferenceModels.PreferenceResponse add(AuthenticatedUserPrincipal actor, PreferenceType type,
                                                    Long tmdbId, PreferenceModels.PersonSourceRequest source) {
        if (!type.isRelease() && source == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "Validation failed",
                List.of(new ApiModels.ErrorDetail("body", "sourceMediaType and sourceTmdbId are required for a person")));
        }
        var existing = preferences.findByUserUserIdAndTypeAndTmdbId(actor.userId(), type, tmdbId);
        if (existing.isPresent()) {
            return PreferenceModels.toResponse(existing.get());
        }

        // Resolve authoritative data before starting the write transaction or locking the user.
        MediaEntity media = type.isRelease()
            ? mediaService.getOrSync(MediaKind.valueOf(type.name()), tmdbId)
            : mediaService.getOrSync(source.sourceMediaType(), source.sourceTmdbId());
        MediaCredit person = type.isRelease() ? null : findPerson(media, type, tmdbId);

        return transactions.execute(status -> {
            UserEntity user = lockUser(actor.userId());
            // Serialize mutations per account, including simultaneous first inserts and deletes.
            return preferences.findByUserUserIdAndTypeAndTmdbId(actor.userId(), type, tmdbId)
                .map(PreferenceModels::toResponse)
                .orElseGet(() -> {
                    UserPreferenceEntity preference = new UserPreferenceEntity();
                    preference.setUser(user);
                    preference.setType(type);
                    preference.setTmdbId(tmdbId);
                    if (person == null) {
                        preference.setMedia(media);
                    } else {
                        preference.setPersonName(person.name());
                        preference.setProfilePath(person.profilePath());
                        preference.setProfileUrl(person.profileUrl());
                    }
                    return PreferenceModels.toResponse(preferences.saveAndFlush(preference));
                });
        });
    }

    @Transactional
    public void delete(AuthenticatedUserPrincipal actor, PreferenceType type, Long tmdbId) {
        lockUser(actor.userId());
        preferences.findByUserUserIdAndTypeAndTmdbId(actor.userId(), type, tmdbId).ifPresent(preferences::delete);
    }

    private UserEntity lockUser(UUID userId) {
        return users.findByIdForUpdate(userId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "User not found"));
    }

    private MediaCredit findPerson(MediaEntity media, PreferenceType type, Long tmdbId) {
        List<MediaCredit> credits = type == PreferenceType.ACTOR ? media.getCastDetails() : media.getCrewDetails();
        return credits.stream()
            .filter(credit -> tmdbId.equals(credit.tmdbPersonId()))
            .filter(credit -> type == PreferenceType.ACTOR || "Director".equalsIgnoreCase(credit.role()))
            .findFirst()
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PERSON_NOT_FOUND",
                "Person with the requested role was not found in the source release credits"));
    }
}
