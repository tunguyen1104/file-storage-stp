package com.filestorage.adapter.out.messaging.event;

import java.time.Instant;
import java.util.Map;

/**
 * Event published to RabbitMQ for email delivery by a separate email service.
 * <p>
 * {@code type} determines which email template the consumer should use.
 * {@code payload} carries template-specific data (e.g. resetLink, verifyLink, fullName).
 */
public record EmailEvent(
        EmailType type,
        String to,
        Map<String, String> payload,
        Instant publishedAt
) {

    public enum EmailType {
        RESET_PASSWORD,
        VERIFY_EMAIL
    }

    public static EmailEvent resetPassword(String to, Map<String, String> payload) {
        return new EmailEvent(EmailType.RESET_PASSWORD, to, payload, Instant.now());
    }

    public static EmailEvent verifyEmail(String to, Map<String, String> payload) {
        return new EmailEvent(EmailType.VERIFY_EMAIL, to, payload, Instant.now());
    }
}

