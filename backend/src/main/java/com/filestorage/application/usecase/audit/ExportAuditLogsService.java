package com.filestorage.application.usecase.audit;

import com.filestorage.adapter.out.persistence.entity.AuditLog;
import com.filestorage.adapter.out.persistence.mapper.AuditLogViewMapper;
import com.filestorage.adapter.out.persistence.repository.AuditLogJpaRepository;
import com.filestorage.domain.model.audit.AuditLogSortBy;
import com.filestorage.domain.model.audit.AuditLogView;
import com.filestorage.domain.model.enums.SortDirection;
import com.filestorage.domain.port.in.audit.ExportAuditLogsUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ExportAuditLogsService implements ExportAuditLogsUseCase {

    private static final Duration DEFAULT_LOOKBACK = Duration.ofDays(90);
    static final int MAX_EXPORT_ROWS = 50_000;

    private final AuditLogJpaRepository auditLogRepository;
    private final AuditLogViewMapper auditLogViewMapper;
    private final AuditAccessPolicy accessPolicy;

    @Override
    @Transactional(readOnly = true)
    public java.util.List<AuditLogView> execute(ExportAuditLogsQuery query) {
        AuditAccessPolicy.EffectiveQuery effective =
                accessPolicy.applyTo(query.actorRole(), query.actorTenantId(), query.tenantId());

        Instant now = Instant.now();
        Instant from = query.from() != null ? query.from() : now.minus(DEFAULT_LOOKBACK);
        Instant to = query.to() != null ? query.to() : now;

        AuditLogSortBy sortBy = query.sortBy() == null ? AuditLogSortBy.CREATED_AT : query.sortBy();
        SortDirection sortDir = query.sortDirection() == null ? SortDirection.DESC : query.sortDirection();
        Sort.Direction springDir = sortDir == SortDirection.ASC ? Sort.Direction.ASC : Sort.Direction.DESC;

        Specification<AuditLog> spec = AuditLogSpecifications.buildFilter(
                effective.tenantId(),
                query.userEmail(),
                query.fileId(),
                query.actions(),
                from,
                to,
                query.ipAddress(),
                query.userAgent(),
                effective.excludeActorRole()
        );

        var pageable = PageRequest.of(0, MAX_EXPORT_ROWS, Sort.by(springDir, sortBy.property()));
        return auditLogRepository.findAll(spec, pageable).getContent().stream()
                .map(auditLogViewMapper::toView)
                .toList();
    }
}
