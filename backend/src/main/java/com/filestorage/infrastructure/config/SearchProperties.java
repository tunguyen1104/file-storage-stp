package com.filestorage.infrastructure.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the search endpoints. Bound from {@code app.search.*}.
 *
 * <p>Every threshold and weight referenced in code lives here — no magic numbers
 * are sprinkled across services or adapters.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.search")
public class SearchProperties {

    private Cache cache = new Cache();
    private Boosts boosts = new Boosts();
    private Pagination pagination = new Pagination();
    private Suggest suggest = new Suggest();

    @Getter
    @Setter
    public static class Cache {

        /** TTL for the full-text result cache. */
        @Min(1)
        private int ttlMinutes = 5;

        /**
         * TTL for the SAYT result cache — much shorter because SAYT cardinality
         * is high (one entry per typed prefix) and freshness matters
         * immediately after an upload.
         */
        @Min(1)
        private int saytTtlSeconds = 60;

        /**
         * Hard cap on the number of live cache keys we track per tenant in the
         * key-index set. Bounds the cost of {@code evictAllForTenant} and
         * stops a single tenant browsing many pages from inflating Redis memory.
         */
        @Min(10)
        private int maxKeysPerTenant = 200;
    }

    @Getter
    @Setter
    public static class Boosts {

        @Min(1)
        private float fileName = 5f;

        @Min(1)
        private float folderName = 5f;

        @Min(1)
        private float content = 1f;
    }

    @Getter
    @Setter
    public static class Pagination {

        @Min(1)
        private int defaultSize = 20;

        @Min(1)
        private int maxSize = 100;
    }

    @Getter
    @Setter
    public static class Suggest {

        @Min(1)
        private int defaultSize = 8;

        @Min(1)
        private int maxSize = 10;

        @Min(1)
        private int maxQueryLength = 50;
    }
}
