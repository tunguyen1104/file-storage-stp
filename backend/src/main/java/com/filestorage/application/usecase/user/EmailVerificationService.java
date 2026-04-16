package com.filestorage.application.usecase.user;

import com.filestorage.adapter.out.messaging.EmailEventPublisher;
import com.filestorage.adapter.out.messaging.event.EmailEvent;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Handles email verification for newly created user accounts.
 * <p>
 * When an admin/tenant creates a user, the account starts with {@code isActive = false}.
 * A verification token is stored in Redis and an email event is published to RabbitMQ.
 * When the user clicks the link, the token is validated and the account is activated.
 * <p>
 * Redis key schema:
 * <ul>
 *   <li>{@code verify_email:{uuid}} → userId (string), TTL = emailVerificationExpirationMs</li>
 *   <li>{@code user_verify:{userId}} → uuid (string), TTL = emailVerificationExpirationMs
 *       — prevents multiple concurrent verification tokens per user</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final String VERIFY_TOKEN_PREFIX = "verify_email:";
    private static final String USER_VERIFY_PREFIX  = "user_verify:";

    private final UserJpaRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final EmailEventPublisher emailEventPublisher;

    @Value("${app.security.emailVerificationExpirationMs:86400000}")
    private long emailVerificationExpirationMs;

    @Value("${app.frontend.base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    // ── Send Verification Email ────────────────────────────────────────────────

    /**
     * Generates a verification token and publishes an email event via RabbitMQ.
     * Called after admin/tenant creates a new user account.
     *
     * @param user the newly created user entity (already persisted, isActive = false)
     */
    public void sendVerificationEmail(User user) {
        Duration ttl = Duration.ofMillis(emailVerificationExpirationMs);

        // Invalidate previous token if exists (e.g. resend)
        invalidatePreviousToken(user.getId());

        // Generate new verification token
        String verifyToken = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(VERIFY_TOKEN_PREFIX + verifyToken, user.getId().toString(), ttl);
        redisTemplate.opsForValue().set(USER_VERIFY_PREFIX + user.getId(), verifyToken, ttl);

        // Publish email event via RabbitMQ
        String verifyLink = frontendBaseUrl + "/verify-email?token=" + verifyToken;
        emailEventPublisher.publishVerifyEmail(
                EmailEvent.verifyEmail(user.getEmail(), Map.of(
                        "fullName", user.getFullName(),
                        "verifyLink", verifyLink
                ))
        );

        log.info("Verification email event published for user {}", user.getId());
    }

    // ── Verify Email ───────────────────────────────────────────────────────────

    /**
     * Validates the verification token from Redis and activates the user account.
     *
     * @param token the verification token from the email link
     */
    @Transactional
    public void verifyEmail(String token) {
        if (token == null || token.isBlank()) {
            throw new AppException(ErrorCode.VERIFY_TOKEN_INVALID);
        }
        token = token.replace("\"", "").trim();

        // 1. Look up token in Redis
        String userId = redisTemplate.opsForValue().get(VERIFY_TOKEN_PREFIX + token);
        if (userId == null) {
            throw new AppException(ErrorCode.VERIFY_TOKEN_INVALID);
        }

        // 2. Load user
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // 3. Idempotent: if already active, just clean up
        if (user.isActive()) {
            deleteVerifyTokens(token, user.getId());
            log.info("User {} already verified, cleaning up token", user.getId());
            return;
        }

        // 4. Activate account
        user.setActive(true);
        userRepository.save(user);

        // 5. Clean up Redis
        deleteVerifyTokens(token, user.getId());

        log.info("Email verified, account activated for user {}", user.getId());
    }

    // ── Resend Verification ────────────────────────────────────────────────────

    /**
     * Resends the verification email for an inactive user.
     */
    public void resendVerification(String email) {
        var userOpt = userRepository.findByEmailWithRoleAndTenant(email.toLowerCase().trim());
        if (userOpt.isEmpty()) {
            log.debug("Resend verification: no user found for email={}, silently ignoring", email);
            return; // Silent — prevents email enumeration
        }

        User user = userOpt.get();
        if (user.isActive()) {
            log.debug("Resend verification: user {} already active, ignoring", user.getId());
            return;
        }

        sendVerificationEmail(user);
    }

    // ── Private Helpers ────────────────────────────────────────────────────────

    private void invalidatePreviousToken(UUID userId) {
        try {
            String previousToken = redisTemplate.opsForValue().get(USER_VERIFY_PREFIX + userId);
            if (previousToken != null) {
                redisTemplate.delete(VERIFY_TOKEN_PREFIX + previousToken);
                redisTemplate.delete(USER_VERIFY_PREFIX + userId);
                log.debug("Invalidated previous verification token for user {}", userId);
            }
        } catch (Exception e) {
            log.warn("Failed to invalidate previous verification token for user {}: {}", userId, e.getMessage());
        }
    }

    private void deleteVerifyTokens(String token, UUID userId) {
        try {
            redisTemplate.delete(VERIFY_TOKEN_PREFIX + token);
            redisTemplate.delete(USER_VERIFY_PREFIX + userId);
        } catch (Exception e) {
            log.warn("Failed to delete verification tokens from Redis: {}", e.getMessage());
        }
    }
}

