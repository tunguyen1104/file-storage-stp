package com.filestorage.domain.port.in.file;

import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.file.FileView;

import java.util.UUID;

public interface UploadFileUseCase {

	FileView execute(UploadFileCommand command);

	record UploadFileCommand(
			UUID actorUserId,
			UUID actorTenantId,
			RoleCode actorRole,
			UUID folderId,
			String originalName,
			String contentType,
			byte[] content
	) {
	}
}

