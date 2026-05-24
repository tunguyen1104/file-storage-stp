package com.filestorage.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for Elasticsearch. Bound from {@code app.elasticsearch.*}.
 *
 * <p>{@code uris} is single-value for simplicity — the dev / prod cluster runs
 * a single coordinating node. Username / password are optional (left empty
 * for the local dev image which runs with security disabled).
 *
 * <p>Index names are deliberately externalised: code reads them through this
 * class so the same image can deploy against {@code files-prod}, {@code files-staging},
 * etc. without recompilation. No string literal {@code "files"} or {@code "folders"}
 * lives anywhere in code paths.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.elasticsearch")
public class ElasticsearchProperties {

    /** Whether the ES adapter is active. False fully disables search and indexing. */
    private boolean enabled = true;

    @NotBlank
    private String uris = "http://localhost:9200";

    private String username;

    private String password;

    private Indices indices = new Indices();

    @Getter
    @Setter
    public static class Indices {

        @NotBlank
        private String files = "files";

        @NotBlank
        private String folders = "folders";

        @NotBlank
        private String ragChunks = "rag_chunks";
    }
}
