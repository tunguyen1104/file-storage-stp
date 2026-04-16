package com.filestorage.application.usecase.user;

import com.filestorage.infrastructure.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Manages Redis blacklist for revoked access tokens (logout / password change).
 * <p>
 * Key schema: {@code blacklisted_at:{jti}} → "1", TTL = remaining token lifetime.
 * <p>
 * Fail-open: if Redis is unavailable, {@link #isBlacklisted} returns {@code false}
 * to avoid blocking all traffic. The access token will expire naturally.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String BLACKLIST_PREFIX = "blacklisted_at:";

    private final StringRedisTemplate redisTemplate;
    private final JwtUtil jwtUtil;

    public void blacklist(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) return;
        try {
            String jti = jwtUtil.getJtiFromToken(accessToken);
            long ttlSeconds = jwtUtil.getRemainingTtlSeconds(accessToken);
            if (ttlSeconds <= 0) {
                log.debug("Access token already expired, skipping blacklist");
                return;
            }
            redisTemplate.opsForValue().set(
                    BLACKLIST_PREFIX + jti,
                    "1",
                    Duration.ofSeconds(ttlSeconds)
            );
            log.info("Blacklisted access token jti={} ttl={}s", jti, ttlSeconds);
        } catch (Exception e) {
            log.warn("Failed to blacklist access token: {}", e.getMessage());
        }
    }

    public boolean isBlacklisted(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) return false;
        try {
            String jti = jwtUtil.getJtiFromToken(accessToken);
            return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + jti));
        } catch (Exception e) {
            log.warn("Failed to check blacklist (fail-open), allowing request: {}", e.getMessage());
            return false;
        }
    }
}

