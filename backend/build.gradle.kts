plugins {
	java
	id("org.springframework.boot") version "4.0.5"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.filestorage"
version = "0.0.1-SNAPSHOT"
description = "File Storage System API"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(17)
	}
}

repositories {
	mavenCentral()
}

// ─── Dependency Versions ──────────────────────────────────────────────────────
val nimbusJoseJwtVersion          = "10.2"
val springdocVersion              = "3.0.0"
val mapstructVersion              = "1.5.5.Final"
val lombokMapstructBindingVersion = "0.2.0"
val archunitVersion               = "1.3.0"
val testcontainersVersion         = "1.19.3"
val minioVersion                  = "8.6.0"
val tikaVersion                   = "2.9.2"
val shedlockVersion               = "6.6.0"

// ─── Dependencies ─────────────────────────────────────────────────────────────
dependencies {

	// ── Spring Boot Starters ────────────────────────────────────────────────
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-amqp")
//	implementation("org.springframework.retry:spring-retry")
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	implementation("org.springframework.boot:spring-boot-starter-mail")
	// Generates metadata for @ConfigurationProperties classes
	annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

	// ── Database & Migrations ───────────────────────────────────────────────
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	runtimeOnly("org.flywaydb:flyway-database-postgresql")
	runtimeOnly("org.postgresql:postgresql")

	// ── Security / Auth ─────────────────────────────────────────────────────
	implementation("com.nimbusds:nimbus-jose-jwt:$nimbusJoseJwtVersion")

	// ── API Documentation ────────────────────────────────────────────────────
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springdocVersion")

	// ── Distributed Scheduler Lock (ShedLock + Redis) ──────────────────────
	implementation("net.javacrumbs.shedlock:shedlock-spring:$shedlockVersion")
	implementation("net.javacrumbs.shedlock:shedlock-provider-redis-spring:$shedlockVersion")

	// ── Object Storage (MinIO / S3-compatible) ─────────────────────────────
	implementation("io.minio:minio:$minioVersion")

	// ── MIME Detection (Apache Tika) ────────────────────────────────────────
	implementation("org.apache.tika:tika-core:$tikaVersion")
	implementation("org.apache.tika:tika-parsers-standard-package:$tikaVersion")

	// ── Elasticsearch (search + indexing pipeline) ──────────────────────────
	implementation("org.springframework.boot:spring-boot-starter-data-elasticsearch")

	// ── Code Generation: Lombok + MapStruct ─────────────────────────────────
	compileOnly("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok")
	implementation("org.mapstruct:mapstruct:$mapstructVersion")
	annotationProcessor("org.mapstruct:mapstruct-processor:$mapstructVersion")
	annotationProcessor("org.projectlombok:lombok-mapstruct-binding:$lombokMapstructBindingVersion")

	// ── Test ────────────────────────────────────────────────────────────────
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.security:spring-security-test")
	testImplementation("io.minio:minio:$minioVersion")

	// Lombok + MapStruct in tests (same processor ordering as main)
	testCompileOnly("org.projectlombok:lombok")
	testAnnotationProcessor("org.projectlombok:lombok")
	testAnnotationProcessor("org.mapstruct:mapstruct-processor:$mapstructVersion")
	testAnnotationProcessor("org.projectlombok:lombok-mapstruct-binding:$lombokMapstructBindingVersion")

	// Testcontainers — BOM pins all module versions consistently
	testImplementation(platform("org.testcontainers:testcontainers-bom:$testcontainersVersion"))
	testImplementation("org.testcontainers:junit-jupiter")
	testImplementation("org.testcontainers:postgresql")

	// ArchUnit — enforces hexagonal dependency rules (HexagonalArchitectureTest)
	testImplementation("com.tngtech.archunit:archunit-junit5:$archunitVersion")

	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
	systemProperty("spring.profiles.active", "test")
}
