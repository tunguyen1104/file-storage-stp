package com.filestorage.adapter.in.rest.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filestorage.adapter.in.rest.advice.GlobalExceptionHandler;
import com.filestorage.adapter.in.rest.dto.response.PageResponse;
import com.filestorage.adapter.in.rest.dto.response.UserCreatedResponse;
import com.filestorage.adapter.in.rest.dto.response.UserDetailResponse;
import com.filestorage.adapter.in.rest.dto.response.UserStorageResponse;
import com.filestorage.adapter.in.rest.mapper.UserRestMapper;
import com.filestorage.adapter.in.rest.security.SecurityUser;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.user.UserPageView;
import com.filestorage.domain.model.user.UserRoleView;

import com.filestorage.domain.model.user.UserStorageView;
import com.filestorage.domain.model.user.UserView;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private ListUsersUseCase listUsersUseCase;
    @Mock
    private GetUserUseCase getUserUseCase;
    @Mock
    private GetUserStorageUseCase getUserStorageUseCase;
    @Mock
    private CreateUserUseCase createUserUseCase;
    @Mock
    private UpdateUserUseCase updateUserUseCase;
    @Mock
    private DeactivateUserUseCase deactivateUserUseCase;
    @Mock
    private ActivateUserUseCase activateUserUseCase;
    @Mock
    private ChangeUserPasswordUseCase changeUserPasswordUseCase;
    @Mock
    private ListTenantUsersUseCase listTenantUsersUseCase;
    @Mock
    private UserRestMapper userRestMapper;

    private UUID actorUserId;
    private UUID actorTenantId;

    @BeforeEach
    void setUp() {
        actorUserId = UUID.randomUUID();
        actorTenantId = UUID.randomUUID();

        SecurityUser securityUser = new SecurityUser(
                actorUserId.toString(), actorTenantId.toString(), "TENANT_ADMIN", "admin@example.com");
        var auth = new UsernamePasswordAuthenticationToken(securityUser, null, securityUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        var messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");

        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        var controller = new UserController(
                listUsersUseCase,
                getUserUseCase,
                getUserStorageUseCase,
                createUserUseCase,
                updateUserUseCase,
                deactivateUserUseCase,
                activateUserUseCase,
                changeUserPasswordUseCase,
                listTenantUsersUseCase,
                userRestMapper
        );

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(messageSource))
                .setValidator(validator)
                .build();

        objectMapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createUser_returns201() throws Exception {
        UUID newUserId = UUID.randomUUID();
        var userView = sampleUserView(newUserId);
        var response = new UserCreatedResponse(
                newUserId,
                "new.user@example.com",
                "New User",
                "MEMBER",
                actorTenantId,
                true,
                100L,
                0L,
                actorUserId,
                Instant.now()
        );

        when(createUserUseCase.execute(any())).thenReturn(userView);
        when(userRestMapper.toUserCreatedResponse(userView)).thenReturn(response);

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "new.user@example.com",
                                "fullName", "New User",
                                "roleCode", RoleCode.MEMBER,
                                "storageQuotaBytes", 100
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("new.user@example.com"));
    }

    @Test
    void createUser_emailExists_returns409() throws Exception {
        doThrow(new AppException(ErrorCode.EMAIL_ALREADY_EXISTS)).when(createUserUseCase).execute(any());

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "exists@example.com",
                                "fullName", "Exists",
                                "roleCode", RoleCode.MEMBER
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.EMAIL_ALREADY_EXISTS.getCode()));
    }

    @Test
    void listUsers_invalidRoleCode_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/users").param("roleCode", "INVALID_ROLE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_PAYLOAD.getCode()));
    }

    @Test
    void listUsers_returns200() throws Exception {
        var pageView = new UserPageView(List.of(sampleUserView(UUID.randomUUID())), 1, 20, 1L, 1);
        var pageResponse = PageResponse.<UserDetailResponse>builder()
                .data(List.of(new UserDetailResponse(
                        UUID.randomUUID(),
                        "a@example.com",
                        "User A",
                        "MEMBER",
                        actorTenantId,
                        "Tenant A",
                        true,
                        100L,
                        20L,
                        "creator@example.com",
                        Instant.now(),
                        Instant.now(),
                        Instant.now(),
                        Instant.now()
                )))
                .currentPage(1)
                .pageSize(20)
                .totalElements(1)
                .totalPages(1)
                .build();

        when(listUsersUseCase.execute(any())).thenReturn(pageView);
        when(userRestMapper.toPageResponse(pageView)).thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.data[0].tenantName").value("Tenant A"))
                .andExpect(jsonPath("$.data.data[0].createdBy").value("creator@example.com"));
    }

    @Test
    void listUsers_forbidden_returns403() throws Exception {
        doThrow(new AppException(ErrorCode.FORBIDDEN)).when(listUsersUseCase).execute(any());

        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));
    }

    @Test
    void getUserById_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        var view = sampleUserView(userId);
        var response = new UserDetailResponse(
                userId,
                "u@example.com",
                "User",
                "MEMBER",
                actorTenantId,
                "Tenant A",
                true,
                100L,
                10L,
                "creator@example.com",
                Instant.now(),
                Instant.now(),
                Instant.now(),
                Instant.now()
        );

        when(getUserUseCase.execute(any())).thenReturn(view);
        when(userRestMapper.toUserDetailResponse(view)).thenReturn(response);

        mockMvc.perform(get("/api/v1/users/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(userId.toString()))
                .andExpect(jsonPath("$.data.tenantName").value("Tenant A"))
                .andExpect(jsonPath("$.data.createdBy").value("creator@example.com"));
    }

    @Test
    void getUserById_notFound_returns404() throws Exception {
        UUID userId = UUID.randomUUID();
        doThrow(new AppException(ErrorCode.USER_NOT_FOUND)).when(getUserUseCase).execute(any());

        mockMvc.perform(get("/api/v1/users/{userId}", userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.USER_NOT_FOUND.getCode()));
    }

    @Test
    void updateUser_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        var view = sampleUserView(userId);
        var response = new UserDetailResponse(
                userId,
                "updated@example.com",
                "Updated User",
                "MEMBER",
                actorTenantId,
                "Tenant A",
                true,
                200L,
                20L,
                "creator@example.com",
                Instant.now(),
                Instant.now(),
                Instant.now(),
                Instant.now()
        );

        when(updateUserUseCase.execute(any())).thenReturn(view);
        when(userRestMapper.toUserDetailResponse(view)).thenReturn(response);

        mockMvc.perform(patch("/api/v1/users/{userId}", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Updated User",
                                "storageQuotaBytes", 200
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("updated@example.com"));
    }

    @Test
    void updateUser_invalidPayload_returns400() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/users/{userId}", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_PAYLOAD.getCode()));
    }

    @Test
    void deactivateUser_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        var view = sampleUserView(userId);
        var response = new UserDetailResponse(
                userId,
                "u@example.com",
                "User",
                "MEMBER",
                actorTenantId,
                "Tenant A",
                false,
                100L,
                20L,
                "creator@example.com",
                Instant.now(),
                Instant.now(),
                Instant.now(),
                Instant.now()
        );

        when(deactivateUserUseCase.execute(any())).thenReturn(view);
        when(userRestMapper.toUserDetailResponse(view)).thenReturn(response);

        mockMvc.perform(delete("/api/v1/users/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.isActive").value(false));
    }

    @Test
    void deactivateUser_forbidden_returns403() throws Exception {
        UUID userId = UUID.randomUUID();
        doThrow(new AppException(ErrorCode.SELF_DEACTIVATION_FORBIDDEN)).when(deactivateUserUseCase).execute(any());

        mockMvc.perform(delete("/api/v1/users/{userId}", userId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.SELF_DEACTIVATION_FORBIDDEN.getCode()));
    }

    @Test
    void activateUser_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        var view = sampleUserView(userId);
        var response = new UserDetailResponse(
                userId,
                "u@example.com",
                "User",
                "MEMBER",
                actorTenantId,
                "Tenant A",
                true,
                100L,
                20L,
                "creator@example.com",
                Instant.now(),
                Instant.now(),
                Instant.now(),
                Instant.now()
        );
        when(activateUserUseCase.execute(any())).thenReturn(view);
        when(userRestMapper.toUserDetailResponse(view)).thenReturn(response);

        mockMvc.perform(patch("/api/v1/users/{userId}/activate", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.isActive").value(true));
    }

    @Test
    void changePassword_returns204() throws Exception {
        UUID userId = UUID.randomUUID();
        doNothing().when(changeUserPasswordUseCase).execute(any());

        mockMvc.perform(patch("/api/v1/users/{userId}/password", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "OldP@ss1",
                                "newPassword", "NewP@ss1",
                                "confirmPassword", "NewP@ss1"
                        ))))
                .andExpect(status().isNoContent());
    }

    @Test
    void changePassword_invalidCurrentPassword_returns401() throws Exception {
        UUID userId = UUID.randomUUID();
        doThrow(new AppException(ErrorCode.INVALID_CURRENT_PASSWORD)).when(changeUserPasswordUseCase).execute(any());

        mockMvc.perform(patch("/api/v1/users/{userId}/password", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "wrong",
                                "newPassword", "NewP@ss1",
                                "confirmPassword", "NewP@ss1"
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_CURRENT_PASSWORD.getCode()));
    }

    @Test
    void getUserStorage_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        var view = new UserStorageView(userId, 100L, 10L, 90L, 10D);
        var response = new UserStorageResponse(userId, 100L, 10L, 90L, 10D);

        when(getUserStorageUseCase.execute(any())).thenReturn(view);
        when(userRestMapper.toUserStorageResponse(view)).thenReturn(response);

        mockMvc.perform(get("/api/v1/users/{userId}/storage", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.storageAvailableBytes").value(90));
    }

    @Test
    void getUserStorage_notFound_returns404() throws Exception {
        UUID userId = UUID.randomUUID();
        doThrow(new AppException(ErrorCode.USER_NOT_FOUND)).when(getUserStorageUseCase).execute(any());

        mockMvc.perform(get("/api/v1/users/{userId}/storage", userId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.USER_NOT_FOUND.getCode()));
    }

    @Test
    void missingSecurityContext_returns401() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.ACCESS_DENIED.getCode()));
    }

    @Test
    void listTenantUsers_returns200() throws Exception {
        var pageView = new UserPageView(List.of(sampleUserView(UUID.randomUUID())), 1, 20, 1L, 1);
        var pageResponse = PageResponse.<UserDetailResponse>builder()
                .data(List.of(new UserDetailResponse(
                        UUID.randomUUID(),
                        "a@example.com",
                        "User A",
                        "MEMBER",
                        actorTenantId,
                        "Tenant A",
                        true,
                        100L,
                        20L,
                        "creator@example.com",
                        Instant.now(),
                        Instant.now(),
                        Instant.now(),
                        Instant.now()
                )))
                .currentPage(1)
                .pageSize(20)
                .totalElements(1)
                .totalPages(1)
                .build();

        when(listTenantUsersUseCase.execute(any())).thenReturn(pageView);
        when(userRestMapper.toPageResponse(pageView)).thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/users/tenant"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.data[0].tenantName").value("Tenant A"))
                .andExpect(jsonPath("$.data.data[0].createdBy").value("creator@example.com"));
    }

    private UserView sampleUserView(UUID id) {
        return new UserView(
                id,
                "u@example.com",
                "User",
                new UserRoleView(UUID.randomUUID(), RoleCode.MEMBER, "Member"),
                actorTenantId,
                "Tenant A",
                true,
                100L,
                10L,
                actorUserId,
                "creator@example.com",
                Instant.now(),
                Instant.now(),
                Instant.now(),
                Instant.now()
        );
    }
}
