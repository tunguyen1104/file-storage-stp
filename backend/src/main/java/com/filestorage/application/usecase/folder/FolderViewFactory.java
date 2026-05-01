package com.filestorage.application.usecase.folder;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.entity.Folder;
import com.filestorage.domain.model.enums.FolderItemType;
import com.filestorage.domain.model.folder.BreadcrumbView;
import com.filestorage.domain.model.folder.FolderItemView;
import com.filestorage.domain.model.folder.FolderView;

import java.util.List;
import java.util.UUID;

final class FolderViewFactory {

    private FolderViewFactory() {
    }

    static FolderView toFolderView(Folder folder, long sizeBytes, List<BreadcrumbView> breadcrumbs) {
        return new FolderView(
                folder.getId(),
                folder.getName(),
                toParentId(folder),
                sizeBytes,
                folder.getTenant().getId(),
                toOwnerId(folder),
                folder.getCreatedBy().getId(),
                folder.getCreatedAt(),
                folder.getUpdatedAt(),
                breadcrumbs
        );
    }

    static FolderItemView toFolderItemView(Folder folder) {
        return new FolderItemView(
                folder.getId(),
                FolderItemType.FOLDER,
                folder.getName(),
                toParentId(folder),
                0L,
                null,
                folder.getCreatedAt()
        );
    }

    static FolderItemView toFileItemView(File file) {
        return new FolderItemView(
                file.getId(),
                FolderItemType.FILE,
                file.getOriginalName(),
                file.getFolder() == null ? null : file.getFolder().getId(),
                file.getSizeBytes() == null ? 0L : file.getSizeBytes(),
                file.getMimeType(),
                file.getCreatedAt()
        );
    }

    private static UUID toParentId(Folder folder) {
        return folder.getParent() == null ? null : folder.getParent().getId();
    }

    private static UUID toOwnerId(Folder folder) {
        return folder.getOwner() == null ? null : folder.getOwner().getId();
    }
}

