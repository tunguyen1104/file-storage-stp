package com.filestorage.domain.port.out.audit;

import com.filestorage.domain.port.in.audit.LogAuditEventUseCase.AuditEvent;

public interface AuditEventPublisherPort {

    void publish(AuditEvent event);
}
