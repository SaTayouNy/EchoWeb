package org.example.echo.service;

import org.example.echo.repository.MediaRepository;
import org.example.echo.repository.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ReviewMediaLinkService {
    private final MediaRepository mediaRepository;
    private final ReviewRepository reviewRepository;

    public ReviewMediaLinkService(MediaRepository mediaRepository, ReviewRepository reviewRepository) {
        this.mediaRepository = mediaRepository;
        this.reviewRepository = reviewRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void attachMediaAndBackfill(UUID mediaId) {
        var managedMedia = mediaRepository.findById(mediaId).orElseThrow();
        reviewRepository.attachMediaAndBackfillMetadata(
            managedMedia,
            managedMedia.getMediaType(),
            managedMedia.getTmdbId(),
            managedMedia.getTitle(),
            managedMedia.getReleaseDate(),
            managedMedia.getPosterPath()
        );
    }
}
