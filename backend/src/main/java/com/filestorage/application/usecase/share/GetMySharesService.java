package com.filestorage.application.usecase.share;

import com.filestorage.adapter.out.persistence.entity.FileShare;
import com.filestorage.domain.model.share.SharePageView;
import com.filestorage.domain.port.in.share.GetMySharesQuery;
import com.filestorage.domain.port.in.share.GetMySharesUseCase;
import com.filestorage.domain.port.out.ShareRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetMySharesService implements GetMySharesUseCase {

    private final ShareRepositoryPort shareRepository;
    private final ShareServiceSupport support;

    @Override
    @Transactional(readOnly = true)
    public SharePageView getMyShares(GetMySharesQuery query) {
        support.resolveActor(query.actorId());
        int page = support.normalizePage(query.page());
        int limit = support.normalizeLimit(query.limit());
        Page<FileShare> shares = shareRepository.findBySharedBy(
                query.actorId(),
                support.normalizeResourceType(query.resourceType()),
                query.status(),
                PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return new SharePageView(
                shares.getContent().stream().map(support::toShareWithResourceView).toList(),
                support.toPagination(shares)
        );
    }
}
