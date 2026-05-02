package com.filestorage.domain.model.file;

import com.filestorage.domain.model.enums.FileStatus;

import java.util.UUID;

/**
 * Domain view returned after the multipart upload has been assembled and
 * the virus-scan pipeline has been triggered.
 *
 * @param fileId  UUID of the assembled file (status = SCANNING)
 * @param status  new file status — always {@link FileStatus#SCANNING} on success
 */
public record MultipartCompleteView(
        UUID fileId,
        FileStatus status
) {}
