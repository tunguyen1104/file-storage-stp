package com.filestorage.adapter.in.rest.mapper;

import com.filestorage.adapter.in.rest.dto.response.PageResponse;
import com.filestorage.adapter.in.rest.dto.response.audit.AuditLogResponse;
import com.filestorage.domain.model.audit.AuditLogPage;
import com.filestorage.domain.model.audit.AuditLogView;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AuditLogRestMapper {

    public AuditLogResponse toResponse(AuditLogView view) {
        return new AuditLogResponse(
                view.id(),
                view.tenantId(),
                view.userId(),
                view.userEmail(),
                view.fileId(),
                view.action(),
                view.action() == null ? null : view.action().severity(),
                view.actorRole(),
                view.ipAddress(),
                view.userAgent(),
                view.requestId(),
                view.extraData(),
                view.createdAt()
        );
    }

    public PageResponse<AuditLogResponse> toPageResponse(AuditLogPage page) {
        List<AuditLogResponse> items = page.data().stream().map(this::toResponse).toList();
        return PageResponse.<AuditLogResponse>builder()
                .data(items)
                .currentPage(page.currentPage())
                .pageSize(page.pageSize())
                .totalElements(page.totalElements())
                .totalPages(page.totalPages())
                .build();
    }
}
