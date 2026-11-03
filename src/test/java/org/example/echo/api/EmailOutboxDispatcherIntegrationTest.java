package org.example.echo.api;

import org.example.echo.entity.EmailOutboxEntity;
import org.example.echo.entity.EmailOutboxStatus;
import org.example.echo.mail.InMemoryEmailDeliveryGateway;
import org.example.echo.repository.EmailOutboxRepository;
import org.example.echo.service.EmailOutboxDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class EmailOutboxDispatcherIntegrationTest {
    @Autowired
    private EmailOutboxDispatcher emailOutboxDispatcher;

    @Autowired
    private EmailOutboxRepository emailOutboxRepository;

    @Autowired
    private InMemoryEmailDeliveryGateway inMemoryEmailDeliveryGateway;

    @BeforeEach
    void cleanData() {
        inMemoryEmailDeliveryGateway.clear();
        emailOutboxRepository.deleteAll();
    }

    @Test
    void recoverStaleProcessingMessagesReturnsLeaseExpiredEmailsToFailedState() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        EmailOutboxEntity staleProcessing = persistEmail(
            EmailOutboxStatus.PROCESSING,
            now.minusMinutes(10),
            now.minusMinutes(10),
            2
        );
        staleProcessing.setProcessingStartedAt(now.minusMinutes(10));

        EmailOutboxEntity freshProcessing = persistEmail(
            EmailOutboxStatus.PROCESSING,
            now.minusMinutes(1),
            now.minusMinutes(1),
            1
        );
        freshProcessing.setProcessingStartedAt(now.minusMinutes(1));

        emailOutboxRepository.saveAllAndFlush(List.of(staleProcessing, freshProcessing));

        emailOutboxDispatcher.recoverStaleProcessingMessages();

        EmailOutboxEntity recovered = emailOutboxRepository.findById(staleProcessing.getId()).orElseThrow();
        assertThat(recovered.getStatus()).isEqualTo(EmailOutboxStatus.FAILED);
        assertThat(recovered.getProcessingStartedAt()).isNull();
        assertThat(recovered.getLastError()).isEqualTo("Dispatch lease expired before completion");

        EmailOutboxEntity untouched = emailOutboxRepository.findById(freshProcessing.getId()).orElseThrow();
        assertThat(untouched.getStatus()).isEqualTo(EmailOutboxStatus.PROCESSING);
        assertThat(untouched.getProcessingStartedAt()).isNotNull();
    }

    @Test
    void cleanupTerminalMessagesDeletesOnlyExpiredSentAndFailedEmails() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        EmailOutboxEntity oldSent = persistEmail(EmailOutboxStatus.SENT, now.minusDays(10), now.minusDays(10), 1);
        oldSent.setSentAt(now.minusDays(10));

        EmailOutboxEntity oldFailed = persistEmail(EmailOutboxStatus.FAILED, now.minusDays(40), now.minusDays(40), 3);
        oldFailed.setLastError("smtp down");

        EmailOutboxEntity recentFailed = persistEmail(EmailOutboxStatus.FAILED, now.minusDays(2), now.minusDays(2), 1);
        recentFailed.setLastError("temporary");

        EmailOutboxEntity processing = persistEmail(EmailOutboxStatus.PROCESSING, now.minusDays(40), now.minusDays(40), 1);
        processing.setProcessingStartedAt(now.minusMinutes(1));

        EmailOutboxEntity pending = persistEmail(EmailOutboxStatus.PENDING, now.minusDays(40), now.minusDays(40), 0);

        emailOutboxRepository.saveAllAndFlush(List.of(oldSent, oldFailed, recentFailed, processing, pending));

        emailOutboxDispatcher.cleanupTerminalMessages();

        assertThat(emailOutboxRepository.findById(oldSent.getId())).isEmpty();
        assertThat(emailOutboxRepository.findById(oldFailed.getId())).isEmpty();
        assertThat(emailOutboxRepository.findById(recentFailed.getId())).isPresent();
        assertThat(emailOutboxRepository.findById(processing.getId())).isPresent();
        assertThat(emailOutboxRepository.findById(pending.getId())).isPresent();
    }

    @Test
    @Transactional
    void cleanupDeleteRechecksStatusBeforeDeletingSelectedIds() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime cutoff = now.minusDays(30);
        EnumSet<EmailOutboxStatus> cleanupStatuses = EnumSet.of(EmailOutboxStatus.FAILED, EmailOutboxStatus.EXHAUSTED);

        EmailOutboxEntity claimedByDispatcher = persistEmail(
            EmailOutboxStatus.FAILED,
            now.minusDays(40),
            now.minusDays(40),
            1
        );
        emailOutboxRepository.saveAndFlush(claimedByDispatcher);
        List<java.util.UUID> selectedIds = emailOutboxRepository.findIdsForCleanup(
            cleanupStatuses,
            cutoff,
            org.springframework.data.domain.PageRequest.of(0, 10)
        );
        assertThat(selectedIds).contains(claimedByDispatcher.getId());

        claimedByDispatcher.setStatus(EmailOutboxStatus.PROCESSING);
        claimedByDispatcher.setProcessingStartedAt(now);
        emailOutboxRepository.saveAndFlush(claimedByDispatcher);

        int deleted = emailOutboxRepository.deleteCleanupBatch(selectedIds, cleanupStatuses, cutoff);

        assertThat(deleted).isZero();
        EmailOutboxEntity stillPresent = emailOutboxRepository.findById(claimedByDispatcher.getId()).orElseThrow();
        assertThat(stillPresent.getStatus()).isEqualTo(EmailOutboxStatus.PROCESSING);
    }

    @Test
    void dispatchMarksEmailExhaustedAfterConfiguredAttemptLimit() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        EmailOutboxEntity exhaustedCandidate = persistEmail(
            EmailOutboxStatus.FAILED,
            now.minusMinutes(5),
            now.minusMinutes(1),
            2
        );
        emailOutboxRepository.saveAndFlush(exhaustedCandidate);

        inMemoryEmailDeliveryGateway.failNextDelivery();
        emailOutboxDispatcher.dispatchPendingMessages();

        EmailOutboxEntity updated = emailOutboxRepository.findById(exhaustedCandidate.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(EmailOutboxStatus.EXHAUSTED);
        assertThat(updated.getAttempts()).isEqualTo(3);
        assertThat(updated.getSentAt()).isNull();
    }

    private EmailOutboxEntity persistEmail(
        EmailOutboxStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime nextAttemptAt,
        int attempts
    ) {
        EmailOutboxEntity email = new EmailOutboxEntity();
        email.setFromAddress("no-reply@test.local");
        email.setToAddress("user@test.local");
        email.setSubject("subject");
        email.setBody("body");
        email.setStatus(status);
        email.setAttempts(attempts);
        email.setNextAttemptAt(nextAttemptAt);
        email = emailOutboxRepository.saveAndFlush(email);
        email.setCreatedAt(createdAt);
        email.setUpdatedAt(createdAt);
        return email;
    }
}
