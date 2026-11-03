package org.example.echo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.Map;
import java.util.LinkedHashMap;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.MapKeyColumn;

@Entity
@Table(
    name = "user_ratings",
    uniqueConstraints = @UniqueConstraint(name = "uk_user_ratings_user_media", columnNames = {"user_id", "media_type", "tmdb_id"}),
    indexes = @Index(name = "idx_user_ratings_user_updated_at", columnList = "user_id,updated_at")
)
public class UserRatingEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "rating_id")
    private UUID ratingId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private MediaEntity media;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 16)
    private MediaKind mediaType;

    @Column(name = "tmdb_id", nullable = false)
    private Long tmdbId;

    @Column(length = 255)
    private String title;

    @Column(name = "poster_path", length = 255)
    private String posterPath;

    @Column
    private Integer rating;

    @Column(name = "scheme_version", length = 64)
    private String schemeVersion;

    @Column(name = "final_score")
    private Integer finalScore;

    @ElementCollection
    @CollectionTable(name = "user_rating_values", joinColumns = @JoinColumn(name = "rating_id"))
    @MapKeyColumn(name = "parameter_code", length = 64)
    @Column(name = "raw_value", nullable = false)
    private Map<String, Integer> parameterValues = new LinkedHashMap<>();

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getRatingId() { return ratingId; }
    public UserEntity getUser() { return user; }
    public void setUser(UserEntity user) { this.user = user; }
    public MediaEntity getMedia() { return media; }
    public void setMedia(MediaEntity media) { this.media = media; }
    public MediaKind getMediaType() { return mediaType; }
    public void setMediaType(MediaKind mediaType) { this.mediaType = mediaType; }
    public Long getTmdbId() { return tmdbId; }
    public void setTmdbId(Long tmdbId) { this.tmdbId = tmdbId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getPosterPath() { return posterPath; }
    public void setPosterPath(String posterPath) { this.posterPath = posterPath; }
    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }
    public String getSchemeVersion() { return schemeVersion; }
    public void setSchemeVersion(String schemeVersion) { this.schemeVersion = schemeVersion; }
    public Integer getFinalScore() { return finalScore; }
    public void setFinalScore(Integer finalScore) { this.finalScore = finalScore; }
    public Map<String, Integer> getParameterValues() { return parameterValues; }
    public void setParameterValues(Map<String, Integer> values) {
        parameterValues.clear();
        parameterValues.putAll(values);
    }
    public void touch() { updatedAt = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS); }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
