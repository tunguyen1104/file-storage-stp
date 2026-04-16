package com.filestorage.adapter.in.rest.controller;

import com.filestorage.adapter.in.rest.dto.request.CreateTenantRequest;
import com.filestorage.adapter.in.rest.dto.request.UpdateTenantRequest;
import com.filestorage.adapter.in.rest.dto.response.ApiResponse;
import com.filestorage.adapter.in.rest.mapper.TenantRestMapper;
import com.filestorage.adapter.in.rest.security.SecurityUser;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.port.in.tenant.CreateTenantUseCase;
import com.filestorage.domain.port.in.tenant.DeactivateTenantUseCase;
import com.filestorage.domain.port.in.tenant.GetTenantUseCase;
import com.filestorage.domain.port.in.tenant.ListTenantsUseCase;
import com.filestorage.domain.port.in.tenant.UpdateTenantUseCase;
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
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final CreateTenantUseCase createTenantUseCase;
    private final ListTenantsUseCase listTenantsUseCase;
    private final GetTenantUseCase getTenantUseCase;
    private final UpdateTenantUseCase updateTenantUseCase;
    private final DeactivateTenantUseCase deactivateTenantUseCase;
    private final TenantRestMapper tenantRestMapper;

    @PostMapping
    public ResponseEntity<ApiResponse<?>> createTenant(@RequestBody @Valid CreateTenantRequest request) {
        SecurityUser actor = currentUserOrThrow();
        var result = createTenantUseCase.execute(new CreateTenantUseCase.CreateTenantCommand(
                UUID.fromString(actor.getUserId()),
                parseRole(actor.getRole()),
                request.name(),
                request.slug(),
                request.storageQuotaBytes()
        ));

        return ResponseEntity.status(201).body(ApiResponse.buildSuccess(tenantRestMapper.toTenantResponse(result)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<?>> listTenants(
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit
    ) {
        SecurityUser actor = currentUserOrThrow();
        var result = listTenantsUseCase.execute(new ListTenantsUseCase.ListTenantsQuery(
                UUID.fromString(actor.getUserId()),
                parseRole(actor.getRole()),
                isActive,
                search,
                page,
                limit
        ));

        return ResponseEntity.ok(ApiResponse.buildSuccess(tenantRestMapper.toPageResponse(result)));
    }

    @GetMapping("/{tenantId}")
    public ResponseEntity<ApiResponse<?>> getTenantById(@PathVariable UUID tenantId) {
        SecurityUser actor = currentUserOrThrow();
        var result = getTenantUseCase.execute(new GetTenantUseCase.GetTenantQuery(
                UUID.fromString(actor.getUserId()),
                parseRole(actor.getRole()),
                tenantId
        ));

        return ResponseEntity.ok(ApiResponse.buildSuccess(tenantRestMapper.toTenantResponse(result)));
    }

    @PatchMapping("/{tenantId}")
    public ResponseEntity<ApiResponse<?>> updateTenant(@PathVariable UUID tenantId,
                                                       @RequestBody @Valid UpdateTenantRequest request) {
        SecurityUser actor = currentUserOrThrow();
        var result = updateTenantUseCase.execute(new UpdateTenantUseCase.UpdateTenantCommand(
                UUID.fromString(actor.getUserId()),
                parseRole(actor.getRole()),
                tenantId,
                request.name(),
                request.slug(),
                request.storageQuotaBytes()
        ));

        return ResponseEntity.ok(ApiResponse.buildSuccess(tenantRestMapper.toTenantResponse(result)));
    }

    @DeleteMapping("/{tenantId}")
    public ResponseEntity<ApiResponse<?>> deactivateTenant(@PathVariable UUID tenantId) {
        SecurityUser actor = currentUserOrThrow();
        var result = deactivateTenantUseCase.execute(new DeactivateTenantUseCase.DeactivateTenantCommand(
                UUID.fromString(actor.getUserId()),
                parseRole(actor.getRole()),
                tenantId
        ));

        return ResponseEntity.ok(ApiResponse.buildSuccess(tenantRestMapper.toTenantResponse(result)));
    }

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
}

