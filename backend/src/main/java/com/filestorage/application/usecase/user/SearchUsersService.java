package com.filestorage.application.usecase.user;

import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository.UserSearchProjection;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.user.UserSearchResult;
import com.filestorage.domain.port.in.user.SearchUsersUseCase;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SearchUsersService implements SearchUsersUseCase {

    private static final int RESULT_LIMIT = 4;

    private final UserJpaRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<UserSearchResult> execute(SearchUsersQuery query) {
        if (query.email() == null || query.email().isBlank()) {
            throw new AppException(ErrorCode.INVALID_PAYLOAD);
        }

        UUID effectiveTenantId = resolveTenantScope(query);
        String prefix = query.email().trim().toLowerCase() + "%";

        List<UserSearchProjection> rows = userRepository.searchByEmailPrefix(
                effectiveTenantId,
                prefix,
                PageRequest.of(0, RESULT_LIMIT)
        );

        return rows.stream()
                .map(r -> new UserSearchResult(r.getId(), r.getEmail()))
                .toList();
    }

    private UUID resolveTenantScope(SearchUsersQuery query) {
        if (query.actorRole() == RoleCode.TENANT_ADMIN) {
            return query.actorTenantId();
        }
        return query.tenantId();
    }
}
