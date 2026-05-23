package com.filestorage.application.usecase.audit;

import com.filestorage.domain.model.audit.AuditAction;
import com.filestorage.domain.port.in.audit.LogAuditEventUseCase.AuditEvent;
import com.filestorage.domain.port.out.audit.AuditEventPublisherPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditLoggerServiceTest {

    @Mock
    private AuditEventPublisherPort publisher;

    @InjectMocks
    private AuditLoggerService loggerService;

    @Test
    void publishesEventToBus() {
        AuditEvent event = sample();
        loggerService.logAsync(event);
        verify(publisher).publish(event);
    }

    @Test
    void publishExceptionIsSwallowed() {
        AuditEvent event = sample();
        doThrow(new RuntimeException("broker down")).when(publisher).publish(event);

        // Must not propagate — broker outage cannot break a business request.
        assertDoesNotThrow(() -> loggerService.logAsync(event));
    }

    private AuditEvent sample() {
        return new AuditEvent(
                AuditAction.FILE_DELETE,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "TENANT_ADMIN",
                "1.2.3.4",
                "ua",
                "req-1",
                Map.of("actor_role", "TENANT_ADMIN")
        );
    }
}
