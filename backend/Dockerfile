# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /workspace

ARG ENABLE_DEPENDENCY_WARMUP=true

# Copy files needed to resolve dependencies first for better layer caching
COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle.kts settings.gradle.kts
COPY build.gradle.kts build.gradle.kts
RUN chmod +x gradlew

# Warm up Gradle cache for faster rebuilds (can be disabled for fast local iteration)
RUN if [ "$ENABLE_DEPENDENCY_WARMUP" = "true" ]; then ./gradlew --no-daemon dependencies > /dev/null || true; fi

# Copy source and build executable Spring Boot jar
COPY src src
RUN ./gradlew --no-daemon clean bootJar
RUN set -eux; \
    jar_path="$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' | head -n 1)"; \
    test -n "$jar_path"; \
    cp "$jar_path" /workspace/app.jar

FROM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

ENV SPRING_PROFILES_ACTIVE=dev
ENV JAVA_OPTS=""

COPY --from=builder /workspace/app.jar /app/app.jar

EXPOSE 8081

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]


