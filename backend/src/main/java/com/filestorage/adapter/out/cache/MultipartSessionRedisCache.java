package com.filestorage.adapter.out.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.filestorage.domain.port.out.CachePort;
import com.filestorage.domain.port.out.MultipartSessionCacheDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis-backed implementation of {@link CachePort} for multipart upload sessions.
 * <p>
 * Key schema: {@code multipart:session:{uploadId}} → JSON of {@link MultipartSessionCacheDto}<br>
 * TTL: 24 h (matches the session expiry in PostgreSQL).
 * <p>
 * All Redis operations are wrapped in try-catch so a Redis outage never blocks
 * the primary flow — callers fall back to the database on cache miss.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MultipartSessionRedisCache implements CachePort {

    private static final String KEY_PREFIX = "multipart:session:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void cacheMultipartSession(String uploadId, MultipartSessionCacheDto dto, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(dto);
            redisTemplate.opsForValue().set(KEY_PREFIX + uploadId, json, ttl);
            log.debug("Cached multipart session: uploadId={}", uploadId);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize multipart session for caching: uploadId={}: {}", uploadId, e.getMessage());
        } catch (Exception e) {
            log.warn("Redis unavailable, skip caching multipart session {}: {}", uploadId, e.getMessage());
        }
    }

    @Override
    public Optional<MultipartSessionCacheDto> getMultipartSession(String uploadId) {
        try {
            String json = redisTemplate.opsForValue().get(KEY_PREFIX + uploadId);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, MultipartSessionCacheDto.class));
        } catch (Exception e) {
            log.warn("Redis unavailable or deserialization failed for multipart session {}: {}", uploadId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void evictMultipartSession(String uploadId) {
        try {
            redisTemplate.delete(KEY_PREFIX + uploadId);
            log.debug("Evicted multipart session from cache: uploadId={}", uploadId);
        } catch (Exception e) {
            log.warn("Redis unavailable during evict of multipart session {}: {}", uploadId, e.getMessage());
        }
    }
}
