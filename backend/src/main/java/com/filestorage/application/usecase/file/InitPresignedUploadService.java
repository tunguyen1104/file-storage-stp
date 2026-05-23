package com.filestorage.application.usecase.file;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.entity.Folder;
import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.adapter.out.persistence.repository.FolderJpaRepository;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
import com.filestorage.application.usecase.folder.FolderAccessPolicy;
import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.model.audit.AuditAction;
import com.filestorage.domain.model.file.PresignedUploadView;
import com.filestorage.domain.port.in.audit.LogAuditEventUseCase;
import com.filestorage.domain.port.in.audit.LogAuditEventUseCase.AuditEvent;
import com.filestorage.domain.port.in.file.InitPresignedUploadUseCase;
import com.filestorage.infrastructure.audit.Auditable;
import com.filestorage.domain.port.out.ObjectStoragePort;
import com.filestorage.infrastructure.config.UploadProperties;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import com.filestorage.shared.util.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InitPresignedUploadService implements InitPresignedUploadUseCase {

    private static final List<FileStatus> IN_FLIGHT_STATUSES = List.of(FileStatus.PENDING, FileStatus.SCANNING);

    private final FileJpaRepository fileRepository;
    private final FolderJpaRepository folderRepository;
    private final UserJpaRepository userRepository;
    private final ObjectStoragePort objectStoragePort;
    private final UploadProperties uploadProperties;
    private final FolderAccessPolicy folderAccessPolicy;
    private final LogAuditEventUseCase auditLogger;

    @Override
    @Transactional
    @Auditable(
            action = AuditAction.FILE_UPLOAD,
            fileIdSpel = "#result.fileId()",
            tenantIdSpel = "#command.tenantId()",
            extraSpel = "{'size_bytes': #command.sizeBytes(), 'mime_type': #command.mimeType(), 'upload_type': 'presigned'}",
            logOnFailure = false
    )
    public PresignedUploadView execute(InitPresignedUploadCommand command) {

        // Fast-exit: reject large file sizes before touching the DB or storage.
        if (command.sizeBytes() > uploadProperties.getMaxFileSizeBytes()) {
            throw new AppException(ErrorCode.FILE_TOO_LARGE);
        }

        // Acquire a pessimistic write lock on the user row to serialize concurrent quota checks.
        User actor = userRepository.findByIdForUpdate(command.userId())
                .orElseThrow(() -> new AppException(ErrorCode.ACCESS_DENIED));

        if (actor.getTenant() == null || !actor.getTenant().getId().equals(command.tenantId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        Tenant tenant = actor.getTenant();

        // Effective usage = already committed bytes + bytes still in-flight (PENDING / SCANNING).
        long inFlightBytes = fileRepository.sumSizeBytesByUserAndStatuses(actor.getId(), IN_FLIGHT_STATUSES);
        long effectiveUsage = safeAdd(actor.getStorageUsedBytes(), inFlightBytes);
        long newUsage = safeAdd(effectiveUsage, command.sizeBytes());

        if (newUsage > actor.getStorageQuotaBytes()) {
            auditQuotaExceeded(actor, command.sizeBytes(), actor.getStorageQuotaBytes() - effectiveUsage);
            throw new AppException(ErrorCode.QUOTA_EXCEEDED);
        }

        Folder folder = getWritableFolder(command.folderId(), actor, command.role());

        String storageKey = objectStoragePort.buildStorageKey(
                tenant.getId().toString(),
                actor.getId().toString(),
                UUID.randomUUID().toString(),
                extractExtension(command.originalName().trim())
        );

        File pending = File.builder()
                .folder(folder)
                .tenant(tenant)
                .uploadedBy(actor)
                .originalName(command.originalName().trim())
                .storageKey(storageKey)
                .mimeType(command.mimeType())
                .sizeBytes(command.sizeBytes())
                .status(FileStatus.PENDING)
                .isIndexed(false)
                .build();
        pending = fileRepository.save(pending);

        int ttlMinutes = uploadProperties.getPresignedPutTtlMinutes();
        String uploadUrl = objectStoragePort.generatePresignedPutUrl(storageKey, command.mimeType(), ttlMinutes);
        Instant expiresAt = Instant.now().plusSeconds((long) ttlMinutes * 60);

        return new PresignedUploadView(pending.getId(), uploadUrl, expiresAt);
    }

    private Folder getWritableFolder(UUID folderId, User actor, com.filestorage.domain.model.enums.RoleCode actorRole) {
        if (folderId == null) {
            return null;
        }
        Folder folder = folderRepository.findByIdAndTenantId(folderId, actor.getTenant().getId())
                .orElseThrow(() -> new AppException(ErrorCode.FOLDER_NOT_FOUND));
        folderAccessPolicy.requireCanWriteDirect(folder, actor.getId(), actor.getTenant().getId(), actorRole);
        return folder;
    }

    private static String extractExtension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1);
    }

    private static long safeAdd(long value, long delta) {
        return Math.addExact(value, delta);
    }

    private void auditQuotaExceeded(User actor, long requestedBytes, long availableBytes) {
        HttpServletRequest request = RequestContext.getCurrentRequest().orElse(null);
        String role = actor.getRole().getCode().name();
        Map<String, Object> extra = new HashMap<>();
        extra.put("actor_role", role);
        extra.put("actor_tenant_id", actor.getTenant().getId().toString());
        extra.put("requested_bytes", requestedBytes);
        extra.put("available_bytes", availableBytes);
        String requestId = RequestContext.getRequestId(request);
        if (requestId != null) {
            extra.put("request_id", requestId);
        }
        auditLogger.logAsync(new AuditEvent(
                AuditAction.QUOTA_EXCEEDED,
                actor.getTenant().getId(),
                actor.getId(),
                null,
                role,
                RequestContext.getClientIp(request),
                RequestContext.getUserAgent(request),
                requestId,
                extra
        ));
    }
}
