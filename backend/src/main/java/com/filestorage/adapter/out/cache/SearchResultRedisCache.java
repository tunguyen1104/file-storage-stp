package com.filestorage.adapter.out.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.filestorage.domain.port.out.SearchCachePort;
import com.filestorage.infrastructure.config.SearchProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Redis-backed implementation of {@link SearchCachePort}.
 *
 * <p>Key layout:
 * <ul>
 *   <li>{@code search:{tenantId}:tenant_admin:{queryHash}}   — TENANT_ADMIN full-text result</li>
 *   <li>{@code search:{tenantId}:member:{userId}:{queryHash}}— MEMBER full-text result</li>
 *   <li>{@code sayt:{tenantId}:tenant_admin:{queryHash}}     — TENANT_ADMIN SAYT result</li>
 *   <li>{@code sayt:{tenantId}:member:{userId}:{queryHash}}  — MEMBER SAYT result</li>
 *   <li>{@code search:tenant:{tenantId}:admin:keys}          — SET tracking live TENANT_ADMIN keys</li>
 *   <li>{@code search:tenant:{tenantId}:member:{userId}:keys}— SET tracking live keys for one member</li>
 *   <li>{@code search:tenant:{tenantId}:members}             — SET of userIds that have active cache entries</li>
 * </ul>
 *
 * <p>SUPER_ADMIN results are never cached — their cross-tenant scope makes
 * per-tenant invalidation insufficient.
 *
 * <p>Eviction granularity:
 * <ul>
 *   <li>{@link #evictForUser} — deletes that member's key-set + the admin key-set.
 *       Used when a member's own file/folder changes, so other members' caches stay warm.</li>
 * </ul>
 *
 * <p>Every Redis call is wrapped in try/catch — a Redis outage must never
 * cascade into a search failure.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SearchResultRedisCache implements SearchCachePort {

    private static final String SEARCH_PREFIX  = "search:";
    private static final String TENANT_SEGMENT = "tenant:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final SearchProperties searchProperties;

    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        try {
            String json = redis.opsForValue().get(key);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, type));
        } catch (Exception e) {
            log.warn("Search cache get failed for key={}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(String key, Object value, Duration ttl, UUID tenantId, UUID userId) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redis.opsForValue().set(key, json, ttl);
            trackKey(tenantId, userId, key);
        } catch (JsonProcessingException e) {
            log.warn("Search cache put failed (serialize) for key={}: {}", key, e.getMessage());
        } catch (Exception e) {
            log.warn("Search cache put failed (Redis) for key={}: {}", key, e.getMessage());
        }
    }

    @Override
    public void evictForUser(UUID tenantId, UUID userId) {
        // Evict this member's own cached results.
        evictKeySet(memberKeySet(tenantId, userId));
        // Also evict admin cached results — admins can see the member's files,
        // so their cache is stale too.
        evictKeySet(adminKeySet(tenantId));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void trackKey(UUID tenantId, UUID userId, String key) {
        String setKey = (userId == null) ? adminKeySet(tenantId) : memberKeySet(tenantId, userId);
        try {
            redis.opsForSet().add(setKey, key);
            redis.expire(setKey, trackingTtl());

            // For member keys, also record the userId in the per-tenant members index.
            if (userId != null) {
                String membersSetKey = tenantMembersSet(tenantId);
                redis.opsForSet().add(membersSetKey, userId.toString());
                redis.expire(membersSetKey, trackingTtl());
            }

            // Best-effort cap on the key-set size.
            Long size = redis.opsForSet().size(setKey);
            int max = searchProperties.getCache().getMaxKeysPerTenant();
            if (size != null && size > max) {
                redis.opsForSet().pop(setKey);
            }
        } catch (Exception e) {
            log.warn("Search cache key tracking failed for tenant={}: {}", tenantId, e.getMessage());
        }
    }

    /** Evicts all cache keys recorded in a tracking set, then deletes the set itself. */
    private void evictKeySet(String setKey) {
        try {
            Set<String> keys = redis.opsForSet().members(setKey);
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
            redis.delete(setKey);
        } catch (Exception e) {
            log.warn("Search cache eviction failed for set={}: {}", setKey, e.getMessage());
        }
    }

    private Duration trackingTtl() {
        return Duration.ofMinutes(Math.max(searchProperties.getCache().getTtlMinutes() + 5, 10));
    }

    private static String adminKeySet(UUID tenantId) {
        return SEARCH_PREFIX + TENANT_SEGMENT + tenantId + ":admin:keys";
    }

    private static String memberKeySet(UUID tenantId, UUID userId) {
        return SEARCH_PREFIX + TENANT_SEGMENT + tenantId + ":member:" + userId + ":keys";
    }

    private static String tenantMembersSet(UUID tenantId) {
        return SEARCH_PREFIX + TENANT_SEGMENT + tenantId + ":members";
    }
}
