package com.filestorage.infrastructure.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.rag")
public class RagProperties {

    private boolean enabled = true;

    private Chunks chunks = new Chunks();

    private Embedding embedding = new Embedding();

    private Retrieval retrieval = new Retrieval();

    private Chat chat = new Chat();

    @Getter
    @Setter
    public static class Chunks {

        @Min(1)
        private int sizeChars = 3000;

        @Min(0)
        private int overlapChars = 400;

        @NotBlank
        private String version = "v1";
    }

    @Getter
    @Setter
    public static class Embedding {

        @NotBlank
        private String model = "bge-m3";

        @Min(1)
        private int dimensions = 1024;

        @Min(1)
        private int batchSize = 32;
    }

    @Getter
    @Setter
    public static class Retrieval {

        @Min(1)
        private int topK = 8;

        @Min(1)
        private int maxTopK = 20;

        @Min(1)
        private int vectorCandidates = 80;

        @Min(1)
        private int bm25Candidates = 80;
    }

    @Getter
    @Setter
    public static class Chat {

        @NotBlank
        private String model = "alibaba-qwen3-32b";

        @DecimalMin("0.0")
        private double temperature = 0.2;

        @Min(0)
        private int maxHistoryMessages = 10;

        @Min(1)
        private int maxMessageChars = 8000;
    }
}
