package com.filestorage.application.usecase.user;

import com.filestorage.adapter.out.cache.RefreshTokenRedisCache;
import com.filestorage.adapter.out.persistence.entity.RefreshToken;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.repository.RefreshTokenJpaRepository;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages the full lifecycle of refresh tokens using a hybrid storage strategy
 * (Redis + Database). See {@link RefreshTokenRedisCache} for cache details.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenJpaRepository refreshTokenRepository;
    private final UserJpaRepository userRepository;
    private final RefreshTokenRedisCache redisCache;

    @Value("${app.security.jwt.refreshTokenExpirationMs}")
    private long refreshTokenExpirationMs;

    // ── PUBLIC API ────────────────────────────────────────────────────────────

    /**
     * Create a new refresh token after successful login.
     */
    public String createRefreshToken(User tokenOwner, Instant now) {
        RefreshToken newToken = RefreshToken.builder()
                .user(tokenOwner)
                .token(UUID.randomUUID())
                .expiryAt(now.plusMillis(refreshTokenExpirationMs))
                .build();

        refreshTokenRepository.save(newToken);
        redisCache.save(newToken.getToken(), tokenOwner.getId(), newToken.getExpiryAt());

        return newToken.getToken().toString();
    }

    /**
     * Verify a refresh token and return the owning User.
     */
    public User verify(String refreshTokenString) {
        UUID tokenUUID = parseTokenStringToUUID(refreshTokenString);
        return findUserByValidToken(tokenUUID);
    }

    /**
     * Rotate refresh token: invalidate old token, create a new one (replay-attack prevention).
     */
    @Transactional
    public String rotate(String refreshTokenString) {
        UUID tokenUUID = parseTokenStringToUUID(refreshTokenString);
        User tokenOwner = findUserByValidToken(tokenUUID);

        // Delete old token from both Redis and DB
        redisCache.delete(tokenUUID, tokenOwner.getId());
        refreshTokenRepository.findByToken(tokenUUID).ifPresent(refreshTokenRepository::delete);
        log.info("Deleted old refresh token for user {}", tokenOwner.getId());

        // Create new token
        Instant now = Instant.now();
        RefreshToken newToken = RefreshToken.builder()
                .user(tokenOwner)
                .token(UUID.randomUUID())
                .expiryAt(now.plusMillis(refreshTokenExpirationMs))
                .build();

        refreshTokenRepository.save(newToken);
        redisCache.save(newToken.getToken(), tokenOwner.getId(), newToken.getExpiryAt());

        log.info("Created new refresh token for user {}", tokenOwner.getId());
        return newToken.getToken().toString();
    }

    /**
     * Invalidate a single refresh token (single-device logout).
     */
    public void invalidate(String refreshTokenString) {
        if (refreshTokenString == null || refreshTokenString.isBlank()) {
            log.warn("Cannot invalidate: refresh token is null or blank");
            return;
        }

        UUID tokenUUID;
        try {
            tokenUUID = UUID.fromString(refreshTokenString);
        } catch (IllegalArgumentException e) {
            log.warn("Cannot invalidate: '{}' is not a valid UUID", refreshTokenString);
            return;
        }

        // Step 1: Delete from Redis
        Optional<UUID> userIdFromRedis = redisCache.findUserId(tokenUUID);
        userIdFromRedis.ifPresent(uid -> redisCache.delete(tokenUUID, uid));

        // Step 2: Delete from Database
        Optional<RefreshToken> tokenInDb = refreshTokenRepository.findByToken(tokenUUID);
        if (tokenInDb.isEmpty()) {
            log.warn("Refresh token {} not found in DB during invalidate (may have expired)", tokenUUID);
            return;
        }

        UUID ownerId = tokenInDb.get().getUser().getId();
        refreshTokenRepository.delete(tokenInDb.get());

        // Step 3: Ensure Redis is also clean (in case of Redis recovery)
        if (userIdFromRedis.isEmpty()) {
            redisCache.delete(tokenUUID, ownerId);
        }

        log.info("Invalidated refresh token for user {}", ownerId);
    }

    /**
     * Revoke ALL refresh tokens of a user across all devices.
     */
    @Transactional
    public void revokeAllUserTokens(UUID userId) {
        redisCache.deleteAllByUserId(userId);
        refreshTokenRepository.deleteByUserId(userId);
        log.info("Revoked all refresh tokens for user {}", userId);
    }

    // ── PRIVATE HELPERS ───────────────────────────────────────────────────────

    /**
     * Hybrid lookup: Redis first (fast path), then DB (fallback).
     * If found in DB but not Redis, re-populates cache (lazy sync).
     */
    private User findUserByValidToken(UUID tokenUUID) {
        // Branch 1: Redis hit
        Optional<UUID> userIdFromRedis = redisCache.findUserId(tokenUUID);
        if (userIdFromRedis.isPresent()) {
            log.debug("Cache hit: token {} found in Redis", tokenUUID);
            return userRepository.findById(userIdFromRedis.get())
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        }

        // Branch 2: Redis miss → DB fallback
        log.debug("Cache miss: token {} not in Redis, querying DB", tokenUUID);
        RefreshToken tokenInDb = refreshTokenRepository.findByToken(tokenUUID)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_TOKEN));

        if (tokenInDb.isExpired()) {
            refreshTokenRepository.delete(tokenInDb);
            throw new AppException(ErrorCode.TOKEN_EXPIRED);
        }

        // Lazy re-populate Redis
        redisCache.save(tokenInDb.getToken(), tokenInDb.getUser().getId(), tokenInDb.getExpiryAt());

        return tokenInDb.getUser();
    }

    private UUID parseTokenStringToUUID(String tokenStr) {
        try {
            return UUID.fromString(tokenStr);
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }
    }
}

