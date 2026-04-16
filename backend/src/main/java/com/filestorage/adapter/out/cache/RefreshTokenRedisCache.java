package com.filestorage.adapter.out.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Redis cache layer for refresh tokens (hybrid storage pattern).
 * <p>
 * Key schema:
 * <ul>
 *   <li>{@code rt:{uuid}} → userId (UUID string), TTL = remaining token lifetime</li>
 *   <li>{@code user_rt:{userId}} → Set&lt;uuid&gt;, TTL = 2 × refreshTokenExpirationMs</li>
 * </ul>
 * <p>
 * All Redis operations are wrapped in try-catch. If Redis is unavailable,
 * methods return empty/void and callers fall back to Database.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RefreshTokenRedisCache {

    private static final String RT_KEY_PREFIX      = "rt:";
    private static final String USER_RT_KEY_PREFIX  = "user_rt:";

    private final StringRedisTemplate redisTemplate;

    @Value("${app.security.jwt.refreshTokenExpirationMs}")
    private long refreshTokenExpirationMs;

    // ── Write ─────────────────────────────────────────────────────────────────

    public void save(UUID tokenUuid, UUID userId, Instant expiryDate) {
        try {
            long ttlSeconds = ChronoUnit.SECONDS.between(Instant.now(), expiryDate);
            if (ttlSeconds <= 0) return;

            redisTemplate.opsForValue().set(
                    RT_KEY_PREFIX + tokenUuid,
                    userId.toString(),
                    Duration.ofSeconds(ttlSeconds)
            );

            String userKey = USER_RT_KEY_PREFIX + userId;
            redisTemplate.opsForSet().add(userKey, tokenUuid.toString());
            redisTemplate.expire(userKey, Duration.ofMillis(refreshTokenExpirationMs * 2L));

            log.debug("Cached refresh token {} for user {}", tokenUuid, userId);
        } catch (Exception e) {
            log.warn("Redis unavailable, skip caching refresh token {}: {}", tokenUuid, e.getMessage());
        }
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /**
     * Find userId by tokenUuid in Redis.
     * Returns empty if not found or Redis unavailable.
     */
    public Optional<UUID> findUserId(UUID tokenUuid) {
        try {
            String value = redisTemplate.opsForValue().get(RT_KEY_PREFIX + tokenUuid);
            if (value == null) return Optional.empty();
            return Optional.of(UUID.fromString(value));
        } catch (Exception e) {
            log.warn("Redis unavailable during findUserId for {}: {}", tokenUuid, e.getMessage());
            return Optional.empty();
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    public void delete(UUID tokenUuid, UUID userId) {
        try {
            redisTemplate.delete(RT_KEY_PREFIX + tokenUuid);
            if (userId != null) {
                redisTemplate.opsForSet().remove(USER_RT_KEY_PREFIX + userId, tokenUuid.toString());
            }
            log.debug("Deleted refresh token {} from Redis", tokenUuid);
        } catch (Exception e) {
            log.warn("Redis unavailable during delete of token {}: {}", tokenUuid, e.getMessage());
        }
    }

    /**
     * Delete ALL refresh tokens of a user from Redis (revoke all sessions).
     */
    public void deleteAllByUserId(UUID userId) {
        try {
            String userKey = USER_RT_KEY_PREFIX + userId;
            Set<String> uuids = redisTemplate.opsForSet().members(userKey);

            if (uuids != null && !uuids.isEmpty()) {
                Set<String> tokenKeys = uuids.stream()
                        .map(uuid -> RT_KEY_PREFIX + uuid)
                        .collect(Collectors.toSet());
                redisTemplate.delete(tokenKeys);
            }

            redisTemplate.delete(userKey);
            log.debug("Deleted all Redis refresh tokens for user {}", userId);
        } catch (Exception e) {
            log.warn("Redis unavailable during deleteAllByUserId for user {}: {}", userId, e.getMessage());
        }
    }
}

