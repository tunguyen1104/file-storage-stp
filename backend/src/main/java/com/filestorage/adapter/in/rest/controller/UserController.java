package com.filestorage.adapter.in.rest.controller;

import com.filestorage.adapter.in.rest.dto.request.ChangeUserPasswordRequest;
import com.filestorage.adapter.in.rest.dto.request.CreateUserRequest;
import com.filestorage.adapter.in.rest.dto.request.UpdateUserRequest;
import com.filestorage.adapter.in.rest.dto.response.ApiResponse;
import com.filestorage.adapter.in.rest.mapper.UserRestMapper;
import com.filestorage.adapter.in.rest.security.SecurityUser;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.port.in.user.ChangeUserPasswordUseCase;
import com.filestorage.domain.port.in.user.CreateUserUseCase;
import com.filestorage.domain.port.in.user.DeactivateUserUseCase;
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
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final ListUsersUseCase listUsersUseCase;
    private final GetUserUseCase getUserUseCase;
    private final GetUserStorageUseCase getUserStorageUseCase;
    private final CreateUserUseCase createUserUseCase;
    private final UpdateUserUseCase updateUserUseCase;
    private final DeactivateUserUseCase deactivateUserUseCase;
    private final ChangeUserPasswordUseCase changeUserPasswordUseCase;
    private final ListTenantUsersUseCase listTenantUsersUseCase;
    private final UserRestMapper userRestMapper;

    @PostMapping
    public ResponseEntity<ApiResponse<?>> createUser(@RequestBody @Valid CreateUserRequest request) {
        SecurityUser actor = currentUserOrThrow();
        var result = createUserUseCase.execute(new CreateUserUseCase.CreateUserCommand(
                UUID.fromString(actor.getUserId()),
                UUID.fromString(actor.getTenantId()),
                parseRole(actor.getRole()),
                request.email(),
                request.fullName(),
                request.roleId(),
                request.storageQuotaBytes(),
                request.tenantId()
        ));

        return ResponseEntity.status(201).body(ApiResponse.buildSuccess(userRestMapper.toUserCreatedResponse(result)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<?>> listUsers(
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) UUID roleId,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit
    ) {
        SecurityUser actor = currentUserOrThrow();
        var result = listUsersUseCase.execute(new ListUsersUseCase.ListUsersQuery(
                UUID.fromString(actor.getUserId()),
                UUID.fromString(actor.getTenantId()),
                parseRole(actor.getRole()),
                tenantId,
                isActive,
                roleId,
                search,
                page,
                limit
        ));

        return ResponseEntity.ok(ApiResponse.buildSuccess(userRestMapper.toPageResponse(result)));
    }

    @GetMapping("/tenant")
    public ResponseEntity<ApiResponse<?>> listTenantUsers(
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) UUID roleId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit
    ) {
        SecurityUser actor = currentUserOrThrow();
        var result = listTenantUsersUseCase.execute(new ListTenantUsersUseCase.ListTenantUsersQuery(
                UUID.fromString(actor.getUserId()),
                UUID.fromString(actor.getTenantId()),
                parseRole(actor.getRole()),
                isActive,
                roleId,
                search,
                page,
                limit
        ));

        return ResponseEntity.ok(ApiResponse.buildSuccess(userRestMapper.toPageResponse(result)));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<?>> getUserById(@PathVariable UUID userId) {
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
    public ResponseEntity<ApiResponse<?>> updateUser(@PathVariable UUID userId, @RequestBody @Valid UpdateUserRequest request) {
        SecurityUser actor = currentUserOrThrow();
        var result = updateUserUseCase.execute(new UpdateUserUseCase.UpdateUserCommand(
                UUID.fromString(actor.getUserId()),
                UUID.fromString(actor.getTenantId()),
                parseRole(actor.getRole()),
                userId,
                request.fullName(),
                request.email(),
                request.roleId(),
                request.storageQuotaBytes()
        ));

        return ResponseEntity.ok(ApiResponse.buildSuccess(userRestMapper.toUserDetailResponse(result)));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<ApiResponse<?>> deactivateUser(@PathVariable UUID userId) {
        SecurityUser actor = currentUserOrThrow();
        var result = deactivateUserUseCase.execute(new DeactivateUserUseCase.DeactivateUserCommand(
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
    public ResponseEntity<ApiResponse<?>> getUserStorage(@PathVariable UUID userId) {
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
