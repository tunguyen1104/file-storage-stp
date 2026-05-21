package com.filestorage.application.usecase.share;

import com.filestorage.adapter.out.persistence.entity.FileShare;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.enums.ShareSortBy;
import com.filestorage.domain.model.enums.ShareStatus;
import com.filestorage.domain.model.enums.SortDirection;
import com.filestorage.domain.model.share.ShareListPageView;
import com.filestorage.domain.port.in.share.GetAdminSharesQuery;
import com.filestorage.domain.port.in.share.GetAdminSharesUseCase;
import com.filestorage.domain.port.out.ShareRepositoryPort;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetAdminSharesService implements GetAdminSharesUseCase {

    private final ShareRepositoryPort shareRepository;
    private final ShareServiceSupport support;

    @Override
    @Transactional(readOnly = true)
    public ShareListPageView getAdminShares(GetAdminSharesQuery query) {
        User admin = support.resolveActor(query.adminId());
        requireAdmin(admin);

        int page = support.normalizePage(query.page());
        int limit = support.normalizeLimit(query.limit());
        List<UUID> sharedByIds = normalizeUuidList(query.sharedByIds());
        List<UUID> tenantIds = resolveTenantIds(query.tenantIds(), admin, sharedByIds);
        Page<FileShare> shares = shareRepository.findAdminShares(
                tenantIds,
                sharedByIds,
                support.normalizeResourceType(query.resourceType()),
                normalizeStatuses(query.statuses()),
                PageRequest.of(page - 1, limit, toSort(query.sortBy(), query.sortDirection()))
        );

        return new ShareListPageView(
                shares.getContent().stream().map(support::toShareView).toList(),
                support.toPagination(shares)
        );
    }

    private void requireAdmin(User admin) {
        RoleCode role = admin.getRole() == null ? null : admin.getRole().getCode();
        if (role != RoleCode.TENANT_ADMIN && role != RoleCode.SUPER_ADMIN) {
            throw new AppException(ErrorCode.FORBIDDEN_ADMIN_REQUIRED);
        }
    }

    private List<UUID> resolveTenantIds(List<UUID> tenantIds, User admin, List<UUID> sharedByIds) {
        List<UUID> normalizedTenantIds = normalizeTenantIds(tenantIds);
        if (isSuperAdmin(admin)) {
            return normalizedTenantIds;
        }

        UUID adminTenantId = support.tenantId(admin);
        if (adminTenantId == null) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
        if (normalizedTenantIds != null && normalizedTenantIds.stream().anyMatch(tenantId -> !adminTenantId.equals(tenantId))) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
        if (sharedByIds != null && !support.allUsersBelongToTenant(sharedByIds, adminTenantId)) {
            throw new AppException(ErrorCode.SHARED_BY_OUT_OF_TENANT);
        }
        return List.of(adminTenantId);
    }

    private List<UUID> normalizeTenantIds(List<UUID> tenantIds) {
        return normalizeUuidList(tenantIds);
    }

    private List<UUID> normalizeUuidList(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        List<UUID> normalized = ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return normalized.isEmpty() ? null : normalized;
    }

    private List<ShareStatus> normalizeStatuses(List<ShareStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return null;
        }
        List<ShareStatus> normalized = statuses.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return normalized.isEmpty() ? null : normalized;
    }

    private Sort toSort(List<ShareSortBy> sortBy, List<SortDirection> sortDirection) {
        List<ShareSortBy> sortByList = sortBy == null || sortBy.isEmpty()
                ? List.of(ShareSortBy.CREATED_AT)
                : sortBy;
        List<SortDirection> sortDirectionList = sortDirection == null
                ? List.of()
                : sortDirection;

        List<Sort.Order> orders = new ArrayList<>();
        for (int i = 0; i < sortByList.size(); i++) {
            ShareSortBy currentSortBy = sortByList.get(i);
            if (currentSortBy == null) {
                continue;
            }
            SortDirection direction = i < sortDirectionList.size() && sortDirectionList.get(i) != null
                    ? sortDirectionList.get(i)
                    : defaultSortDirection(currentSortBy);
            orders.add(new Sort.Order(toSpringDirection(direction), currentSortBy.property()));
        }
        if (orders.isEmpty()) {
            orders.add(new Sort.Order(Sort.Direction.DESC, ShareSortBy.CREATED_AT.property()));
        }
        Collections.reverse(orders);
        return Sort.by(orders);
    }

    private SortDirection defaultSortDirection(ShareSortBy sortBy) {
        return switch (sortBy) {
            case CREATED_AT, EXPIRES_AT, REVOKED_AT -> SortDirection.DESC;
            case STATUS, TENANT_ID -> SortDirection.ASC;
        };
    }

    private Sort.Direction toSpringDirection(SortDirection direction) {
        return direction == SortDirection.ASC ? Sort.Direction.ASC : Sort.Direction.DESC;
    }

    private boolean isSuperAdmin(User admin) {
        return admin.getRole() != null && admin.getRole().getCode() == RoleCode.SUPER_ADMIN;
    }
}
