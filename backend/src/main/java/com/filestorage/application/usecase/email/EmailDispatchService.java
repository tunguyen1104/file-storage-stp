package com.filestorage.application.usecase.email;

import com.filestorage.adapter.out.messaging.event.EmailEvent;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Application-layer service responsible for constructing and sending emails.
 * <p>
 * Loads HTML templates from {@code classpath:templates/email/} and substitutes
 * {@code {{key}}} placeholders with values from the event's {@code payload}.
 * <p>
 * Delegates to {@link JavaMailSender} — any {@link MailException} is re-thrown
 * so that the RabbitMQ consumer can trigger retry / DLQ routing.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EmailDispatchService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromAddress;

    public void send(EmailEvent event) {
        String templatePath = resolveTemplatePath(event.type());
        String html = loadAndInterpolate(templatePath, event.payload());
        String subject = resolveSubject(event.type());

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(event.to());
            helper.setSubject(subject);
            helper.setText(html, true); // isHtml = true

            mailSender.send(message);
            log.info("Email [{}] sent successfully to {}", event.type(), event.to());

        } catch (MessagingException e) {
            // Wrap checked exception so callers only need to handle MailException hierarchy
            throw new org.springframework.mail.MailSendException("Failed to build MIME message", e);
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

