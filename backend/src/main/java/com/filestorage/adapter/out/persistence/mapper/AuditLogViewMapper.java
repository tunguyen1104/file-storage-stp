package com.filestorage.adapter.out.persistence.mapper;

import com.filestorage.adapter.out.persistence.entity.AuditLog;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
import com.filestorage.domain.model.audit.AuditLogView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AuditLogViewMapper {

    private static final String KEY_ACTOR_ROLE = "actor_role";
    private static final String KEY_REQUEST_ID = "request_id";

    private final UserJpaRepository userRepository;

    public AuditLogView toView(AuditLog entity) {
        Map<String, Object> raw = entity.getExtraData() == null ? Map.of() : entity.getExtraData();
        String actorRole = toStringOrNull(raw.get(KEY_ACTOR_ROLE));
        String requestId = toStringOrNull(raw.get(KEY_REQUEST_ID));

        Map<String, Object> remaining = new HashMap<>(raw);
        remaining.remove(KEY_ACTOR_ROLE);
        remaining.remove(KEY_REQUEST_ID);

        String email = entity.getUserId() == null
                ? null
                : userRepository.findById(entity.getUserId()).map(User::getEmail).orElse(null);

        return new AuditLogView(
                entity.getId(),
                entity.getTenantId(),
                entity.getUserId(),
                email,
                entity.getFileId(),
                entity.getAction(),
                actorRole,
                entity.getIpAddress(),
                entity.getUserAgent(),
                requestId,
                remaining,
                entity.getCreatedAt()
        );
    }

    private String toStringOrNull(Object o) {
        return o == null ? null : o.toString();
    }
}
