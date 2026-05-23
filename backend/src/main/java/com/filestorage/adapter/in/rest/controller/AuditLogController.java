package com.filestorage.adapter.in.rest.controller;

import com.filestorage.adapter.in.rest.dto.response.ApiResponse;
import com.filestorage.adapter.in.rest.dto.response.PageResponse;
import com.filestorage.adapter.in.rest.dto.response.audit.AuditLogResponse;
import com.filestorage.adapter.in.rest.dto.response.audit.AuditLogStatsResponse;
import com.filestorage.adapter.in.rest.mapper.AuditLogCsvWriter;
import com.filestorage.adapter.in.rest.mapper.AuditLogRestMapper;
import com.filestorage.adapter.in.rest.security.SecurityUser;
import com.filestorage.domain.model.audit.AuditAction;
import com.filestorage.domain.model.audit.AuditLogPage;
import com.filestorage.domain.model.audit.AuditLogSortBy;
import com.filestorage.domain.model.audit.AuditLogStats;
import com.filestorage.domain.model.audit.AuditLogView;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.enums.SortDirection;
import com.filestorage.domain.port.in.audit.ExportAuditLogsUseCase;
import com.filestorage.domain.port.in.audit.GetAuditLogStatsUseCase;
import com.filestorage.domain.port.in.audit.ListAuditLogsUseCase;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import com.filestorage.shared.util.SecurityContextUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'TENANT_ADMIN')")
public class AuditLogController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_EMAIL_LENGTH = 255;
    private static final int MAX_USER_AGENT_LENGTH = 256;

    private final ListAuditLogsUseCase listAuditLogsUseCase;
    private final GetAuditLogStatsUseCase getAuditLogStatsUseCase;
    private final ExportAuditLogsUseCase exportAuditLogsUseCase;
    private final AuditLogRestMapper mapper;
    private final AuditLogCsvWriter csvWriter;

    @GetMapping("/audit-logs")
    public ResponseEntity<ApiResponse<PageResponse<AuditLogResponse>>> list(
            @RequestParam(required = false) String userEmail,
            @RequestParam(required = false) List<AuditAction> action,
            @RequestParam(required = false) UUID fileId,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String ipAddress,
            @RequestParam(required = false) String userAgent,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) AuditLogSortBy sortBy,
            @RequestParam(required = false) SortDirection sortDir
    ) {
        SecurityUser actor = currentUserOrThrow();
        validateCommonFilters(from, to, userEmail, userAgent);
        if (limit > MAX_PAGE_SIZE) {
            throw new AppException(ErrorCode.INVALID_PAYLOAD);
        }

        AuditLogPage view = listAuditLogsUseCase.execute(new ListAuditLogsUseCase.ListAuditLogsQuery(
                UUID.fromString(actor.getUserId()),
                UUID.fromString(actor.getTenantId()),
                parseRole(actor.getRole()),
                userEmail,
                action,
                fileId,
                tenantId,
                from,
                to,
                ipAddress,
                userAgent,
                page,
                limit,
                sortBy,
                sortDir
        ));

        return ResponseEntity.ok(ApiResponse.buildSuccess(mapper.toPageResponse(view)));
    }

    @GetMapping("/audit-logs/stats")
    public ResponseEntity<ApiResponse<AuditLogStatsResponse>> stats() {
        SecurityUser actor = currentUserOrThrow();

        AuditLogStats stats = getAuditLogStatsUseCase.execute(new GetAuditLogStatsUseCase.GetAuditLogStatsQuery(
                UUID.fromString(actor.getUserId()),
                UUID.fromString(actor.getTenantId()),
                parseRole(actor.getRole())
        ));

        return ResponseEntity.ok(ApiResponse.buildSuccess(
                new AuditLogStatsResponse(stats.total(), stats.severityCounts())
        ));
    }

    @GetMapping("/audit-logs/export")
    public ResponseEntity<StreamingResponseBody> export(
            @RequestParam(required = false) String userEmail,
            @RequestParam(required = false) List<AuditAction> action,
            @RequestParam(required = false) UUID fileId,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String ipAddress,
            @RequestParam(required = false) String userAgent,
            @RequestParam(required = false) AuditLogSortBy sortBy,
            @RequestParam(required = false) SortDirection sortDir
    ) {
        SecurityUser actor = currentUserOrThrow();
        validateCommonFilters(from, to, userEmail, userAgent);

        List<AuditLogView> rows = exportAuditLogsUseCase.execute(new ExportAuditLogsUseCase.ExportAuditLogsQuery(
                UUID.fromString(actor.getUserId()),
                UUID.fromString(actor.getTenantId()),
                parseRole(actor.getRole()),
                userEmail,
                action,
                fileId,
                tenantId,
                from,
                to,
                ipAddress,
                userAgent,
                sortBy,
                sortDir
        ));

        String filename = "audit-logs-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .withZone(java.time.ZoneOffset.UTC)
                .format(Instant.now()) + ".csv";

        StreamingResponseBody body = outputStream -> {
            try (var writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                csvWriter.write(rows, writer);
            } catch (java.io.IOException e) {
                throw new UncheckedIOException(e);
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(body);
    }

    private void validateCommonFilters(Instant from, Instant to, String userEmail, String userAgent) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new AppException(ErrorCode.INVALID_PAYLOAD);
        }
        if (userEmail != null && userEmail.length() > MAX_EMAIL_LENGTH) {
            throw new AppException(ErrorCode.INVALID_PAYLOAD);
        }
        if (userAgent != null && userAgent.length() > MAX_USER_AGENT_LENGTH) {
            throw new AppException(ErrorCode.INVALID_PAYLOAD);
        }
    }

    private SecurityUser currentUserOrThrow() {
        return SecurityContextUtil.getCurrentUser().orElseThrow(() -> new AppException(ErrorCode.ACCESS_DENIED));
    }

    private RoleCode parseRole(String role) {
        RoleCode parsed;
        try {
            parsed = RoleCode.valueOf(role);
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        if (parsed == RoleCode.MEMBER) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
        return parsed;
    }
}
