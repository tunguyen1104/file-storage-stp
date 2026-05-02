package com.filestorage.domain.port.out;

import java.time.Duration;
import java.util.Optional;

/**
 * Outbound port for cache operations required by the application layer.
 * <p>
 * Implementations live in {@code adapter.out.cache}.
 */
public interface CachePort {

    /**
     * Stores a multipart upload session DTO in the cache.
     *
     * @param uploadId MinIO upload ID (used as the cache key suffix)
     * @param dto      session data to cache
     * @param ttl      time-to-live for the cache entry
     */
    void cacheMultipartSession(String uploadId, MultipartSessionCacheDto dto, Duration ttl);

    /**
     * Retrieves a cached multipart upload session.
     *
     * @param uploadId MinIO upload ID
     * @return the cached DTO, or empty if not present / cache unavailable
     */
    Optional<MultipartSessionCacheDto> getMultipartSession(String uploadId);

    /**
     * Removes a multipart upload session from the cache.
     * Silently ignores missing keys or cache failures.
     *
     * @param uploadId MinIO upload ID
     */
    void evictMultipartSession(String uploadId);
}
