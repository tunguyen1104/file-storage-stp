package com.filestorage.adapter.in.rest.controller;

import com.filestorage.adapter.in.rest.dto.request.ChangeUserPasswordRequest;
import com.filestorage.adapter.in.rest.dto.request.CreateUserRequest;
import com.filestorage.adapter.in.rest.dto.request.UpdateUserRequest;
import com.filestorage.adapter.in.rest.dto.response.ApiResponse;
import com.filestorage.adapter.in.rest.dto.response.PageResponse;
import com.filestorage.adapter.in.rest.dto.response.UserCreatedResponse;
import com.filestorage.adapter.in.rest.dto.response.UserDetailResponse;
import com.filestorage.adapter.in.rest.dto.response.UserStorageResponse;
import com.filestorage.adapter.in.rest.mapper.UserRestMapper;
import com.filestorage.adapter.in.rest.security.SecurityUser;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.port.in.user.ChangeUserPasswordUseCase;
import com.filestorage.domain.port.in.user.CreateUserUseCase;
import com.filestorage.domain.port.in.user.DeactivateUserUseCase;
import com.filestorage.domain.port.in.user.ActivateUserUseCase;
import com.filestorage.domain.port.in.user.GetUserStorageUseCase;
import com.filestorage.domain.port.in.user.GetUserUseCase;
import com.filestorage.domain.port.in.user.ListTenantUsersUseCase;
import com.filestorage.domain.port.in.user.ListUsersUseCase;
import com.filestorage.domain.port.in.user.UpdateUserUseCase;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import com.filestorage.shared.util.SecurityContextUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final ListUsersUseCase listUsersUseCase;
    private final GetUserUseCase getUserUseCase;
    private final GetUserStorageUseCase getUserStorageUseCase;
    private final CreateUserUseCase createUserUseCase;
    private final UpdateUserUseCase updateUserUseCase;
    private final DeactivateUserUseCase deactivateUserUseCase;
    private final ActivateUserUseCase activateUserUseCase;
    private final ChangeUserPasswordUseCase changeUserPasswordUseCase;
    private final ListTenantUsersUseCase listTenantUsersUseCase;
    private final UserRestMapper userRestMapper;


    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<UserCreatedResponse>> createUser(@RequestBody @Valid CreateUserRequest request) {
        SecurityUser actor = currentUserOrThrow();
        var result = createUserUseCase.execute(new CreateUserUseCase.CreateUserCommand(
                UUID.fromString(actor.getUserId()),
                UUID.fromString(actor.getTenantId()),
                parseRole(actor.getRole()),
                request.email(),
                request.fullName(),
                request.roleCode(),
                request.storageQuotaBytes(),
                request.tenantId()
        ));

        return ResponseEntity.status(201).body(ApiResponse.buildSuccess(userRestMapper.toUserCreatedResponse(result)));
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<UserDetailResponse>>> listUsers(
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) RoleCode roleCode,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit
    ) {
        SecurityUser actor = currentUserOrThrow();
        var result = listUsersUseCase.execute(new ListUsersUseCase.ListUsersQuery(
                UUID.fromString(actor.getUserId()),
                UUID.fromString(actor.getTenantId()),
                parseRole(actor.getRole()),
                tenantId,
                isActive,
                roleCode,
                search,
                page,
                limit
        ));

        return ResponseEntity.ok(ApiResponse.buildSuccess(userRestMapper.toPageResponse(result)));
    }

    @GetMapping("/tenant")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<UserDetailResponse>>> listTenantUsers(
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) RoleCode roleCode,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit
    ) {
        SecurityUser actor = currentUserOrThrow();
        var result = listTenantUsersUseCase.execute(new ListTenantUsersUseCase.ListTenantUsersQuery(
                UUID.fromString(actor.getUserId()),
                UUID.fromString(actor.getTenantId()),
                parseRole(actor.getRole()),
                isActive,
                roleCode,
                search,
                page,
                limit
        ));

        return ResponseEntity.ok(ApiResponse.buildSuccess(userRestMapper.toPageResponse(result)));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserDetailResponse>> getUserById(@PathVariable UUID userId) {
        SecurityUser actor = currentUserOrThrow();
        var result = getUserUseCase.execute(new GetUserUseCase.GetUserQuery(
                userId,
                UUID.fromString(actor.getUserId()),
                UUID.fromString(actor.getTenantId()),
                parseRole(actor.getRole())
        ));

        return ResponseEntity.ok(ApiResponse.buildSuccess(userRestMapper.toUserDetailResponse(result)));
    }

    @PatchMapping("/{userId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<UserDetailResponse>> updateUser(@PathVariable UUID userId, @RequestBody @Valid UpdateUserRequest request) {
        SecurityUser actor = currentUserOrThrow();
        var result = updateUserUseCase.execute(new UpdateUserUseCase.UpdateUserCommand(
                UUID.fromString(actor.getUserId()),
                UUID.fromString(actor.getTenantId()),
                parseRole(actor.getRole()),
                userId,
                request.fullName(),
                request.roleCode(),
                request.storageQuotaBytes()
        ));

        return ResponseEntity.ok(ApiResponse.buildSuccess(userRestMapper.toUserDetailResponse(result)));
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<UserDetailResponse>> deactivateUser(@PathVariable UUID userId) {
        SecurityUser actor = currentUserOrThrow();
        var result = deactivateUserUseCase.execute(new DeactivateUserUseCase.DeactivateUserCommand(
                UUID.fromString(actor.getUserId()),
                UUID.fromString(actor.getTenantId()),
                parseRole(actor.getRole()),
                userId
        ));

        return ResponseEntity.ok(ApiResponse.buildSuccess(userRestMapper.toUserDetailResponse(result)));
    }

    @PatchMapping("/{userId}/activate")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'TENANT_ADMIN')")
    public ResponseEntity<ApiResponse<UserDetailResponse>> activateUser(@PathVariable UUID userId) {
        SecurityUser actor = currentUserOrThrow();
        var result = activateUserUseCase.execute(new ActivateUserUseCase.ActivateUserCommand(
                UUID.fromString(actor.getUserId()),
                UUID.fromString(actor.getTenantId()),
                parseRole(actor.getRole()),
                userId
        ));

        return ResponseEntity.ok(ApiResponse.buildSuccess(userRestMapper.toUserDetailResponse(result)));
    }

    @PatchMapping("/{userId}/password")
    public ResponseEntity<Void> changePassword(@PathVariable UUID userId,
                                               @RequestBody @Valid ChangeUserPasswordRequest request) {
        SecurityUser actor = currentUserOrThrow();
        changeUserPasswordUseCase.execute(new ChangeUserPasswordUseCase.ChangeUserPasswordCommand(
                UUID.fromString(actor.getUserId()),
                UUID.fromString(actor.getTenantId()),
                parseRole(actor.getRole()),
                userId,
                request.currentPassword(),
                request.newPassword(),
                request.confirmPassword()
        ));

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{userId}/storage")
    public ResponseEntity<ApiResponse<UserStorageResponse>> getUserStorage(@PathVariable UUID userId) {
        SecurityUser actor = currentUserOrThrow();
        var result = getUserStorageUseCase.execute(new GetUserStorageUseCase.GetUserStorageQuery(
                userId,
                UUID.fromString(actor.getUserId()),
                UUID.fromString(actor.getTenantId()),
                parseRole(actor.getRole())
        ));

        return ResponseEntity.ok(ApiResponse.buildSuccess(userRestMapper.toUserStorageResponse(result)));
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
