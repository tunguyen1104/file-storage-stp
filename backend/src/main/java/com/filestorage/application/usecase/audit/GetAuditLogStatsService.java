package com.filestorage.application.usecase.audit;

import com.filestorage.adapter.out.persistence.repository.AuditLogJpaRepository;
import com.filestorage.adapter.out.persistence.repository.AuditLogJpaRepository.ActionCountProjection;
import com.filestorage.domain.model.audit.AuditAction;
import com.filestorage.domain.model.audit.AuditLogStats;
import com.filestorage.domain.model.audit.AuditSeverity;
import com.filestorage.domain.port.in.audit.GetAuditLogStatsUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GetAuditLogStatsService implements GetAuditLogStatsUseCase {

    private final AuditLogJpaRepository auditLogRepository;
    private final AuditAccessPolicy accessPolicy;

    /**
     * One native GROUP BY action query → aggregate severity counts in Java.
     * Scope determined by {@link AuditAccessPolicy}: super_admin sees all tenants,
     * tenant_admin sees own tenant excluding super_admin events.
     */
    @Override
    @Transactional(readOnly = true)
    public AuditLogStats execute(GetAuditLogStatsQuery query) {
        AuditAccessPolicy.EffectiveQuery effective =
                accessPolicy.applyTo(query.actorRole(), query.actorTenantId(), null);

        List<ActionCountProjection> rows = auditLogRepository.countGroupedByAction(
                effective.tenantId(),
                effective.excludeActorRole()
        );

        Map<AuditSeverity, Long> severityCounts = new EnumMap<>(AuditSeverity.class);
        for (AuditSeverity s : AuditSeverity.values()) {
            severityCounts.put(s, 0L);
        }

        long total = 0;
        for (ActionCountProjection row : rows) {
            AuditAction action;
            try {
                action = AuditAction.valueOf(row.getAction());
            } catch (IllegalArgumentException ex) {
                // Unknown action value in DB (e.g. legacy row). Count toward total, skip severity bucket.
                total += row.getCnt();
                continue;
            }
            long cnt = row.getCnt();
            total += cnt;
            severityCounts.merge(action.severity(), cnt, Long::sum);
        }

        return new AuditLogStats(total, severityCounts);
    }
}
