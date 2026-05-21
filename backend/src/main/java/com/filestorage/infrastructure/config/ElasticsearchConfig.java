package com.filestorage.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;
import org.jspecify.annotations.NonNull;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Spring Data Elasticsearch wiring. Only active when {@code app.elasticsearch.enabled=true},
 * which lets the test profile turn search off entirely (no ES container needed in CI).
 *
 * <p>Provides only the {@link ClientConfiguration} bean — the framework auto-creates
 * {@code ElasticsearchOperations} on top of it.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.elasticsearch", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({ElasticsearchProperties.class, SearchProperties.class, IndexingProperties.class})
public class ElasticsearchConfig extends ElasticsearchConfiguration {

    private final ElasticsearchProperties properties;

    public ElasticsearchConfig(ElasticsearchProperties properties) {
        this.properties = properties;
    }

    @Override
    public @NonNull ClientConfiguration clientConfiguration() {
        ClientConfiguration.MaybeSecureClientConfigurationBuilder builder =
                ClientConfiguration.builder().connectedTo(parseHostAndPort(properties.getUris()));

        if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
            return builder.usingSsl().withBasicAuth(properties.getUsername(), properties.getPassword()).build();
        }
        return builder.build();
    }

    private static String parseHostAndPort(String uri) {
        try {
            URI parsed = new URI(uri);
            return parsed.getHost() + ":" + parsed.getPort();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Invalid app.elasticsearch.uris: " + uri, e);
        }
    }
}
