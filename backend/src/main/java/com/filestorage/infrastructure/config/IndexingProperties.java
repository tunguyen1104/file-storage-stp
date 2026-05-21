package com.filestorage.infrastructure.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.indexing")
public class IndexingProperties {

    /** Prefix used for MinIO keys of extracted plain-text files. */
    @NotBlank
    private String contentKeyPrefix = "contents/";

    /** Upper bound on extracted text size; Tika stops at this many chars. */
    @Min(1)
    private int maxContentChars = 500_000;

    private Retry retry = new Retry();

    /**
     * MIME types eligible for text extraction. Other types still get indexed,
     * but without a content field.
     */
    private List<String> extractableMimeTypes = List.of(
            "text/plain",
            "text/csv",
            "text/html",
            "text/xml",
            "application/xml",
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/rtf",
            "text/rtf"
    );

    @Getter
    @Setter
    public static class Retry {

        /** How often the safety-net retry scheduler runs. */
        @Min(1)
        private int intervalMinutes = 10;

        /** Maximum number of indexing attempts before a file is given up on. */
        @Min(1)
        private int maxAttempts = 3;

        /**
         * Files whose indexing message was never published become eligible for
         * re-publishing only after this much time has passed since creation.
         * Avoids racing the after-commit publisher.
         */
        @Min(1)
        private int eligibleAfterMinutes = 5;
    }
}
