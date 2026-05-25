package com.filestorage.application.usecase.rag;

import com.filestorage.adapter.out.persistence.repository.FileShareJpaRepository;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.rag.RagAccessScope;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RagAccessResolver {

    private final FileShareJpaRepository fileShareRepository;

    public RagAccessScope resolve(UUID tenantId, UUID userId, RoleCode role) {
        if (tenantId == null || userId == null || role == null) {
            throw new AppException(ErrorCode.INVALID_PAYLOAD);
        }

        if (role == RoleCode.MEMBER) {
            Instant now = Instant.now();
            List<UUID> sharedFileIds = fileShareRepository.findAccessibleFileIdsForUser(userId, tenantId, now);
            List<UUID> sharedFolderIds = fileShareRepository.findAccessibleFolderIdClosureForUser(userId, tenantId, now);
            return new RagAccessScope(tenantId, userId, role, sharedFileIds, sharedFolderIds);
        }

        // RAG intentionally keeps SUPER_ADMIN inside the actor's own tenant.
        return new RagAccessScope(tenantId, userId, role, Collections.emptyList(), Collections.emptyList());
    }
}
