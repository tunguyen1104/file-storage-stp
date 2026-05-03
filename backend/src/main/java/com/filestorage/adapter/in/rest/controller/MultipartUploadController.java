package com.filestorage.adapter.in.rest.controller;

import com.filestorage.adapter.in.rest.dto.request.CompleteMultipartRequest;
import com.filestorage.adapter.in.rest.dto.request.CompletePartRequest;
import com.filestorage.adapter.in.rest.dto.request.InitMultipartUploadRequest;
import com.filestorage.adapter.in.rest.dto.response.ApiResponse;
import com.filestorage.adapter.in.rest.dto.response.MultipartCompleteResponse;
import com.filestorage.adapter.in.rest.dto.response.MultipartInitResponse;
import com.filestorage.adapter.in.rest.dto.response.MultipartSessionResponse;
import com.filestorage.adapter.in.rest.dto.response.PartCompletionResponse;
import com.filestorage.adapter.in.rest.dto.response.PartUrlResponse;
import com.filestorage.adapter.in.rest.mapper.MultipartUploadRestMapper;
import com.filestorage.adapter.in.rest.security.SecurityUser;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.file.MultipartCompleteView;
import com.filestorage.domain.model.file.MultipartSessionView;
import com.filestorage.domain.model.file.MultipartUploadInitView;
import com.filestorage.domain.model.file.PartCompletionView;
import com.filestorage.domain.model.file.PartPresignedUrlView;
import com.filestorage.domain.port.in.file.AbortMultipartUploadUseCase;
import com.filestorage.domain.port.in.file.CompleteMultipartUploadUseCase;
import com.filestorage.domain.port.in.file.CompletePartUploadUseCase;
import com.filestorage.domain.port.in.file.GetMultipartUploadSessionUseCase;
import com.filestorage.domain.port.in.file.GetPartPresignedUrlUseCase;
import com.filestorage.domain.port.in.file.InitMultipartUploadUseCase;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import com.filestorage.shared.util.SecurityContextUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MultipartUploadController {

    private final InitMultipartUploadUseCase initMultipartUploadUseCase;
    private final GetPartPresignedUrlUseCase getPartPresignedUrlUseCase;
    private final CompletePartUploadUseCase completePartUploadUseCase;
    private final CompleteMultipartUploadUseCase completeMultipartUploadUseCase;
    private final AbortMultipartUploadUseCase abortMultipartUploadUseCase;
    private final GetMultipartUploadSessionUseCase getMultipartUploadSessionUseCase;
    private final MultipartUploadRestMapper mapper;

    /**
     * Phase 1 — Initiate a multipart upload session.
     */
    @PostMapping("/uploads/multipart/init")
    public ResponseEntity<ApiResponse<MultipartInitResponse>> initMultipartUpload(
            @RequestBody @Valid InitMultipartUploadRequest request) {
        SecurityUser actor = currentUserOrThrow();
        MultipartUploadInitView view = initMultipartUploadUseCase.execute(
                new InitMultipartUploadUseCase.InitMultipartUploadCommand(
                        UUID.fromString(actor.getUserId()),
                        UUID.fromString(actor.getTenantId()),
                        parseRole(actor.getRole()),
                        request.getFolderId(),
                        request.getOriginalName(),
                        request.getMimeType(),
                        request.getTotalSizeBytes(),
                        request.getTotalParts()
                )
        );
        return ResponseEntity.status(201).body(ApiResponse.buildSuccess(mapper.toInitResponse(view)));
    }

    /**
     * Phase 2a — Get a presigned PUT URL for a single part.
     */
    @GetMapping("/uploads/multipart/{uploadId}/part-url")
    public ResponseEntity<ApiResponse<PartUrlResponse>> getPartUrl(
            @PathVariable String uploadId,
            @RequestParam int partNumber) {
        SecurityUser actor = currentUserOrThrow();
        PartPresignedUrlView view = getPartPresignedUrlUseCase.execute(
                new GetPartPresignedUrlUseCase.GetPartPresignedUrlCommand(
                        UUID.fromString(actor.getUserId()),
                        UUID.fromString(actor.getTenantId()),
                        parseRole(actor.getRole()),
                        uploadId,
                        partNumber
                )
        );
        return ResponseEntity.ok(ApiResponse.buildSuccess(mapper.toPartUrlResponse(view)));
    }

    /**
     * Phase 2b — Acknowledge a completed part upload.
     */
    @PostMapping("/uploads/multipart/{uploadId}/parts/{partNumber}/complete")
    public ResponseEntity<ApiResponse<PartCompletionResponse>> completePart(
            @PathVariable String uploadId,
            @PathVariable int partNumber,
            @RequestBody @Valid CompletePartRequest request) {
        SecurityUser actor = currentUserOrThrow();
        PartCompletionView view = completePartUploadUseCase.execute(
                new CompletePartUploadUseCase.CompletePartUploadCommand(
                        UUID.fromString(actor.getUserId()),
                        UUID.fromString(actor.getTenantId()),
                        uploadId,
                        partNumber,
                        request.getEtag()
                )
        );
        return ResponseEntity.ok(ApiResponse.buildSuccess(mapper.toPartCompletionResponse(view)));
    }

    /**
     * Phase 3 — Complete the multipart upload (assemble all parts).
     */
    @PostMapping("/uploads/multipart/{uploadId}/complete")
    public ResponseEntity<ApiResponse<MultipartCompleteResponse>> completeMultipart(
            @PathVariable String uploadId,
            @RequestBody @Valid CompleteMultipartRequest request) {
        SecurityUser actor = currentUserOrThrow();

        java.util.List<CompleteMultipartUploadUseCase.PartInfo> parts = request.getParts().stream()
                .map(p -> new CompleteMultipartUploadUseCase.PartInfo(p.getPartNumber(), p.getEtag()))
                .toList();

        MultipartCompleteView view = completeMultipartUploadUseCase.execute(
                new CompleteMultipartUploadUseCase.CompleteMultipartUploadCommand(
                        UUID.fromString(actor.getUserId()),
                        UUID.fromString(actor.getTenantId()),
                        parseRole(actor.getRole()),
                        uploadId,
                        parts
                )
        );
        return ResponseEntity.ok(ApiResponse.buildSuccess(mapper.toCompleteResponse(view)));
    }

    /**
     * Abort an in-progress multipart upload session.
     */
    @DeleteMapping("/uploads/multipart/{uploadId}")
    public ResponseEntity<ApiResponse<AbortMultipartUploadUseCase.AbortView>> abortMultipart(
            @PathVariable String uploadId) {
        SecurityUser actor = currentUserOrThrow();
        AbortMultipartUploadUseCase.AbortView view = abortMultipartUploadUseCase.execute(
                new AbortMultipartUploadUseCase.AbortMultipartUploadCommand(
                        UUID.fromString(actor.getUserId()),
                        UUID.fromString(actor.getTenantId()),
                        parseRole(actor.getRole()),
                        uploadId
                )
        );
        return ResponseEntity.ok(ApiResponse.buildSuccess(view));
    }

    /**
     * Get session status — supports resume-upload scenarios.
     */
    @GetMapping("/uploads/multipart/{uploadId}")
    public ResponseEntity<ApiResponse<MultipartSessionResponse>> getSession(
            @PathVariable String uploadId) {
        SecurityUser actor = currentUserOrThrow();
        MultipartSessionView view = getMultipartUploadSessionUseCase.execute(
                new GetMultipartUploadSessionUseCase.GetMultipartUploadSessionQuery(
                        UUID.fromString(actor.getUserId()),
                        UUID.fromString(actor.getTenantId()),
                        parseRole(actor.getRole()),
                        uploadId
                )
        );
        return ResponseEntity.ok(ApiResponse.buildSuccess(mapper.toSessionResponse(view)));
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private SecurityUser currentUserOrThrow() {
        return SecurityContextUtil.getCurrentUser()
                .orElseThrow(() -> new AppException(ErrorCode.ACCESS_DENIED));
    }

    private RoleCode parseRole(String role) {
        try {
            return RoleCode.valueOf(role);
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
    }
}
