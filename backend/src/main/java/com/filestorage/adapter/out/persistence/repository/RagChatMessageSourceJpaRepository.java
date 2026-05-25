package com.filestorage.adapter.out.persistence.repository;

import com.filestorage.adapter.out.persistence.entity.RagChatMessageSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface RagChatMessageSourceJpaRepository extends JpaRepository<RagChatMessageSource, UUID> {

    List<RagChatMessageSource> findByMessageIdInOrderByCreatedAtAsc(Collection<UUID> messageIds);

    long deleteByMessageIdIn(Collection<UUID> messageIds);
}
