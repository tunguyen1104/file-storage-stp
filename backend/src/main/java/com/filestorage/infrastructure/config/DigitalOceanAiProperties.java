package com.filestorage.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.digitalocean.ai")
public class DigitalOceanAiProperties {

    @NotBlank
    private String baseUrl = "https://inference.do-ai.run";

    /**
     * Intentionally optional at startup in Phase 1. The AI adapters will validate
     * this before making DigitalOcean calls once chat/embedding is wired.
     */
    private String apiKey;
}
