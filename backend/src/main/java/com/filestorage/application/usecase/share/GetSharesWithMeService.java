package com.filestorage.application.usecase.share;

import com.filestorage.domain.model.share.SharedWithMeView;
import com.filestorage.domain.port.in.share.GetSharesWithMeQuery;
import com.filestorage.domain.port.in.share.GetSharesWithMeUseCase;
import com.filestorage.domain.port.out.ShareRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetSharesWithMeService implements GetSharesWithMeUseCase {

    private final ShareRepositoryPort shareRepository;
    private final ShareServiceSupport support;

    @Override
    @Transactional(readOnly = true)
    public Page<SharedWithMeView> getSharesWithMe(GetSharesWithMeQuery query) {
        var actor = support.resolveActor(query.actorId(), query.actorTenantId());
        int page = support.normalizePage(query.page());
        int limit = support.normalizeLimit(query.limit());
        return shareRepository.findSharesWithMe(
                actor.getId(),
                support.tenantId(actor),
                support.normalizeResourceType(query.resourceType()),
                PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).map(support::toSharedWithMeView);
    }
}
