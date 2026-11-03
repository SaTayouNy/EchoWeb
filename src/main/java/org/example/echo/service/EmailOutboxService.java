package org.example.echo.service;

import org.example.echo.entity.EmailOutboxEntity;
import org.example.echo.entity.EmailOutboxStatus;
import org.example.echo.mail.EmailMessage;
import org.example.echo.repository.EmailOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
public class EmailOutboxService {
    private static final Logger log = LoggerFactory.getLogger(EmailOutboxService.class);

    private final EmailOutboxRepository emailOutboxRepository;
    private final EmailOutboxDispatcher emailOutboxDispatcher;
    private final TaskExecutor emailOutboxExecutor;

    public EmailOutboxService(
        EmailOutboxRepository emailOutboxRepository,
        EmailOutboxDispatcher emailOutboxDispatcher,
        @Qualifier("emailOutboxExecutor") TaskExecutor emailOutboxExecutor
    ) {
        this.emailOutboxRepository = emailOutboxRepository;
        this.emailOutboxDispatcher = emailOutboxDispatcher;
        this.emailOutboxExecutor = emailOutboxExecutor;
    }

    public void enqueue(EmailMessage message) {
        OffsetDateTime now = nowUtc();

        EmailOutboxEntity outboxEmail = new EmailOutboxEntity();
        outboxEmail.setFromAddress(message.from());
        outboxEmail.setToAddress(message.to());
        outboxEmail.setSubject(message.subject());
        outboxEmail.setBody(message.text());
        outboxEmail.setStatus(EmailOutboxStatus.PENDING);
        outboxEmail.setAttempts(0);
        outboxEmail.setNextAttemptAt(now.minusSeconds(1));
        emailOutboxRepository.save(outboxEmail);

        triggerDispatchAfterCommit();
    }

    private void triggerDispatchAfterCommit() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
            || !TransactionSynchronizationManager.isSynchronizationActive()) {
            dispatchAsync();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatchAsync();
            }
        });
    }

    private void dispatchAsync() {
        emailOutboxExecutor.execute(this::safelyDispatchPendingMessages);
    }

    private void safelyDispatchPendingMessages() {
        try {
            emailOutboxDispatcher.dispatchPendingMessages();
        } catch (RuntimeException ex) {
            log.error("Failed to dispatch pending outbox emails", ex);
        }
    }

    private OffsetDateTime nowUtc() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
