package com.filestorage.application.usecase.folder;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.entity.Folder;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.adapter.out.persistence.repository.FolderJpaRepository;
import com.filestorage.application.usecase.share.ShareResolutionService;
import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.model.enums.FolderItemType;
import com.filestorage.domain.model.enums.FolderSortBy;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.enums.SortDirection;
import com.filestorage.domain.model.folder.FolderItemPageView;
import com.filestorage.domain.model.folder.FolderItemView;
import com.filestorage.domain.port.in.folder.ListFolderChildrenUseCase;
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
public class ListFolderChildrenService implements ListFolderChildrenUseCase {

    private final FolderJpaRepository folderRepository;
    private final FileJpaRepository fileRepository;
    private final FolderSupport folderSupport;
    private final FolderAccessPolicy folderAccessPolicy;
    private final ShareResolutionService shareResolutionService;

    @Override
    @Transactional(readOnly = true)
    public FolderItemPageView execute(ListFolderChildrenQuery query) {
        var actor = folderSupport.resolveActor(query.actorUserId(), query.actorTenantId());
        Folder parent = folderSupport.findReadableFolder(query.folderId(), actor.getTenant().getId(), query.actorRole());
        folderAccessPolicy.requireCanRead(parent, actor.getId(), actor.getTenant().getId(), query.actorRole());

        return listItems(
                actor.getId(),
                actor.getTenant().getId(),
                query.actorRole(),
                parent.getId(),
                query.page(),
                query.size(),
                query.sortBy(),
                query.sortDirection(),
                query.type(),
                query.search(),
                false
        );
    }

    FolderItemPageView listItems(
            UUID actorUserId,
            UUID tenantId,
            RoleCode actorRole,
            UUID parentId,
            int page,
            int size,
            FolderSortBy sortBy,
            SortDirection sortDirection,
            FolderItemType type,
            String search,
            boolean restrictMemberToOwnedItems
    ) {
        int effectivePage = Math.max(1, page);
        int effectiveSize = Math.min(100, Math.max(1, size));
        FolderSortBy effectiveSortBy = sortBy == null ? FolderSortBy.CREATED_AT : sortBy;
        SortDirection effectiveDirection = sortDirection == null ? defaultSortDirection(effectiveSortBy) : sortDirection;

        UUID createdByFilter = restrictMemberToOwnedItems && actorRole == RoleCode.MEMBER ? actorUserId : null;

        if (type == FolderItemType.FOLDER) {
            return listFoldersOnly(actorUserId, tenantId, actorRole, parentId, createdByFilter, search, effectivePage, effectiveSize, effectiveSortBy, effectiveDirection);
        }
        if (type == FolderItemType.FILE) {
            return listFilesOnly(actorUserId, tenantId, actorRole, parentId, createdByFilter, search, effectivePage, effectiveSize, effectiveSortBy, effectiveDirection);
        }

        return listFoldersThenFiles(actorUserId, tenantId, actorRole, parentId, createdByFilter, search, effectivePage, effectiveSize, effectiveSortBy, effectiveDirection);
    }

    private FolderItemPageView listFoldersOnly(
            UUID actorUserId,
            UUID tenantId,
            RoleCode actorRole,
            UUID parentId,
            UUID createdBy,
            String search,
            int page,
            int size,
            FolderSortBy sortBy,
            SortDirection sortDirection
    ) {
        Specification<Folder> spec = Specification
                .where(FolderSearchSpecifications.byTenant(tenantId))
                .and(FolderSearchSpecifications.byParentId(parentId))
                .and(FolderSearchSpecifications.byCreatedBy(createdBy))
                .and(FolderSearchSpecifications.bySearch(search));

        var result = folderRepository.findAll(spec, PageRequest.of(page - 1, size, toFolderSort(sortBy, sortDirection)));
        List<FolderItemView> data = result.getContent().stream()
                .map(folder -> toFolderItemView(actorUserId, tenantId, actorRole, folder))
                .toList();
        return new FolderItemPageView(data, page, size, result.getTotalElements(), result.getTotalPages());
    }

    private FolderItemPageView listFilesOnly(
            UUID actorUserId,
            UUID tenantId,
            RoleCode actorRole,
            UUID parentId,
            UUID createdBy,
            String search,
            int page,
            int size,
            FolderSortBy sortBy,
            SortDirection sortDirection
    ) {
        Specification<File> spec = Specification
                .where(FolderFileSearchSpecifications.byTenant(tenantId))
                .and(FolderFileSearchSpecifications.byFolderId(parentId))
                .and(FolderFileSearchSpecifications.byStatus(FileStatus.ACTIVE))
                .and(FolderFileSearchSpecifications.byUploadedBy(createdBy))
                .and(FolderFileSearchSpecifications.bySearch(search));

        var result = fileRepository.findAll(spec, PageRequest.of(page - 1, size, toFileSort(sortBy, sortDirection)));
        List<FolderItemView> data = result.getContent().stream()
                .map(file -> toFileItemView(actorUserId, tenantId, actorRole, file))
                .toList();
        return new FolderItemPageView(data, page, size, result.getTotalElements(), result.getTotalPages());
    }

    private FolderItemPageView listFoldersThenFiles(
            UUID actorUserId,
            UUID tenantId,
            RoleCode actorRole,
            UUID parentId,
            UUID createdBy,
            String search,
            int page,
            int size,
            FolderSortBy sortBy,
            SortDirection sortDirection
    ) {
        Specification<Folder> folderSpec = Specification
                .where(FolderSearchSpecifications.byTenant(tenantId))
                .and(FolderSearchSpecifications.byParentId(parentId))
                .and(FolderSearchSpecifications.byCreatedBy(createdBy))
                .and(FolderSearchSpecifications.bySearch(search));

        Specification<File> fileSpec = Specification
                .where(FolderFileSearchSpecifications.byTenant(tenantId))
                .and(FolderFileSearchSpecifications.byFolderId(parentId))
                .and(FolderFileSearchSpecifications.byStatus(FileStatus.ACTIVE))
                .and(FolderFileSearchSpecifications.byUploadedBy(createdBy))
                .and(FolderFileSearchSpecifications.bySearch(search));

        long folderCount = folderRepository.count(folderSpec);
        long fileCount = fileRepository.count(fileSpec);
        long total = folderCount + fileCount;
        int totalPages = (int) Math.ceil(total / (double) size);

        int offset = (page - 1) * size;
        if (offset >= total) {
            return new FolderItemPageView(List.of(), page, size, total, totalPages);
        }

        List<FolderItemView> data = new ArrayList<>();
        if (offset < folderCount) {
            var folderPage = folderRepository.findAll(folderSpec, PageRequest.of(page - 1, size, toFolderSort(sortBy, sortDirection)));
            data.addAll(folderPage.getContent().stream()
                    .map(folder -> toFolderItemView(actorUserId, tenantId, actorRole, folder))
                    .toList());
        }

        if (data.size() < size && fileCount > 0) {
            int remaining = size - data.size();
            int fileOffset = Math.max(0, offset - (int) folderCount);
            data.addAll(fetchFileSlice(actorUserId, tenantId, actorRole, fileSpec, fileOffset, remaining, sortBy, sortDirection));
        }

        if (data.size() > size) {
            data = data.subList(0, size);
        }

        return new FolderItemPageView(data, page, size, total, totalPages);
    }

    private List<FolderItemView> fetchFileSlice(
            UUID actorUserId,
            UUID tenantId,
            RoleCode actorRole,
            Specification<File> spec,
            int offset,
            int size,
            FolderSortBy sortBy,
            SortDirection sortDirection
    ) {
        int pageIndex = offset / size;
        int offsetWithinPage = offset % size;
        int requestedSize = size + offsetWithinPage;

        var filePage = fileRepository.findAll(spec, PageRequest.of(pageIndex, requestedSize, toFileSort(sortBy, sortDirection)));
        List<FolderItemView> items = filePage.getContent().stream()
                .map(file -> toFileItemView(actorUserId, tenantId, actorRole, file))
                .toList();
        if (offsetWithinPage >= items.size()) {
            return List.of();
        }
        int toIndex = Math.min(items.size(), offsetWithinPage + size);
        return items.subList(offsetWithinPage, toIndex);
    }

    private FolderItemView toFolderItemView(UUID actorUserId, UUID tenantId, RoleCode actorRole, Folder folder) {
        return FolderViewFactory.toFolderItemView(
                folder,
                shareResolutionService.resolveFolder(actorUserId, tenantId, actorRole, folder)
        );
    }

    private FolderItemView toFileItemView(UUID actorUserId, UUID tenantId, RoleCode actorRole, File file) {
        return FolderViewFactory.toFileItemView(
                file,
                shareResolutionService.resolveFile(actorUserId, tenantId, actorRole, file)
        );
    }

    private Sort toFolderSort(FolderSortBy sortBy, SortDirection direction) {
        return Sort.by(new Sort.Order(toSpringDirection(direction), toFolderProperty(sortBy)));
    }

    private Sort toFileSort(FolderSortBy sortBy, SortDirection direction) {
        return Sort.by(new Sort.Order(toSpringDirection(direction), toFileProperty(sortBy)));
    }

    private String toFolderProperty(FolderSortBy sortBy) {
        return sortBy == FolderSortBy.NAME ? "name" : "createdAt";
    }

    private String toFileProperty(FolderSortBy sortBy) {
        return switch (sortBy) {
            case NAME -> "originalName";
            case SIZE_BYTES -> "sizeBytes";
            case CREATED_AT -> "createdAt";
        };
    }

    private SortDirection defaultSortDirection(FolderSortBy sortBy) {
        return sortBy == FolderSortBy.CREATED_AT ? SortDirection.DESC : SortDirection.ASC;
    }

    private Sort.Direction toSpringDirection(SortDirection direction) {
        return direction == SortDirection.ASC ? Sort.Direction.ASC : Sort.Direction.DESC;
    }
}
