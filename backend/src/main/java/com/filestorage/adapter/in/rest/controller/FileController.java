package com.filestorage.adapter.in.rest.controller;

import com.filestorage.adapter.in.rest.dto.request.MoveFileRequest;
import com.filestorage.adapter.in.rest.dto.request.PresignedUploadInitRequest;
import com.filestorage.adapter.in.rest.dto.request.RenameFileRequest;
import com.filestorage.adapter.in.rest.dto.response.ApiResponse;
import com.filestorage.adapter.in.rest.dto.response.ConfirmUploadResponse;
import com.filestorage.adapter.in.rest.dto.response.FileMetadataResponse;
import com.filestorage.adapter.in.rest.dto.response.FileResponse;
import com.filestorage.adapter.in.rest.dto.response.FileStatusResponse;
import com.filestorage.adapter.in.rest.dto.response.PageResponse;
import com.filestorage.adapter.in.rest.dto.response.PresignedDownloadResponse;
import com.filestorage.adapter.in.rest.dto.response.PresignedGetUrlResponse;
import com.filestorage.adapter.in.rest.dto.response.PresignedUploadInitResponse;
import com.filestorage.adapter.in.rest.mapper.FileRestMapper;
import com.filestorage.adapter.in.rest.security.SecurityUser;
import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.model.enums.FileSortBy;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.enums.SortDirection;
import com.filestorage.domain.model.file.ConfirmUploadView;
import com.filestorage.domain.model.file.FileDownloadView;
import com.filestorage.domain.model.file.FilePageView;
import com.filestorage.domain.model.file.FilePresignedGetView;
import com.filestorage.domain.model.file.FileView;
import com.filestorage.domain.model.file.PresignedUploadView;
import com.filestorage.domain.port.in.file.BulkUploadFilesUseCase;
import com.filestorage.domain.port.in.file.ConfirmUploadUseCase;
import com.filestorage.domain.port.in.file.DeleteFileUseCase;
import com.filestorage.domain.port.in.file.DirectDeleteFileUseCase;
import com.filestorage.domain.port.in.file.DownloadFileUseCase;
import com.filestorage.domain.port.in.file.GetFileMetadataUseCase;
import com.filestorage.domain.port.in.file.GetFilePresignedUrlUseCase;
import com.filestorage.domain.port.in.file.InitPresignedUploadUseCase;
import com.filestorage.domain.port.in.file.ListFileMetadataUseCase;
import com.filestorage.domain.port.in.file.MoveFileUseCase;
import com.filestorage.domain.port.in.file.RenameFileUseCase;
import com.filestorage.domain.port.in.file.RestoreFileUseCase;
import com.filestorage.domain.port.in.file.UploadFileUseCase;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import com.filestorage.shared.util.SecurityContextUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FileController {

    private final UploadFileUseCase uploadFileUseCase;
    private final BulkUploadFilesUseCase bulkUploadFilesUseCase;
    private final GetFileMetadataUseCase getFileMetadataUseCase;
    private final ListFileMetadataUseCase listFileMetadataUseCase;
    private final DownloadFileUseCase downloadFileUseCase;
    private final GetFilePresignedUrlUseCase getFilePresignedUrlUseCase;
    private final DeleteFileUseCase deleteFileUseCase;
    private final DirectDeleteFileUseCase directDeleteFileUseCase;
    private final RestoreFileUseCase restoreFileUseCase;
    private final RenameFileUseCase renameFileUseCase;
    private final MoveFileUseCase moveFileUseCase;
    private final InitPresignedUploadUseCase initPresignedUploadUseCase;
    private final ConfirmUploadUseCase confirmUploadUseCase;
    private final FileRestMapper fileRestMapper;

    @PostMapping(value = "/uploads/single", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<FileResponse>> uploadSingle(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) UUID folderId) throws IOException {
        SecurityUser actor = currentUserOrThrow();
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_PAYLOAD);
        }

        String effectiveOriginalName = resolveOriginalName(file.getOriginalFilename());
        FileView view = uploadFileUseCase.execute(new UploadFileUseCase.UploadFileCommand(
                UUID.fromString(actor.getUserId()),
                UUID.fromString(actor.getTenantId()),
                parseRole(actor.getRole()),
                folderId,
                effectiveOriginalName,
                file.getContentType(),
                file.getBytes()
        ));

        return ResponseEntity.status(201).body(ApiResponse.buildSuccess(fileRestMapper.toFileResponse(view)));
    }

    @PostMapping(value = "/uploads/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<List<FileResponse>>> uploadBulk(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(required = false) UUID folderId) throws IOException {
        SecurityUser actor = currentUserOrThrow();
        if (files == null || files.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_PAYLOAD);
        }

        List<BulkUploadFilesUseCase.UploadItem> items = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                throw new AppException(ErrorCode.INVALID_PAYLOAD);
            }
            String effectiveOriginalName = resolveOriginalName(file.getOriginalFilename());
            items.add(new BulkUploadFilesUseCase.UploadItem(
                    effectiveOriginalName,
                    file.getContentType(),
                    file.getBytes()
            ));
        }

        List<FileView> views = bulkUploadFilesUseCase.execute(new BulkUploadFilesUseCase.BulkUploadCommand(
                UUID.fromString(actor.getUserId()),
                UUID.fromString(actor.getTenantId()),
                parseRole(actor.getRole()),
                folderId,
                items
        ));

        List<FileResponse> responses = views.stream().map(fileRestMapper::toFileResponse).toList();
        return ResponseEntity.status(201).body(ApiResponse.buildSuccess(responses));
    }

    @GetMapping("/files/{fileId}/download")
    public ResponseEntity<ApiResponse<PresignedDownloadResponse>> download(
            @PathVariable UUID fileId,
            @RequestParam(required = false) Integer ttlMinutes) {
        SecurityUser actor = currentUserOrThrow();
        FileDownloadView view = downloadFileUseCase.execute(new DownloadFileUseCase.DownloadFileQuery(
                UUID.fromString(actor.getUserId()),
                UUID.fromString(actor.getTenantId()),
                parseRole(actor.getRole()),
                fileId,
                ttlMinutes
        ));

        return ResponseEntity.ok(ApiResponse.buildSuccess(new PresignedDownloadResponse(
                view.downloadUrl(),
                view.expiresAt(),
                view.originalName(),
                view.mimeType(),
                view.sizeBytes()
        )));
    }

    @GetMapping("/files/{fileId}")
    public ResponseEntity<ApiResponse<FileMetadataResponse>> getFileMetadata(@PathVariable UUID fileId) {
        SecurityUser actor = currentUserOrThrow();
        FileView view = getFileMetadataUseCase.execute(new GetFileMetadataUseCase.GetFileMetadataQuery(
                UUID.fromString(actor.getUserId()),
                UUID.fromString(actor.getTenantId()),
                parseRole(actor.getRole()),
                fileId
        ));

        return ResponseEntity.ok(ApiResponse.buildSuccess(fileRestMapper.toFileMetadataResponse(view)));
    }

    @GetMapping("/files")
    public ResponseEntity<ApiResponse<PageResponse<FileMetadataResponse>>> listFileMetadata(
            @RequestParam(required = false) UUID folderId,
            @RequestParam(required = false) FileStatus status,
            @RequestParam(required = false) UUID uploadedBy,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) List<FileSortBy> sortBy,
            @RequestParam(required = false) List<SortDirection> sortDirection
    ) {
        SecurityUser actor = currentUserOrThrow();
        FilePageView view = listFileMetadataUseCase.execute(new ListFileMetadataUseCase.ListFileMetadataQuery(
                UUID.fromString(actor.getUserId()),
                UUID.fromString(actor.getTenantId()),
                parseRole(actor.getRole()),
                folderId,
                status,
                uploadedBy,
                tenantId,
                search,
                page,
                limit,
                sortBy,
                sortDirection
        ));

        return ResponseEntity.ok(ApiResponse.buildSuccess(fileRestMapper.toMetadataPageResponse(view)));
    }

    @GetMapping("/files/{fileId}/presigned-get-url")
    public ResponseEntity<ApiResponse<PresignedGetUrlResponse>> getPresignedGetUrl(
            @PathVariable UUID fileId,
            @RequestParam(required = false) Integer ttlMinutes) {
        SecurityUser actor = currentUserOrThrow();
        FilePresignedGetView view = getFilePresignedUrlUseCase.execute(new GetFilePresignedUrlUseCase.GetFilePresignedUrlQuery(
                UUID.fromString(actor.getUserId()),
                UUID.fromString(actor.getTenantId()),
                parseRole(actor.getRole()),
                fileId,
                ttlMinutes
        ));

        return ResponseEntity.ok(ApiResponse.buildSuccess(new PresignedGetUrlResponse(
                view.getUrl(),
                view.expiresAt(),
                view.originalName(),
                view.mimeType(),
                view.sizeBytes()
        )));
    }

    @DeleteMapping("/files/{fileId}")
    public ResponseEntity<ApiResponse<FileResponse>> delete(@PathVariable UUID fileId) {
        SecurityUser actor = currentUserOrThrow();
        FileView view = deleteFileUseCase.execute(new DeleteFileUseCase.DeleteFileCommand(
                UUID.fromString(actor.getUserId()),
                UUID.fromString(actor.getTenantId()),
                parseRole(actor.getRole()),
                fileId
        ));
        return ResponseEntity.ok(ApiResponse.buildSuccess(fileRestMapper.toFileResponse(view)));
    }

    @DeleteMapping("/files/{fileId}/direct")
    public ResponseEntity<Void> hardDelete(@PathVariable UUID fileId) {
        SecurityUser actor = currentUserOrThrow();
        directDeleteFileUseCase.execute(new DirectDeleteFileUseCase.DirectDeleteFileCommand(
                UUID.fromString(actor.getUserId()),
                UUID.fromString(actor.getTenantId()),
                parseRole(actor.getRole()),
                fileId
        ));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/files/{fileId}/restore")
    public ResponseEntity<ApiResponse<FileResponse>> restore(@PathVariable UUID fileId) {
        SecurityUser actor = currentUserOrThrow();
        FileView view = restoreFileUseCase.execute(new RestoreFileUseCase.RestoreFileCommand(
                UUID.fromString(actor.getUserId()),
                UUID.fromString(actor.getTenantId()),
                parseRole(actor.getRole()),
                fileId
        ));
        return ResponseEntity.ok(ApiResponse.buildSuccess(fileRestMapper.toFileResponse(view)));
    }

    @PatchMapping("/files/{fileId}")
    public ResponseEntity<ApiResponse<FileMetadataResponse>> renameFile(
            @PathVariable UUID fileId,
            @RequestBody @Valid RenameFileRequest request
    ) {
        SecurityUser actor = currentUserOrThrow();
        FileView view = renameFileUseCase.execute(new RenameFileUseCase.RenameFileCommand(
                UUID.fromString(actor.getUserId()),
                UUID.fromString(actor.getTenantId()),
                parseRole(actor.getRole()),
                fileId,
                request.originalName()
        ));

        return ResponseEntity.ok(ApiResponse.buildSuccess(fileRestMapper.toFileMetadataResponse(view)));
    }

    @PatchMapping("/files/{fileId}/move")
    public ResponseEntity<ApiResponse<FileMetadataResponse>> moveFile(
            @PathVariable UUID fileId,
            @RequestBody MoveFileRequest request
    ) {
        if (request == null) {
            throw new AppException(ErrorCode.INVALID_PAYLOAD);
        }
        SecurityUser actor = currentUserOrThrow();
        FileView view = moveFileUseCase.execute(new MoveFileUseCase.MoveFileCommand(
                UUID.fromString(actor.getUserId()),
                UUID.fromString(actor.getTenantId()),
                parseRole(actor.getRole()),
                fileId,
                request.folderId()
        ));

        return ResponseEntity.ok(ApiResponse.buildSuccess(fileRestMapper.toFileMetadataResponse(view)));
    }

    // ─── Presigned Upload ────────────────────────────────────────────────────────

    @PostMapping("/uploads/presigned")
    public ResponseEntity<ApiResponse<PresignedUploadInitResponse>> initPresignedUpload(
            @RequestBody @Valid PresignedUploadInitRequest request) {
        SecurityUser actor = currentUserOrThrow();
        PresignedUploadView view = initPresignedUploadUseCase.execute(new InitPresignedUploadUseCase.InitPresignedUploadCommand(
                UUID.fromString(actor.getUserId()),
                UUID.fromString(actor.getTenantId()),
                parseRole(actor.getRole()),
                request.folderId(),
                request.originalName(),
                request.mimeType(),
                request.sizeBytes()
        ));
        return ResponseEntity.status(201).body(ApiResponse.buildSuccess(fileRestMapper.toPresignedUploadInitResponse(view)));
    }

    @PostMapping("/files/{fileId}/confirm-upload")
    public ResponseEntity<ApiResponse<ConfirmUploadResponse>> confirmUpload(@PathVariable UUID fileId) {
        SecurityUser actor = currentUserOrThrow();
        ConfirmUploadView view = confirmUploadUseCase.execute(new ConfirmUploadUseCase.ConfirmUploadCommand(
                UUID.fromString(actor.getUserId()),
                UUID.fromString(actor.getTenantId()),
                parseRole(actor.getRole()),
                fileId
        ));
        return ResponseEntity.ok(ApiResponse.buildSuccess(fileRestMapper.toConfirmUploadResponse(view)));
    }

    @GetMapping("/files/{fileId}/status")
    public ResponseEntity<ApiResponse<FileStatusResponse>> getFileStatus(@PathVariable UUID fileId) {
        SecurityUser actor = currentUserOrThrow();
        FileView view = getFileMetadataUseCase.execute(new GetFileMetadataUseCase.GetFileMetadataQuery(
                UUID.fromString(actor.getUserId()),
                UUID.fromString(actor.getTenantId()),
                parseRole(actor.getRole()),
                fileId
        ));
        return ResponseEntity.ok(ApiResponse.buildSuccess(fileRestMapper.toFileStatusResponse(view)));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private SecurityUser currentUserOrThrow() {
        return SecurityContextUtil.getCurrentUser().orElseThrow(() -> new AppException(ErrorCode.ACCESS_DENIED));
    }

    private RoleCode parseRole(String role) {
        try {
            return RoleCode.valueOf(role);
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
    }

    private String resolveOriginalName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "upload-" + UUID.randomUUID();
        }
        return originalFilename.trim();
    }
}

