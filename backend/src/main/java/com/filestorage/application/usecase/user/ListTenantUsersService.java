package com.filestorage.application.usecase.user;

import com.filestorage.adapter.out.persistence.mapper.UserViewMapper;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.user.UserPageView;
import com.filestorage.domain.model.user.UserView;
import com.filestorage.domain.port.in.user.ListTenantUsersUseCase;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListTenantUsersService implements ListTenantUsersUseCase {

	private final UserJpaRepository userRepository;
	private final UserViewMapper userViewMapper;

	@Override
	@Transactional(readOnly = true)
	public UserPageView execute(ListTenantUsersQuery query) {
		if (query.actorRole() != RoleCode.SUPER_ADMIN && query.actorRole() != RoleCode.TENANT_ADMIN) {
			throw new AppException(ErrorCode.FORBIDDEN);
		}

		int page = Math.max(1, query.page());
		int limit = Math.min(100, Math.max(1, query.limit()));

		var pageable = PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
		Specification<com.filestorage.adapter.out.persistence.entity.User> spec = Specification
				.where(UserSearchSpecifications.byTenant(query.actorTenantId()))
				.and(UserSearchSpecifications.byIsActive(query.isActive()))
				.and(UserSearchSpecifications.byRole(query.roleCode()))
				.and(UserSearchSpecifications.bySearch(query.search()));

		var result = userRepository.findAll(spec, pageable);
		List<UserView> mapped = result.getContent().stream().map(userViewMapper::toUserView).toList();

		return new UserPageView(mapped, page, limit, result.getTotalElements(), result.getTotalPages());
	}
}
