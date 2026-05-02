package com.filestorage.adapter.out.virusscan;

import com.filestorage.domain.port.out.VirusScanPort;
import com.filestorage.infrastructure.config.ClamAvProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Outbound virus-scan adapter that delegates to a ClamAV REST API sidecar
 * (e.g. <a href="https://github.com/benzino77/clamav-rest-api">clamav-rest-api</a>).
 * <p>
 * The REST API exposes {@code POST /api/v1/scan} which accepts multipart/form-data
 * and returns JSON like: {@code { "data": { "result": [{ "isInfected": false }] } }}.
 * <p>
 * This adapter is intentionally thin — the interface contract is defined by {@link VirusScanPort}.
 */
@Component
@Slf4j
public class ClamAvVirusScanAdapter implements VirusScanPort {

    private static final String INFECTED_MARKER = "\"isInfected\":true";

    private final ClamAvProperties clamAvProperties;
    private final HttpClient httpClient;

    public ClamAvVirusScanAdapter(ClamAvProperties clamAvProperties) {
        this.clamAvProperties = clamAvProperties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(clamAvProperties.getTimeoutMs()))
                .build();
    }

    @Override
    public boolean scan(InputStream fileStream) {
        byte[] fileBytes;
        try {
            fileBytes = fileStream.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read file stream for virus scan", e);
        }

        String boundary = "----ClamAvBoundary" + System.nanoTime();
        byte[] body = buildMultipartBody(fileBytes, boundary);

        String url = "http://" + clamAvProperties.getHost() + ":" + clamAvProperties.getPort() + "/api/v1/scan";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(clamAvProperties.getTimeoutMs()))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("ClamAV REST API returned HTTP {}: {}", response.statusCode(), response.body());
                throw new IllegalStateException("ClamAV scan failed with HTTP " + response.statusCode());
            }
            String responseBody = response.body();
            log.debug("ClamAV response: {}", responseBody);
            // Simple string-contains check — avoids an extra JSON parsing dependency
            boolean infected = responseBody.contains(INFECTED_MARKER);
            log.info("ClamAV scan result: infected={}", infected);
            return infected;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("ClamAV REST API call failed", e);
        }
    }

    /**
     * Builds a minimal multipart/form-data body with the file bytes under the field name {@code FILES}.
     */
    private byte[] buildMultipartBody(byte[] fileBytes, String boundary) {
        String header = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"FILES\"; filename=\"upload\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n";
        String footer = "\r\n--" + boundary + "--\r\n";

        byte[] headerBytes = header.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] footerBytes = footer.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        byte[] body = new byte[headerBytes.length + fileBytes.length + footerBytes.length];
        System.arraycopy(headerBytes, 0, body, 0, headerBytes.length);
        System.arraycopy(fileBytes, 0, body, headerBytes.length, fileBytes.length);
        System.arraycopy(footerBytes, 0, body, headerBytes.length + fileBytes.length, footerBytes.length);
        return body;
    }
}
