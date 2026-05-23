package com.filestorage.application.usecase.audit;

import com.filestorage.adapter.out.persistence.entity.AuditLog;
import com.filestorage.adapter.out.persistence.mapper.AuditLogViewMapper;
import com.filestorage.adapter.out.persistence.repository.AuditLogJpaRepository;
import com.filestorage.domain.model.audit.AuditLogPage;
import com.filestorage.domain.model.audit.AuditLogSortBy;
import com.filestorage.domain.model.audit.AuditLogView;
import com.filestorage.domain.model.enums.SortDirection;
import com.filestorage.domain.port.in.audit.ListAuditLogsUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ListAuditLogsService implements ListAuditLogsUseCase {

    private static final Duration DEFAULT_LOOKBACK = Duration.ofDays(90);
    private static final int MAX_LIMIT = 100;
    private static final int DEFAULT_LIMIT = 20;

    private final AuditLogJpaRepository auditLogRepository;
    private final AuditLogViewMapper auditLogViewMapper;
    private final AuditAccessPolicy accessPolicy;

    @Override
    @Transactional(readOnly = true)
    public AuditLogPage execute(ListAuditLogsQuery query) {
        AuditAccessPolicy.EffectiveQuery effective =
                accessPolicy.applyTo(query.actorRole(), query.actorTenantId(), query.tenantId());

        Instant now = Instant.now();
        Instant from = query.from() != null ? query.from() : now.minus(DEFAULT_LOOKBACK);
        Instant to = query.to() != null ? query.to() : now;

        int page = Math.max(1, query.page());
        int limit = Math.min(MAX_LIMIT, Math.max(1, query.limit() <= 0 ? DEFAULT_LIMIT : query.limit()));

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

        var pageable = PageRequest.of(page - 1, limit, Sort.by(springDir, sortBy.property()));
        var result = auditLogRepository.findAll(spec, pageable);
        List<AuditLogView> data = result.getContent().stream().map(auditLogViewMapper::toView).toList();

        return new AuditLogPage(data, page, limit, result.getTotalElements(), result.getTotalPages());
    }
}
