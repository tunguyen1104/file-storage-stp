package com.filestorage.adapter.out.persistence.repository;

import com.filestorage.adapter.out.persistence.entity.RagChatSession;
import com.filestorage.domain.model.enums.RagChatSessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RagChatSessionJpaRepository extends JpaRepository<RagChatSession, UUID> {

    Optional<RagChatSession> findByIdAndTenantIdAndUserIdAndStatus(
            UUID id,
            UUID tenantId,
            UUID userId,
            RagChatSessionStatus status
    );

    Page<RagChatSession> findByTenantIdAndUserIdAndStatusOrderByUpdatedAtDesc(
            UUID tenantId,
            UUID userId,
            RagChatSessionStatus status,
            Pageable pageable
    );
}
