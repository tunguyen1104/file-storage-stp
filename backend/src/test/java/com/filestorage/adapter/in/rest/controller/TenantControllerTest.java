package com.filestorage.adapter.in.rest.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filestorage.adapter.in.rest.advice.GlobalExceptionHandler;
import com.filestorage.adapter.in.rest.dto.response.PageResponse;
import com.filestorage.adapter.in.rest.dto.response.TenantResponse;
import com.filestorage.adapter.in.rest.mapper.TenantRestMapper;
import com.filestorage.adapter.in.rest.security.SecurityUser;
import com.filestorage.domain.model.tenant.TenantPageView;
import com.filestorage.domain.model.tenant.TenantView;
import com.filestorage.domain.port.in.tenant.*;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TenantControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private CreateTenantUseCase createTenantUseCase;
    @Mock
    private ListTenantsUseCase listTenantsUseCase;
    @Mock
    private ListTenantOptionsUseCase listTenantOptionsUseCase;
    @Mock
    private GetTenantUseCase getTenantUseCase;
    @Mock
    private UpdateTenantUseCase updateTenantUseCase;
    @Mock
    private DeactivateTenantUseCase deactivateTenantUseCase;
    @Mock
    private TenantRestMapper tenantRestMapper;

    private UUID actorUserId;

    @BeforeEach
    void setUp() {
        actorUserId = UUID.randomUUID();
        SecurityUser securityUser = new SecurityUser(actorUserId.toString(), UUID.randomUUID().toString(), "SUPER_ADMIN", "super@sys.local");
        var auth = new UsernamePasswordAuthenticationToken(securityUser, null, securityUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        var messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");

        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        var controller = new TenantController(
                createTenantUseCase,
                listTenantsUseCase,
                listTenantOptionsUseCase,
                getTenantUseCase,
                updateTenantUseCase,
                deactivateTenantUseCase,
                tenantRestMapper
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
    void createTenant_returns201() throws Exception {
        UUID tenantId = UUID.randomUUID();
        TenantView view = new TenantView(tenantId, "Acme", "acme", 100L, 0L, true, actorUserId, Instant.now(), Instant.now());
        TenantResponse response = new TenantResponse(tenantId, "Acme", "acme", 100L, 0L, true, actorUserId, Instant.now(), Instant.now());

        when(createTenantUseCase.execute(any())).thenReturn(view);
        when(tenantRestMapper.toTenantResponse(view)).thenReturn(response);

        mockMvc.perform(post("/api/v1/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Acme", "slug", "acme"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.slug").value("acme"));
    }

    @Test
    void createTenant_slugConflict_returns409() throws Exception {
        doThrow(new AppException(ErrorCode.SLUG_ALREADY_EXISTS)).when(createTenantUseCase).execute(any());

        mockMvc.perform(post("/api/v1/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Acme", "slug", "acme"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.SLUG_ALREADY_EXISTS.getCode()));
    }

    @Test
    void listTenants_returns200() throws Exception {
        UUID tenantId = UUID.randomUUID();
        TenantPageView pageView = new TenantPageView(
                List.of(new TenantView(tenantId, "Acme", "acme", 100L, 10L, true, 3L, actorUserId, Instant.now(), Instant.now())),
                1, 20, 1, 1
        );
        PageResponse<TenantResponse> pageResponse = PageResponse.<TenantResponse>builder()
                .data(List.of(new TenantResponse(tenantId, "Acme", "acme", 100L, 10L, true, 3L, actorUserId, Instant.now(), Instant.now())))
                .currentPage(1)
                .pageSize(20)
                .totalElements(1)
                .totalPages(1)
                .build();

        when(listTenantsUseCase.execute(any())).thenReturn(pageView);
        when(tenantRestMapper.toPageResponse(pageView)).thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/tenants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.data[0].userCount").value(3))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void getTenantById_notFound_returns404() throws Exception {
        UUID tenantId = UUID.randomUUID();
        doThrow(new AppException(ErrorCode.TENANT_NOT_FOUND)).when(getTenantUseCase).execute(any());

        mockMvc.perform(get("/api/v1/tenants/{tenantId}", tenantId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.TENANT_NOT_FOUND.getCode()));
    }

    @Test
    void updateTenant_quotaBelowUsed_returns400() throws Exception {
        UUID tenantId = UUID.randomUUID();
        doThrow(new AppException(ErrorCode.QUOTA_BELOW_USED)).when(updateTenantUseCase).execute(any());

        mockMvc.perform(patch("/api/v1/tenants/{tenantId}", tenantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("storageQuotaBytes", 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.QUOTA_BELOW_USED.getCode()));
    }

    @Test
    void deactivateTenant_systemTenant_returns409() throws Exception {
        UUID tenantId = UUID.randomUUID();
        doThrow(new AppException(ErrorCode.SYSTEM_TENANT_CANNOT_BE_DELETED)).when(deactivateTenantUseCase).execute(any());

        mockMvc.perform(delete("/api/v1/tenants/{tenantId}", tenantId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.SYSTEM_TENANT_CANNOT_BE_DELETED.getCode()));
    }

    @Test
    void missingSecurityContext_returns401() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/v1/tenants"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.ACCESS_DENIED.getCode()));
    }
}
