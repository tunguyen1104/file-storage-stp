package com.filestorage.domain.port.out;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public interface SearchCachePort {

    <T> Optional<T> get(String key, Class<T> type);

    void put(String key, Object value, Duration ttl, UUID tenantId, UUID userId);

    void evictForUser(UUID tenantId, UUID userId);
}
