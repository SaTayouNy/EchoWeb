package org.example.echo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
    name = "media",
    uniqueConstraints = @UniqueConstraint(name = "uk_media_type_tmdb_id", columnNames = {"media_type", "tmdb_id"}),
    indexes = {
        @Index(name = "idx_media_type_tmdb_id", columnList = "media_type,tmdb_id"),
        @Index(name = "idx_media_last_synced_at", columnList = "last_synced_at")
    }
)
public class MediaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "media_id")
    private UUID mediaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 16)
    private MediaKind mediaType;

    @Column(name = "tmdb_id", nullable = false)
    private Long tmdbId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false, length = 16)
    private MediaSyncStatus syncStatus = MediaSyncStatus.SYNCED;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "original_title", length = 255)
    private String originalTitle;

    @Column(columnDefinition = "TEXT")
    private String overview;

    @Column(name = "original_overview", columnDefinition = "TEXT")
    private String originalOverview;

    @Column(name = "release_year")
    private Integer releaseYear;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    @Column(name = "runtime_minutes")
    private Integer runtimeMinutes;

    @Column(name = "poster_path", length = 255)
    private String posterPath;

    @Column(name = "poster_url", length = 512)
    private String posterUrl;

    @Column(length = 255)
    private String director;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "cast_members", nullable = false, columnDefinition = "TEXT")
    private List<String> castMembers = List.of();

    @Convert(converter = MediaCreditListJsonConverter.class)
    @Column(name = "cast_details", nullable = false, columnDefinition = "TEXT")
    private List<MediaCredit> castDetails = List.of();

    @Convert(converter = MediaCreditListJsonConverter.class)
    @Column(name = "crew_details", nullable = false, columnDefinition = "TEXT")
    private List<MediaCredit> crewDetails = List.of();

    @Convert(converter = StringListJsonConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private List<String> genres = List.of();

    @Convert(converter = StringListJsonConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private List<String> countries = List.of();

    @Column(name = "last_synced_at", nullable = false)
    private OffsetDateTime lastSyncedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        createdAt = now;
        updatedAt = now;
        if (lastSyncedAt == null) {
            lastSyncedAt = now;
        }
        if (syncStatus == null) {
            syncStatus = MediaSyncStatus.SYNCED;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public UUID getMediaId() {
        return mediaId;
    }

    public void setMediaId(UUID mediaId) {
        this.mediaId = mediaId;
    }

    public MediaKind getMediaType() {
        return mediaType;
    }

    public void setMediaType(MediaKind mediaType) {
        this.mediaType = mediaType;
    }

    public Long getTmdbId() {
        return tmdbId;
    }

    public void setTmdbId(Long tmdbId) {
        this.tmdbId = tmdbId;
    }

    public MediaSyncStatus getSyncStatus() {
        return syncStatus;
    }

    public void setSyncStatus(MediaSyncStatus syncStatus) {
        this.syncStatus = syncStatus;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getOriginalTitle() {
        return originalTitle;
    }

    public void setOriginalTitle(String originalTitle) {
        this.originalTitle = originalTitle;
    }

    public String getOverview() {
        return overview;
    }

    public void setOverview(String overview) {
        this.overview = overview;
    }

    public String getOriginalOverview() {
        return originalOverview;
    }

    public void setOriginalOverview(String originalOverview) {
        this.originalOverview = originalOverview;
    }

    public Integer getReleaseYear() {
        return releaseYear;
    }

    public void setReleaseYear(Integer releaseYear) {
        this.releaseYear = releaseYear;
    }

    public LocalDate getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(LocalDate releaseDate) {
        this.releaseDate = releaseDate;
    }

    public Integer getRuntimeMinutes() {
        return runtimeMinutes;
    }

    public void setRuntimeMinutes(Integer runtimeMinutes) {
        this.runtimeMinutes = runtimeMinutes;
    }

    public String getPosterPath() {
        return posterPath;
    }

    public void setPosterPath(String posterPath) {
        this.posterPath = posterPath;
    }

    public String getPosterUrl() {
        return posterUrl;
    }

    public void setPosterUrl(String posterUrl) {
        this.posterUrl = posterUrl;
    }

    public String getDirector() {
        return director;
    }

    public void setDirector(String director) {
        this.director = director;
    }

    public List<String> getCastMembers() {
        return castMembers;
    }

    public void setCastMembers(List<String> castMembers) {
        this.castMembers = castMembers == null ? List.of() : List.copyOf(castMembers);
    }

    public List<MediaCredit> getCastDetails() {
        return castDetails;
    }

    public void setCastDetails(List<MediaCredit> castDetails) {
        this.castDetails = castDetails == null ? List.of() : List.copyOf(castDetails);
    }

    public List<MediaCredit> getCrewDetails() {
        return crewDetails;
    }

    public void setCrewDetails(List<MediaCredit> crewDetails) {
        this.crewDetails = crewDetails == null ? List.of() : List.copyOf(crewDetails);
    }

    public List<String> getGenres() {
        return genres;
    }

    public void setGenres(List<String> genres) {
        this.genres = genres == null ? List.of() : List.copyOf(genres);
    }

    public List<String> getCountries() {
        return countries;
    }

    public void setCountries(List<String> countries) {
        this.countries = countries == null ? List.of() : List.copyOf(countries);
    }

    public OffsetDateTime getLastSyncedAt() {
        return lastSyncedAt;
    }

    public void setLastSyncedAt(OffsetDateTime lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
