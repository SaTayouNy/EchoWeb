package org.example.echo.service;

import org.example.echo.entity.MediaKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

@Service
public class TmdbBackgroundSyncService {
    private static final Logger log = LoggerFactory.getLogger(TmdbBackgroundSyncService.class);

    private final TmdbMediaService tmdbMediaService;
    private final TaskExecutor tmdbSyncExecutor;
    private final boolean asyncEnabled;
    private final int maxInFlight;
    private final Semaphore inFlightPermits;
    private final Set<MediaKey> inFlight = ConcurrentHashMap.newKeySet();

    public TmdbBackgroundSyncService(
        TmdbMediaService tmdbMediaService,
        @Qualifier("tmdbSyncExecutor") TaskExecutor tmdbSyncExecutor,
        @Value("${app.tmdb.sync.async:true}") boolean asyncEnabled,
        @Value("${app.tmdb.sync.max-in-flight:1000}") int maxInFlight
    ) {
        this.tmdbMediaService = tmdbMediaService;
        this.tmdbSyncExecutor = tmdbSyncExecutor;
        this.asyncEnabled = asyncEnabled;
        this.maxInFlight = Math.max(1, maxInFlight);
        this.inFlightPermits = new Semaphore(this.maxInFlight);
        if (!asyncEnabled) {
            log.warn("TMDB background sync async mode is disabled; sync will run in the caller thread after commit");
        }
    }

    public void scheduleAfterCommit(MediaKind mediaType, Long tmdbId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
            || !TransactionSynchronizationManager.isSynchronizationActive()) {
            dispatch(mediaType, tmdbId);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatch(mediaType, tmdbId);
            }
        });
    }

    private void dispatch(MediaKind mediaType, Long tmdbId) {
        MediaKey key = new MediaKey(mediaType, tmdbId);
        if (inFlight.contains(key)) {
            return;
        }
        if (!inFlightPermits.tryAcquire()) {
            log.warn("Skipping TMDB background sync for {} {} because in-flight cap {} is reached", mediaType, tmdbId, maxInFlight);
            return;
        }
        if (!inFlight.add(key)) {
            inFlightPermits.release();
            return;
        }

        Runnable syncTask = () -> {
            try {
                tmdbMediaService.syncMedia(mediaType, tmdbId);
            } catch (RuntimeException ex) {
                log.warn("Background TMDB sync failed for {} {}", mediaType, tmdbId, ex);
            } finally {
                inFlight.remove(key);
                inFlightPermits.release();
            }
        };

        if (asyncEnabled) {
            try {
                tmdbSyncExecutor.execute(syncTask);
            } catch (RuntimeException ex) {
                inFlight.remove(key);
                inFlightPermits.release();
                log.warn("Failed to submit TMDB background sync for {} {}", mediaType, tmdbId, ex);
            }
            return;
        }

        syncTask.run();
    }

    private record MediaKey(MediaKind mediaType, Long tmdbId) {
    }
}
