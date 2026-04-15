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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Handles forgot-password / reset-password flows.
 * <p>
 * Reset token is a random UUID stored in Redis with a 1-hour TTL.
 * The actual email delivery is delegated to a separate service via RabbitMQ.
 * <p>
 * Redis key schema:
 * <ul>
 *   <li>{@code reset_token:{uuid}} → userId (string), TTL = resetTokenExpirationMs</li>
 *   <li>{@code user_reset:{userId}} → uuid (string), TTL = resetTokenExpirationMs
 *       — prevents multiple concurrent reset tokens per user</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PasswordResetService {

    private static final String RESET_TOKEN_PREFIX = "reset_token:";
    private static final String USER_RESET_PREFIX  = "user_reset:";

    private final UserJpaRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final PasswordEncoder passwordEncoder;
    private final EmailEventPublisher emailEventPublisher;
    private final RefreshTokenService refreshTokenService;

    @Value("${app.security.resetTokenExpirationMs:3600000}")
    private long resetTokenExpirationMs;

    @Value("${app.frontend.base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    // ── Forgot Password ────────────────────────────────────────────────────────

    /**
     * Initiates the password reset flow:
     * 1. Validate user exists and is active
     * 2. Invalidate any previous reset token
     * 3. Generate and store new reset token in Redis
     * 4. Publish email event to RabbitMQ
     * <p>
     * Always returns void (no indication whether user exists — prevents email enumeration).
     */
    public void forgotPassword(String email) {
        var userOpt = userRepository.findByEmailWithRoleAndTenant(email.toLowerCase().trim());
        if (userOpt.isEmpty()) {
            log.debug("Forgot-password: no user found for email={}, silently ignoring", email);
            return; // Silent — prevents email enumeration
        }

        User user = userOpt.get();
        if (!user.isActive()) {
            log.debug("Forgot-password: user {} is inactive, silently ignoring", user.getId());
            return; // Silent — inactive accounts cannot reset
        }

        Duration ttl = Duration.ofMillis(resetTokenExpirationMs);

        // Invalidate previous reset token if exists
        invalidatePreviousResetToken(user.getId());

        // Generate new token
        String resetToken = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(RESET_TOKEN_PREFIX + resetToken, user.getId().toString(), ttl);
        redisTemplate.opsForValue().set(USER_RESET_PREFIX + user.getId(), resetToken, ttl);

        // Publish email event via RabbitMQ
        String resetLink = frontendBaseUrl + "/reset-password?token=" + resetToken;
        emailEventPublisher.publishResetPassword(
                EmailEvent.resetPassword(user.getEmail(), Map.of(
                        "fullName", user.getFullName(),
                        "resetLink", resetLink
                ))
        );

        log.info("Forgot-password: reset token generated for user {}", user.getId());
    }

    // ── Validate Reset Token ───────────────────────────────────────────────────

    /**
     * Validates that a reset token exists in Redis and has not expired.
     * Used by the frontend to check token validity before showing the reset form.
     */
    public void validateResetToken(String token) {
        String userId = redisTemplate.opsForValue().get(RESET_TOKEN_PREFIX + token);
        if (userId == null) {
            throw new AppException(ErrorCode.RESET_TOKEN_INVALID);
        }
    }

    // ── Reset Password ─────────────────────────────────────────────────────────

    /**
     * Resets the user's password using a valid reset token (unauthenticated flow).
     * <ol>
     *   <li>Validate reset token in Redis</li>
     *   <li>Validate password confirmation match</li>
     *   <li>Update password + passwordChangedAt</li>
     *   <li>Delete reset token from Redis</li>
     *   <li>Revoke all refresh tokens (force re-login on all devices)</li>
     * </ol>
     */
    @Transactional
    public void resetPassword(String token, String newPassword, String confirmPassword) {
        // 1. Validate token
        String userId = redisTemplate.opsForValue().get(RESET_TOKEN_PREFIX + token);
        if (userId == null) {
            throw new AppException(ErrorCode.RESET_TOKEN_INVALID);
        }

        // 2. Validate password match
        if (!newPassword.equals(confirmPassword)) {
            throw new AppException(ErrorCode.PASSWORD_MISMATCH);
        }

        // 3. Load and validate user
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (!user.isActive()) {
            throw new AppException(ErrorCode.ACCOUNT_DISABLED);
        }

        // 4. Update password
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(Instant.now());
        userRepository.save(user);

        // 5. Delete reset tokens from Redis
        deleteResetTokens(token, user.getId());

        // 6. Revoke all sessions
        refreshTokenService.revokeAllUserTokens(user.getId());

        log.info("Password reset successful, all sessions revoked for user {}", user.getId());
    }

    // ── Private Helpers ────────────────────────────────────────────────────────

    private void invalidatePreviousResetToken(UUID userId) {
        try {
            String previousToken = redisTemplate.opsForValue().get(USER_RESET_PREFIX + userId);
            if (previousToken != null) {
                redisTemplate.delete(RESET_TOKEN_PREFIX + previousToken);
                redisTemplate.delete(USER_RESET_PREFIX + userId);
                log.debug("Invalidated previous reset token for user {}", userId);
            }
        } catch (Exception e) {
            log.warn("Failed to invalidate previous reset token for user {}: {}", userId, e.getMessage());
        }
    }

    private void deleteResetTokens(String token, UUID userId) {
        try {
            redisTemplate.delete(RESET_TOKEN_PREFIX + token);
            redisTemplate.delete(USER_RESET_PREFIX + userId);
        } catch (Exception e) {
            log.warn("Failed to delete reset tokens from Redis: {}", e.getMessage());
        }
    }
}
