package com.filestorage.application.usecase.user;

import com.filestorage.adapter.out.persistence.mapper.UserViewMapper;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.user.UserPageView;
import com.filestorage.domain.model.user.UserView;
import com.filestorage.domain.port.in.user.ListUsersUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListUsersService implements ListUsersUseCase {

    private final UserJpaRepository userRepository;
    private final UserViewMapper userViewMapper;

    @Override
    @Transactional(readOnly = true)
    public UserPageView execute(ListUsersQuery query) {
        UserAccessPolicy.requireCanList(query.actorRole());

        UUID effectiveTenantId = resolveEffectiveTenantId(query.actorRole(), query.actorTenantId(), query.tenantId());
        int page = Math.max(1, query.page());
        int limit = Math.min(100, Math.max(1, query.limit()));

        var pageable = PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        Specification<com.filestorage.adapter.out.persistence.entity.User> spec = Specification
                .where(UserSearchSpecifications.byTenant(effectiveTenantId))
                .and(UserSearchSpecifications.byIsActive(query.isActive()))
                .and(UserSearchSpecifications.byRole(query.roleId()))
                .and(UserSearchSpecifications.bySearch(query.search()));

        var result = userRepository.findAll(spec, pageable);
        List<UserView> mapped = result.getContent().stream().map(userViewMapper::toUserView).toList();

        return new UserPageView(mapped, page, limit, result.getTotalElements(), result.getTotalPages());
    }

    private UUID resolveEffectiveTenantId(RoleCode actorRole, UUID actorTenantId, UUID requestedTenantId) {
        if (actorRole == RoleCode.SUPER_ADMIN) {
            return requestedTenantId;
        }
        return actorTenantId;
    }
}

