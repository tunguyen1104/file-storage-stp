package com.filestorage.adapter.out.search;

import com.filestorage.infrastructure.config.ElasticsearchProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Creates the {@code files} and {@code folders} indices on application startup
 * with the JSON mapping that wires the {@code icu_analyzer}. Idempotent: if the
 * index already exists, leaves it alone.
 *
 * <p><b>Fails fast</b> if the mapping cannot be installed — most often because
 * the {@code analysis-icu} plugin is not present on the ES node. Silently
 * downgrading to the default analyzer would break Vietnamese diacritic
 * matching in production without anyone noticing.
 *
 * <p>Index names are read from {@link ElasticsearchProperties} — never hardcoded.
 */
@Component
@ConditionalOnProperty(prefix = "app.elasticsearch", name = "enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
@RequiredArgsConstructor
public class IndexInitializer implements ApplicationRunner {

    private static final String FILES_MAPPING_PATH   = "elasticsearch/files-mapping.json";
    private static final String FOLDERS_MAPPING_PATH = "elasticsearch/folders-mapping.json";

    private final ElasticsearchTemplate elasticsearchTemplate;
    private final ElasticsearchProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        createIfMissing(properties.getIndices().getFiles(), FILES_MAPPING_PATH);
        createIfMissing(properties.getIndices().getFolders(), FOLDERS_MAPPING_PATH);
    }

    private void createIfMissing(String indexName, String mappingResourcePath) {
        IndexCoordinates coordinates = IndexCoordinates.of(indexName);
        IndexOperations ops = elasticsearchTemplate.indexOps(coordinates);

        if (ops.exists()) {
            log.info("Elasticsearch index '{}' already exists, skipping creation", indexName);
            return;
        }

        String mappingJson = readClasspathResource(mappingResourcePath);
        Document mapping = Document.parse(mappingJson);

        try {
            boolean created = ops.create(mapping);
            if (!created) {
                throw new IllegalStateException("ES index creation returned false for '" + indexName + "'");
            }
            log.info("Created Elasticsearch index '{}' with icu_analyzer mapping", indexName);
        } catch (RuntimeException e) {
            // The most common failure here is "Unknown analyzer: icu_analyzer" — the analysis-icu
            // plugin is missing on the ES node. Surface this immediately rather than fall back
            // to the standard analyzer, which would silently break Vietnamese tokenization.
            log.error("Failed to create Elasticsearch index '{}'. Most common cause: analysis-icu plugin is not installed on the ES node.", indexName, e);
            throw new IllegalStateException(
                    "Failed to create Elasticsearch index '" + indexName + "'. " +
                            "Verify the analysis-icu plugin is installed (see docker/elasticsearch/Dockerfile).",
                    e);
        }
    }

    private static String readClasspathResource(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read classpath resource: " + path, e);
        }
    }
}
