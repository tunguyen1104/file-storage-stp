package com.filestorage.adapter.in.rest.controller;

import com.filestorage.adapter.in.rest.dto.request.CreateRagChatSessionRequest;
import com.filestorage.adapter.in.rest.dto.request.RagChatRequest;
import com.filestorage.adapter.in.rest.dto.request.RenameRagChatSessionRequest;
import com.filestorage.adapter.in.rest.dto.response.ApiResponse;
import com.filestorage.adapter.in.rest.dto.response.PageResponse;
import com.filestorage.adapter.in.rest.dto.response.RagChatMessageResponse;
import com.filestorage.adapter.in.rest.dto.response.RagChatResponse;
import com.filestorage.adapter.in.rest.dto.response.RagChatSessionDeletedResponse;
import com.filestorage.adapter.in.rest.dto.response.RagChatSessionResponse;
import com.filestorage.adapter.in.rest.mapper.RagChatRestMapper;
import com.filestorage.adapter.in.rest.security.SecurityUser;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.rag.RagChatMessagePageView;
import com.filestorage.domain.model.rag.RagChatSessionPageView;
import com.filestorage.domain.model.rag.RagChatSessionView;
import com.filestorage.domain.model.rag.RagChatView;
import com.filestorage.domain.model.rag.RagDeletedSessionView;
import com.filestorage.domain.port.in.rag.ChatRagUseCase;
import com.filestorage.domain.port.in.rag.CreateRagChatSessionUseCase;
import com.filestorage.domain.port.in.rag.DeleteRagChatSessionUseCase;
import com.filestorage.domain.port.in.rag.GetRagChatMessagesUseCase;
import com.filestorage.domain.port.in.rag.ListRagChatSessionsUseCase;
import com.filestorage.domain.port.in.rag.RenameRagChatSessionUseCase;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import com.filestorage.shared.util.SecurityContextUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rag/chat/sessions")
@RequiredArgsConstructor
public class RagChatController {

    private final CreateRagChatSessionUseCase createSessionUseCase;
    private final ListRagChatSessionsUseCase listSessionsUseCase;
    private final GetRagChatMessagesUseCase getMessagesUseCase;
    private final RenameRagChatSessionUseCase renameSessionUseCase;
    private final DeleteRagChatSessionUseCase deleteSessionUseCase;
    private final ChatRagUseCase chatRagUseCase;
    private final RagChatRestMapper mapper;

    @PostMapping
    public ResponseEntity<ApiResponse<RagChatSessionResponse>> createSession(
            @RequestBody(required = false) @Valid CreateRagChatSessionRequest request
    ) {
        SecurityUser actor = currentUserOrThrow();
        RagChatSessionView view = createSessionUseCase.execute(new CreateRagChatSessionUseCase.CreateRagChatSessionCommand(
                parseUuid(actor.getUserId()),
                parseUuid(actor.getTenantId()),
                parseRole(actor.getRole()),
                request == null ? null : request.title()
        ));
        return ResponseEntity.status(201).body(ApiResponse.buildSuccess(mapper.toSessionResponse(view)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<RagChatSessionResponse>>> listSessions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        SecurityUser actor = currentUserOrThrow();
        RagChatSessionPageView view = listSessionsUseCase.execute(new ListRagChatSessionsUseCase.ListRagChatSessionsQuery(
                parseUuid(actor.getUserId()),
                parseUuid(actor.getTenantId()),
                parseRole(actor.getRole()),
                page,
                size
        ));
        return ResponseEntity.ok(ApiResponse.buildSuccess(mapper.toSessionPageResponse(view)));
    }

    @PatchMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<RagChatSessionResponse>> renameSession(
            @PathVariable UUID sessionId,
            @RequestBody @Valid RenameRagChatSessionRequest request
    ) {
        SecurityUser actor = currentUserOrThrow();
        RagChatSessionView view = renameSessionUseCase.execute(new RenameRagChatSessionUseCase.RenameRagChatSessionCommand(
                parseUuid(actor.getUserId()),
                parseUuid(actor.getTenantId()),
                parseRole(actor.getRole()),
                sessionId,
                request.title()
        ));
        return ResponseEntity.ok(ApiResponse.buildSuccess(mapper.toSessionResponse(view)));
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<RagChatSessionDeletedResponse>> deleteSession(@PathVariable UUID sessionId) {
        SecurityUser actor = currentUserOrThrow();
        RagDeletedSessionView view = deleteSessionUseCase.execute(new DeleteRagChatSessionUseCase.DeleteRagChatSessionCommand(
                parseUuid(actor.getUserId()),
                parseUuid(actor.getTenantId()),
                parseRole(actor.getRole()),
                sessionId
        ));
        return ResponseEntity.ok(ApiResponse.buildSuccess(mapper.toDeletedSessionResponse(view)));
    }

    @GetMapping("/{sessionId}/messages")
    public ResponseEntity<ApiResponse<PageResponse<RagChatMessageResponse>>> getMessages(
            @PathVariable UUID sessionId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        SecurityUser actor = currentUserOrThrow();
        RagChatMessagePageView view = getMessagesUseCase.execute(new GetRagChatMessagesUseCase.GetRagChatMessagesQuery(
                parseUuid(actor.getUserId()),
                parseUuid(actor.getTenantId()),
                parseRole(actor.getRole()),
                sessionId,
                page,
                size
        ));
        return ResponseEntity.ok(ApiResponse.buildSuccess(mapper.toMessagePageResponse(view)));
    }

    @PostMapping("/{sessionId}/messages")
    public ResponseEntity<ApiResponse<RagChatResponse>> chat(
            @PathVariable UUID sessionId,
            @RequestBody @Valid RagChatRequest request
    ) {
        SecurityUser actor = currentUserOrThrow();
        RagChatView view = chatRagUseCase.execute(new ChatRagUseCase.ChatRagCommand(
                parseUuid(actor.getUserId()),
                parseUuid(actor.getTenantId()),
                parseRole(actor.getRole()),
                sessionId,
                request.message(),
                request.topK()
        ));
        return ResponseEntity.ok(ApiResponse.buildSuccess(mapper.toChatResponse(view)));
    }

    private SecurityUser currentUserOrThrow() {
        return SecurityContextUtil.getCurrentUser().orElseThrow(() -> new AppException(ErrorCode.ACCESS_DENIED));
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException ex) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
    }

    private RoleCode parseRole(String role) {
        try {
            return RoleCode.valueOf(role);
        } catch (RuntimeException ex) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
    }
}
