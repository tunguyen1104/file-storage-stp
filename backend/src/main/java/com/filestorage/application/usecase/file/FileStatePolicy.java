package com.filestorage.application.usecase.file;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;

final class FileStatePolicy {

    private FileStatePolicy() {
    }

    static void requireActive(File file) {
        if (file.getStatus() != FileStatus.ACTIVE) {
            throw new AppException(ErrorCode.FILE_STATUS_INVALID);
        }
    }

    static void requirePendingDelete(File file) {
        if (file.getStatus() != FileStatus.PENDING_DELETE) {
            throw new AppException(ErrorCode.FILE_STATUS_INVALID);
        }
    }
}

