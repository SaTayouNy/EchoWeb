package org.example.echo.mail;

public record EmailMessage(String from, String to, String subject, String text) {
}
