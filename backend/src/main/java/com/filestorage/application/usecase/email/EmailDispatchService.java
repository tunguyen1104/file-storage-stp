package com.filestorage.application.usecase.email;

import com.filestorage.adapter.out.messaging.event.EmailEvent;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Application-layer service responsible for constructing and sending emails via SendGrid.
 * <p>
 * Loads HTML templates from {@code classpath:templates/email/} and substitutes
 * {@code {{key}}} placeholders with values from the event's {@code payload}.
 * <p>
 * Throws {@link EmailDeliveryException} on any non-2xx SendGrid response or I/O failure
 * so the RabbitMQ consumer can trigger retry / DLQ routing.
 */
@Service
@Slf4j
public class EmailDispatchService {

    private final SendGrid sendGrid;
    private final String fromAddress;

    public EmailDispatchService(
            @Value("${sendgrid.apiKey}") String apiKey,
            @Value("${app.mail.from}") String fromAddress) {
        this.sendGrid = new SendGrid(apiKey);
        this.fromAddress = fromAddress;
    }

    public void send(EmailEvent event) {
        String templatePath = resolveTemplatePath(event.type());
        String html = loadAndInterpolate(templatePath, event.payload());
        String subject = resolveSubject(event.type());

        Mail mail = new Mail(
                new Email(fromAddress),
                subject,
                new Email(event.to()),
                new Content("text/html", html));

        Request request = new Request();
        request.setMethod(Method.POST);
        request.setEndpoint("mail/send");

        try {
            request.setBody(mail.build());
            Response response = sendGrid.api(request);

            int status = response.getStatusCode();
            if (status >= 200 && status < 300) {
                log.info("Email [{}] sent to {} via SendGrid (status={})",
                        event.type(), event.to(), status);
            } else {
                log.error("SendGrid rejected email [{}] to={}: status={} body={}",
                        event.type(), event.to(), status, response.getBody());
                throw new EmailDeliveryException(
                        "SendGrid returned non-2xx status " + status + ": " + response.getBody());
            }
        } catch (IOException e) {
            log.error("SendGrid I/O failure for email [{}] to={}: {}",
                    event.type(), event.to(), e.getMessage());
            throw new EmailDeliveryException("Failed to send email via SendGrid", e);
        }
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    private String resolveTemplatePath(EmailEvent.EmailType type) {
        return switch (type) {
            case RESET_PASSWORD   -> "templates/email/reset-password.html";
            case VERIFY_EMAIL     -> "templates/email/verify-email.html";
            case INITIAL_PASSWORD -> "templates/email/initial-password.html";
        };
    }

    private String resolveSubject(EmailEvent.EmailType type) {
        return switch (type) {
            case RESET_PASSWORD   -> "Reset your password";
            case VERIFY_EMAIL     -> "Verify your email address";
            case INITIAL_PASSWORD -> "Your account has been created";
        };
    }

    /**
     * Loads an HTML template from the classpath and replaces {@code {{key}}} tokens
     * with the corresponding values from {@code payload}.
     */
    private String loadAndInterpolate(String templatePath, Map<String, String> payload) {
        try {
            String template = new ClassPathResource(templatePath)
                    .getContentAsString(StandardCharsets.UTF_8);

            for (Map.Entry<String, String> entry : payload.entrySet()) {
                template = template.replace("{{" + entry.getKey() + "}}", entry.getValue());
            }
            return template;

        } catch (IOException e) {
            throw new IllegalStateException("Email template not found: " + templatePath, e);
        }
    }
}
