package com.filestorage.adapter.out.persistence.repository;

import com.filestorage.adapter.out.persistence.entity.RagChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RagChatMessageJpaRepository extends JpaRepository<RagChatMessage, UUID> {

    Page<RagChatMessage> findBySessionIdOrderByCreatedAtAsc(UUID sessionId, Pageable pageable);

    @Query("SELECT m FROM RagChatMessage m WHERE m.sessionId = :sessionId ORDER BY m.createdAt DESC")
    List<RagChatMessage> findRecentBySessionId(UUID sessionId, Pageable pageable);

    @Query("SELECT m.id FROM RagChatMessage m WHERE m.sessionId = :sessionId")
    List<UUID> findIdsBySessionId(UUID sessionId);

    long deleteBySessionId(UUID sessionId);
}
