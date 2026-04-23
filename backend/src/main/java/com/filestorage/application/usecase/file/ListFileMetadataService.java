package com.filestorage.application.usecase.file;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.mapper.FileViewMapper;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.domain.model.enums.FileSortBy;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.enums.SortDirection;
import com.filestorage.domain.model.file.FilePageView;
import com.filestorage.domain.model.file.FileView;
import com.filestorage.domain.port.in.file.ListFileMetadataUseCase;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListFileMetadataService implements ListFileMetadataUseCase {

    private final FileJpaRepository fileRepository;
    private final FileSupport fileSupport;
    private final FileViewMapper fileViewMapper;

    @Override
    @Transactional(readOnly = true)
    public FilePageView execute(ListFileMetadataQuery query) {
        var actor = fileSupport.resolveActor(query.actorUserId(), query.actorTenantId());
        UUID effectiveTenantId = resolveEffectiveTenantId(query, actor.getTenant().getId());
        UUID effectiveUploadedBy = resolveEffectiveUploadedBy(query, actor.getId());

        int page = Math.max(1, query.page());
        int limit = Math.min(100, Math.max(1, query.limit()));
        FileSortBy sortBy = query.sortBy() == null ? FileSortBy.CREATED_AT : query.sortBy();
        SortDirection sortDirection = query.sortDirection() == null ? SortDirection.DESC : query.sortDirection();

        Specification<File> spec = Specification
                .where(FileSearchSpecifications.byTenant(effectiveTenantId))
                .and(FileSearchSpecifications.byFolderId(query.folderId()))
                .and(FileSearchSpecifications.byStatus(query.status()))
                .and(FileSearchSpecifications.byUploadedBy(effectiveUploadedBy))
                .and(FileSearchSpecifications.bySearch(query.search()));

        var pageable = PageRequest.of(page - 1, limit, Sort.by(toSpringDirection(sortDirection), sortBy.property()));
        var result = fileRepository.findAll(spec, pageable);
        List<FileView> data = result.getContent().stream().map(fileViewMapper::toFileView).toList();

        return new FilePageView(data, page, limit, result.getTotalElements(), result.getTotalPages());
    }

    private UUID resolveEffectiveTenantId(ListFileMetadataQuery query, UUID actorTenantId) {
        if (query.actorRole() == RoleCode.SUPER_ADMIN) {
            return query.tenantId();
        }

        if (query.tenantId() != null && !query.tenantId().equals(actorTenantId)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
        return actorTenantId;
    }

    private UUID resolveEffectiveUploadedBy(ListFileMetadataQuery query, UUID actorUserId) {
        if (query.actorRole() != RoleCode.MEMBER) {
            return query.uploadedBy();
        }

        if (query.uploadedBy() != null && !query.uploadedBy().equals(actorUserId)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
        return actorUserId;
    }

    private Sort.Direction toSpringDirection(SortDirection direction) {
        return direction == SortDirection.ASC ? Sort.Direction.ASC : Sort.Direction.DESC;
    }
}

