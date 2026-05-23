package com.filestorage.adapter.out.persistence.repository;

import com.filestorage.adapter.out.persistence.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogJpaRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    interface ActionCountProjection {
        String getAction();

        long getCnt();
    }

    @Query(value = """
            SELECT action AS action, COUNT(*) AS cnt
            FROM audit_logs
            WHERE (CAST(:tenantId AS uuid) IS NULL OR tenant_id = CAST(:tenantId AS uuid))
              AND (
                  :excludedActorRole IS NULL
                  OR extra_data IS NULL
                  OR extra_data->>'actor_role' IS NULL
                  OR extra_data->>'actor_role' <> :excludedActorRole
              )
            GROUP BY action
            """, nativeQuery = true)
    List<ActionCountProjection> countGroupedByAction(
            @Param("tenantId") UUID tenantId,
            @Param("excludedActorRole") String excludedActorRole
    );
}
