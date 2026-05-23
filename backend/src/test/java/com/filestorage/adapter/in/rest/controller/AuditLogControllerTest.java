package com.filestorage.adapter.in.rest.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filestorage.adapter.in.rest.advice.GlobalExceptionHandler;
import com.filestorage.adapter.in.rest.dto.response.PageResponse;
import com.filestorage.adapter.in.rest.dto.response.audit.AuditLogResponse;
import com.filestorage.adapter.in.rest.mapper.AuditLogCsvWriter;
import com.filestorage.adapter.in.rest.mapper.AuditLogRestMapper;
import com.filestorage.adapter.in.rest.security.SecurityUser;
import com.filestorage.domain.model.audit.AuditAction;
import com.filestorage.domain.model.audit.AuditLogPage;
import com.filestorage.domain.model.audit.AuditLogStats;
import com.filestorage.domain.model.audit.AuditLogView;
import com.filestorage.domain.model.audit.AuditSeverity;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.port.in.audit.ExportAuditLogsUseCase;
import com.filestorage.domain.port.in.audit.GetAuditLogStatsUseCase;
import com.filestorage.domain.port.in.audit.ListAuditLogsUseCase;
import com.filestorage.shared.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuditLogControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ListAuditLogsUseCase listAuditLogsUseCase;
    @Mock
    private GetAuditLogStatsUseCase getAuditLogStatsUseCase;
    @Mock
    private ExportAuditLogsUseCase exportAuditLogsUseCase;
    @Mock
    private AuditLogRestMapper mapper;

    private UUID actorUserId;
    private UUID actorTenantId;

    @BeforeEach
    void setUp() {
        actorUserId = UUID.randomUUID();
        actorTenantId = UUID.randomUUID();

        var messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");

        var controller = new AuditLogController(
                listAuditLogsUseCase,
                getAuditLogStatsUseCase,
                exportAuditLogsUseCase,
                mapper,
                new AuditLogCsvWriter()
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(messageSource))
                .build();
        new ObjectMapper();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void superAdmin_passesTenantIdFromQuery() throws Exception {
        authenticate("SUPER_ADMIN");
        UUID queriedTenant = UUID.randomUUID();
        stubEmptyPage();

        mockMvc.perform(get("/api/v1/audit-logs").param("tenantId", queriedTenant.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        ArgumentCaptor<ListAuditLogsUseCase.ListAuditLogsQuery> captor =
                ArgumentCaptor.forClass(ListAuditLogsUseCase.ListAuditLogsQuery.class);
        verify(listAuditLogsUseCase).execute(captor.capture());
        assertEquals(queriedTenant, captor.getValue().tenantId());
        assertEquals(RoleCode.SUPER_ADMIN, captor.getValue().actorRole());
    }

    @Test
    void tenantAdmin_canCallEndpoint() throws Exception {
        authenticate("TENANT_ADMIN");
        stubEmptyPage();

        mockMvc.perform(get("/api/v1/audit-logs"))
                .andExpect(status().isOk());

        ArgumentCaptor<ListAuditLogsUseCase.ListAuditLogsQuery> captor =
                ArgumentCaptor.forClass(ListAuditLogsUseCase.ListAuditLogsQuery.class);
        verify(listAuditLogsUseCase).execute(captor.capture());
        assertEquals(RoleCode.TENANT_ADMIN, captor.getValue().actorRole());
        // Controller forwards raw query — AuditAccessPolicy in service layer enforces tenant override.
    }

    @Test
    void member_returns403() throws Exception {
        authenticate("MEMBER");

        mockMvc.perform(get("/api/v1/audit-logs"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.getCode()));
    }

    @Test
    void noSecurityContext_returns401() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/v1/audit-logs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.ACCESS_DENIED.getCode()));
    }

    @Test
    void limitOver100_returns400() throws Exception {
        authenticate("SUPER_ADMIN");

        mockMvc.perform(get("/api/v1/audit-logs").param("limit", "150"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_PAYLOAD.getCode()));
    }

    @Test
    void fromAfterTo_returns400() throws Exception {
        authenticate("SUPER_ADMIN");
        Instant from = Instant.now();
        Instant to = from.minusSeconds(3600);

        mockMvc.perform(get("/api/v1/audit-logs")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_PAYLOAD.getCode()));
    }

    @Test
    void returnsMappedPageResponse() throws Exception {
        authenticate("SUPER_ADMIN");
        UUID rowId = UUID.randomUUID();
        AuditLogView view = new AuditLogView(
                rowId, actorTenantId, actorUserId, "user@x.com", null,
                AuditAction.FILE_DELETE, "TENANT_ADMIN", "1.2.3.4", "ua", "req-1",
                Map.of(), Instant.now()
        );
        AuditLogPage page = new AuditLogPage(List.of(view), 1, 20, 1L, 1);
        AuditLogResponse response = new AuditLogResponse(
                rowId, actorTenantId, actorUserId, "user@x.com", null,
                AuditAction.FILE_DELETE, AuditAction.FILE_DELETE.severity(),
                "TENANT_ADMIN", "1.2.3.4", "ua", "req-1",
                Map.of(), view.createdAt()
        );
        PageResponse<AuditLogResponse> pageResponse = PageResponse.<AuditLogResponse>builder()
                .data(List.of(response))
                .currentPage(1).pageSize(20).totalElements(1L).totalPages(1)
                .build();

        when(listAuditLogsUseCase.execute(any())).thenReturn(page);
        when(mapper.toPageResponse(page)).thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.data[0].id").value(rowId.toString()))
                .andExpect(jsonPath("$.data.data[0].action").value("FILE_DELETE"));
    }

    @Test
    void list_forwardsUserEmailAndUserAgent() throws Exception {
        authenticate("SUPER_ADMIN");
        stubEmptyPage();

        mockMvc.perform(get("/api/v1/audit-logs")
                        .param("userEmail", "alice")
                        .param("userAgent", "Mozilla"))
                .andExpect(status().isOk());

        ArgumentCaptor<ListAuditLogsUseCase.ListAuditLogsQuery> captor =
                ArgumentCaptor.forClass(ListAuditLogsUseCase.ListAuditLogsQuery.class);
        verify(listAuditLogsUseCase).execute(captor.capture());
        assertEquals("alice", captor.getValue().userEmail());
        assertEquals("Mozilla", captor.getValue().userAgent());
    }

    @Test
    void stats_returnsSeverityCounts() throws Exception {
        authenticate("SUPER_ADMIN");
        Map<AuditSeverity, Long> counts = new EnumMap<>(AuditSeverity.class);
        counts.put(AuditSeverity.ERROR, 3L);
        counts.put(AuditSeverity.WARNING, 2L);
        counts.put(AuditSeverity.INFO, 5L);
        counts.put(AuditSeverity.SUCCESS, 10L);
        when(getAuditLogStatsUseCase.execute(any())).thenReturn(new AuditLogStats(20L, counts));

        mockMvc.perform(get("/api/v1/audit-logs/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(20))
                .andExpect(jsonPath("$.data.severityCounts.ERROR").value(3))
                .andExpect(jsonPath("$.data.severityCounts.WARNING").value(2))
                .andExpect(jsonPath("$.data.severityCounts.INFO").value(5))
                .andExpect(jsonPath("$.data.severityCounts.SUCCESS").value(10));
    }

    @Test
    void stats_member_returns403() throws Exception {
        authenticate("MEMBER");

        mockMvc.perform(get("/api/v1/audit-logs/stats"))
                .andExpect(status().isForbidden());
    }

    @Test
    void export_returnsCsvAttachment() throws Exception {
        authenticate("SUPER_ADMIN");
        AuditLogView view = new AuditLogView(
                UUID.randomUUID(), actorTenantId, actorUserId, "user@x.com", null,
                AuditAction.FILE_DELETE, "TENANT_ADMIN", "1.2.3.4", "ua", "req-1",
                Map.of("k", "v"), Instant.parse("2026-05-01T00:00:00Z")
        );
        when(exportAuditLogsUseCase.execute(any())).thenReturn(List.of(view));

        mockMvc.perform(get("/api/v1/audit-logs/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("text/csv")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("FILE_DELETE")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("WARNING")));
    }

    @Test
    void export_member_returns403() throws Exception {
        authenticate("MEMBER");

        mockMvc.perform(get("/api/v1/audit-logs/export"))
                .andExpect(status().isForbidden());
    }

    private void stubEmptyPage() {
        AuditLogPage empty = new AuditLogPage(List.of(), 1, 20, 0L, 0);
        PageResponse<AuditLogResponse> emptyResponse = PageResponse.<AuditLogResponse>builder()
                .data(List.of()).currentPage(1).pageSize(20).totalElements(0L).totalPages(0).build();
        when(listAuditLogsUseCase.execute(any())).thenReturn(empty);
        when(mapper.toPageResponse(empty)).thenReturn(emptyResponse);
    }

    private void authenticate(String role) {
        SecurityUser securityUser = new SecurityUser(actorUserId.toString(), actorTenantId.toString(), role, "user@x.com");
        var auth = new UsernamePasswordAuthenticationToken(securityUser, null, securityUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
