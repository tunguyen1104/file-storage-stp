package com.filestorage.application.usecase.file;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.infrastructure.config.StorageProperties;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
class FileSupport {

    private final UserJpaRepository userRepository;
    private final FileJpaRepository fileRepository;
    private final StorageProperties storageProperties;

    User resolveActor(UUID actorUserId, UUID actorTenantId) {
        User actor = userRepository.findByIdWithRoleAndTenant(actorUserId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCESS_DENIED));
        if (actor.getTenant() == null || !actor.getTenant().getId().equals(actorTenantId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        return actor;
    }

    int resolveTtlMinutes(Integer ttlMinutes) {
        int effectiveTtl = ttlMinutes == null ? storageProperties.getDefaultDownloadTtlMinutes() : ttlMinutes;
        if (effectiveTtl <= 0) {
            throw new AppException(ErrorCode.INVALID_PAYLOAD);
        }
        return effectiveTtl;
    }

    File findReadableFile(UUID fileId, UUID actorTenantId, RoleCode actorRole) {
        if (actorRole == RoleCode.SUPER_ADMIN) {
            return fileRepository.findById(fileId)
                    .orElseThrow(() -> new AppException(ErrorCode.FILE_NOT_FOUND));
        }
        return fileRepository.findByIdAndTenantId(fileId, actorTenantId)
                .orElseThrow(() -> new AppException(ErrorCode.FILE_NOT_FOUND));
    }

    DownloadContext resolveDownloadContext(
            UUID actorUserId,
            UUID actorTenantId,
            RoleCode actorRole,
            UUID fileId,
            Integer ttlMinutes
    ) {
        User actor = resolveActor(actorUserId, actorTenantId);
        File file = findReadableFile(fileId, actorTenantId, actorRole);
        FileAccessPolicy.requireCanDownload(file, actor.getId(), actor.getTenant().getId(), actorRole);
        FileStatePolicy.requireActive(file);
        int effectiveTtlMinutes = resolveTtlMinutes(ttlMinutes);
        return new DownloadContext(file, effectiveTtlMinutes);
    }

    record DownloadContext(File file, int ttlMinutes) {
    }
}
