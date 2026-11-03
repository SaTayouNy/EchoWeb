package org.example.echo.service;

import org.example.echo.entity.MediaKind;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class TmdbBackgroundSyncServiceTest {
    @Test
    void doesNotDispatchMoreTasksThanInFlightCap() throws Exception {
        TmdbMediaService tmdbMediaService = mock(TmdbMediaService.class);
        ExecutorService executorService = Executors.newFixedThreadPool(4);
        CountDownLatch firstTaskStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstTask = new CountDownLatch(1);

        doAnswer(invocation -> {
            firstTaskStarted.countDown();
            assertThat(releaseFirstTask.await(2, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(tmdbMediaService).syncMedia(any(MediaKind.class), anyLong());

        TaskExecutor taskExecutor = executorService::execute;
        TmdbBackgroundSyncService syncService = new TmdbBackgroundSyncService(tmdbMediaService, taskExecutor, true, 1);

        try {
            syncService.scheduleAfterCommit(MediaKind.MOVIE, 1L);
            assertThat(firstTaskStarted.await(2, TimeUnit.SECONDS)).isTrue();

            for (long tmdbId = 2; tmdbId <= 20; tmdbId++) {
                syncService.scheduleAfterCommit(MediaKind.MOVIE, tmdbId);
            }

            verify(tmdbMediaService, timeout(250).times(1)).syncMedia(any(MediaKind.class), anyLong());
        } finally {
            releaseFirstTask.countDown();
            executorService.shutdown();
            assertThat(executorService.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void releasesPermitWhenExecutorRejectsTask() {
        TmdbMediaService tmdbMediaService = mock(TmdbMediaService.class);
        AtomicInteger executeAttempts = new AtomicInteger();
        TaskExecutor rejectingTaskExecutor = task -> {
            executeAttempts.incrementAndGet();
            throw new RuntimeException("queue full");
        };
        TmdbBackgroundSyncService syncService = new TmdbBackgroundSyncService(tmdbMediaService, rejectingTaskExecutor, true, 1);

        syncService.scheduleAfterCommit(MediaKind.MOVIE, 1L);
        syncService.scheduleAfterCommit(MediaKind.MOVIE, 2L);

        assertThat(executeAttempts).hasValue(2);
    }
}
