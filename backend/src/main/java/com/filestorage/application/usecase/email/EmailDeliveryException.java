package com.filestorage.application.usecase.email;

/**
 * Thrown by {@code EmailDispatchService} when SendGrid rejects a message
 * (non-2xx response) or the HTTP call fails. Re-thrown by the RabbitMQ consumer
 * to trigger retry / DLQ routing.
 */
public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException(String message) {
        super(message);
    }

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
