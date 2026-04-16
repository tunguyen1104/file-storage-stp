package com.filestorage.infrastructure.config;

import org.mapstruct.MapperConfig;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/**
 * Global MapStruct configuration shared across all {@code @Mapper} interfaces via
 * {@code @Mapper(config = GlobalMapperConfig.class)}.
 * <p>
 * Registers all mappers as Spring beans and fails the build on any unmapped target field.
 */
@MapperConfig(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface GlobalMapperConfig {
}
