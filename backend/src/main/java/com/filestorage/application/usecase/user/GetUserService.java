package com.filestorage.application.usecase.user;

import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.mapper.UserViewMapper;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
import com.filestorage.domain.model.user.UserView;
import com.filestorage.domain.port.in.user.GetUserUseCase;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetUserService implements GetUserUseCase {

    private final UserJpaRepository userRepository;
    private final UserViewMapper userViewMapper;

    @Override
    @Transactional(readOnly = true)
    public UserView execute(GetUserQuery query) {
        User target = userRepository.findByIdWithRoleAndTenant(query.targetUserId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        UserAccessPolicy.requireCanRead(target, query.actorUserId(), query.actorTenantId(), query.actorRole());
        return userViewMapper.toUserView(target);
    }
}

