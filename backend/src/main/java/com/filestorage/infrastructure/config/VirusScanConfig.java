package com.filestorage.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Activates binding of {@link ClamAvProperties} and {@link UploadProperties}.
 */
@Configuration
@EnableConfigurationProperties({ClamAvProperties.class, UploadProperties.class})
public class VirusScanConfig {
}
