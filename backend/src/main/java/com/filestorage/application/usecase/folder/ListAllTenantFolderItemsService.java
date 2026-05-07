package com.filestorage.application.usecase.folder;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.entity.Folder;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.adapter.out.persistence.repository.FolderJpaRepository;
import com.filestorage.domain.model.enums.AdminFolderItemSortBy;
import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.model.enums.FolderItemType;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.enums.SortDirection;
import com.filestorage.domain.model.folder.AdminFolderItemPageView;
import com.filestorage.domain.model.folder.AdminFolderItemView;
import com.filestorage.domain.port.in.folder.ListAllTenantFolderItemsUseCase;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListAllTenantFolderItemsService implements ListAllTenantFolderItemsUseCase {

    private final FolderJpaRepository folderRepository;
    private final FileJpaRepository fileRepository;
    private final FolderSupport folderSupport;

    @Override
    @Transactional(readOnly = true)
    public AdminFolderItemPageView execute(ListAllTenantFolderItemsQuery query) {
        folderSupport.resolveActor(query.actorUserId(), query.actorTenantId());
        requireSuperAdmin(query.actorRole());
        validateQuery(query);

        int page = Math.max(1, query.page());
        int limit = Math.min(100, Math.max(1, query.limit()));
        AdminFolderItemSortBy sortBy = query.sortBy() == null ? AdminFolderItemSortBy.CREATED_AT : query.sortBy();
        SortDirection sortDirection = query.sortDirection() == null ? defaultSortDirection(sortBy) : query.sortDirection();
        FolderItemType effectiveType = query.type() == null && query.status() != null ? FolderItemType.FILE : query.type();

        if (effectiveType == FolderItemType.FOLDER) {
            return listFoldersOnly(query, page, limit, sortBy, sortDirection);
        }
        if (effectiveType == FolderItemType.FILE) {
            return listFilesOnly(query, page, limit, sortBy, sortDirection);
        }

        return listFoldersThenFiles(query, page, limit, sortBy, sortDirection);
    }

    private AdminFolderItemPageView listFoldersOnly(
            ListAllTenantFolderItemsQuery query,
            int page,
            int limit,
            AdminFolderItemSortBy sortBy,
            SortDirection sortDirection
    ) {
        Specification<Folder> spec = folderSpec(query);
        var result = folderRepository.findAll(spec, PageRequest.of(page - 1, limit, toFolderSort(sortBy, sortDirection)));
        List<AdminFolderItemView> data = result.getContent().stream().map(this::toFolderItemView).toList();
        return new AdminFolderItemPageView(data, page, limit, result.getTotalElements(), result.getTotalPages());
    }

    private AdminFolderItemPageView listFilesOnly(
            ListAllTenantFolderItemsQuery query,
            int page,
            int limit,
            AdminFolderItemSortBy sortBy,
            SortDirection sortDirection
    ) {
        Specification<File> spec = fileSpec(query);
        var result = fileRepository.findAll(spec, PageRequest.of(page - 1, limit, toFileSort(sortBy, sortDirection)));
        List<AdminFolderItemView> data = result.getContent().stream().map(this::toFileItemView).toList();
        return new AdminFolderItemPageView(data, page, limit, result.getTotalElements(), result.getTotalPages());
    }

    private AdminFolderItemPageView listFoldersThenFiles(
            ListAllTenantFolderItemsQuery query,
            int page,
            int limit,
            AdminFolderItemSortBy sortBy,
            SortDirection sortDirection
    ) {
        Specification<Folder> folderSpec = folderSpec(query);
        Specification<File> fileSpec = fileSpec(query);

        long folderCount = folderRepository.count(folderSpec);
        long fileCount = fileRepository.count(fileSpec);
        long total = folderCount + fileCount;
        int totalPages = (int) Math.ceil(total / (double) limit);

        long offset = (long) (page - 1) * limit;
        if (offset >= total) {
            return new AdminFolderItemPageView(List.of(), page, limit, total, totalPages);
        }

        List<AdminFolderItemView> data = new ArrayList<>();
        if (offset < folderCount) {
            var folderPage = folderRepository.findAll(
                    folderSpec,
                    PageRequest.of(page - 1, limit, toFolderSort(sortBy, sortDirection))
            );
            data.addAll(folderPage.getContent().stream().map(this::toFolderItemView).toList());
        }

        if (data.size() < limit && fileCount > 0) {
            int remaining = limit - data.size();
            long fileOffset = Math.max(0L, offset - folderCount);
            data.addAll(fetchFileSlice(fileSpec, fileOffset, remaining, sortBy, sortDirection));
        }

        if (data.size() > limit) {
            data = data.subList(0, limit);
        }

        return new AdminFolderItemPageView(data, page, limit, total, totalPages);
    }

    private List<AdminFolderItemView> fetchFileSlice(
            Specification<File> spec,
            long offset,
            int limit,
            AdminFolderItemSortBy sortBy,
            SortDirection sortDirection
    ) {
        int pageIndex = Math.toIntExact(offset / limit);
        int offsetWithinPage = Math.toIntExact(offset % limit);
        int requestedSize = limit + offsetWithinPage;

        var filePage = fileRepository.findAll(spec, PageRequest.of(pageIndex, requestedSize, toFileSort(sortBy, sortDirection)));
        List<AdminFolderItemView> items = filePage.getContent().stream().map(this::toFileItemView).toList();
        if (offsetWithinPage >= items.size()) {
            return List.of();
        }
        int toIndex = Math.min(items.size(), offsetWithinPage + limit);
        return items.subList(offsetWithinPage, toIndex);
    }

    private Specification<Folder> folderSpec(ListAllTenantFolderItemsQuery query) {
        return Specification
                .where(FolderSearchSpecifications.byTenant(query.tenantId()))
                .and(FolderSearchSpecifications.byParentId(query.parentId()))
                .and(FolderSearchSpecifications.byCreatedBy(query.createdBy()))
                .and(FolderSearchSpecifications.bySearch(query.search()));
    }

    private Specification<File> fileSpec(ListAllTenantFolderItemsQuery query) {
        return Specification
                .where(FolderFileSearchSpecifications.byTenant(query.tenantId()))
                .and(FolderFileSearchSpecifications.byFolderId(query.parentId()))
                .and(FolderFileSearchSpecifications.byStatus(query.status()))
                .and(FolderFileSearchSpecifications.byUploadedBy(query.createdBy()))
                .and(FolderFileSearchSpecifications.bySearch(query.search()));
    }

    private AdminFolderItemView toFolderItemView(Folder folder) {
        Folder parent = folder.getParent();
        return new AdminFolderItemView(
                folder.getId(),
                FolderItemType.FOLDER,
                folder.getName(),
                folder.getTenant().getId(),
                folder.getTenant().getName(),
                parent == null ? null : parent.getId(),
                parent == null ? null : parent.getName(),
                0L,
                null,
                null,
                folder.getCreatedBy().getId(),
                folder.getCreatedBy().getFullName(),
                folder.getCreatedAt(),
                folder.getUpdatedAt()
        );
    }

    private AdminFolderItemView toFileItemView(File file) {
        Folder parent = file.getFolder();
        return new AdminFolderItemView(
                file.getId(),
                FolderItemType.FILE,
                file.getOriginalName(),
                file.getTenant().getId(),
                file.getTenant().getName(),
                parent == null ? null : parent.getId(),
                parent == null ? null : parent.getName(),
                file.getSizeBytes() == null ? 0L : file.getSizeBytes(),
                file.getMimeType(),
                file.getStatus(),
                file.getUploadedBy().getId(),
                file.getUploadedBy().getFullName(),
                file.getCreatedAt(),
                file.getUpdatedAt()
        );
    }

    private void requireSuperAdmin(RoleCode actorRole) {
        if (actorRole != RoleCode.SUPER_ADMIN) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
    }

    private void validateQuery(ListAllTenantFolderItemsQuery query) {
        if (query.type() == FolderItemType.FOLDER && query.status() != null) {
            throw new AppException(ErrorCode.INVALID_PAYLOAD);
        }
    }

    private Sort toFolderSort(AdminFolderItemSortBy sortBy, SortDirection direction) {
        if (sortBy == AdminFolderItemSortBy.SIZE_BYTES) {
            return Sort.by(new Sort.Order(Sort.Direction.ASC, "id"));
        }
        return Sort.by(
                new Sort.Order(toSpringDirection(direction), toFolderProperty(sortBy)),
                new Sort.Order(Sort.Direction.ASC, "id")
        );
    }

    private Sort toFileSort(AdminFolderItemSortBy sortBy, SortDirection direction) {
        return Sort.by(
                new Sort.Order(toSpringDirection(direction), toFileProperty(sortBy)),
                new Sort.Order(Sort.Direction.ASC, "id")
        );
    }

    private String toFolderProperty(AdminFolderItemSortBy sortBy) {
        return switch (sortBy) {
            case NAME -> "name";
            case CREATED_AT -> "createdAt";
            case SIZE_BYTES -> "id";
            case TENANT_NAME -> "tenant.name";
        };
    }

    private String toFileProperty(AdminFolderItemSortBy sortBy) {
        return switch (sortBy) {
            case NAME -> "originalName";
            case CREATED_AT -> "createdAt";
            case SIZE_BYTES -> "sizeBytes";
            case TENANT_NAME -> "tenant.name";
        };
    }

    private SortDirection defaultSortDirection(AdminFolderItemSortBy sortBy) {
        return sortBy == AdminFolderItemSortBy.CREATED_AT ? SortDirection.DESC : SortDirection.ASC;
    }

    private Sort.Direction toSpringDirection(SortDirection direction) {
        return direction == SortDirection.ASC ? Sort.Direction.ASC : Sort.Direction.DESC;
    }
}
