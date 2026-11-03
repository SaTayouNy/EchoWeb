package org.example.echo.entity;

public enum EmailOutboxStatus {
    PENDING,
    PROCESSING,
    FAILED,
    EXHAUSTED,
    SENT
}
