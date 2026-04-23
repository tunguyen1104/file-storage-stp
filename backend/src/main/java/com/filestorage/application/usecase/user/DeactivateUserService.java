package com.filestorage.application.usecase.user;

import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.mapper.UserViewMapper;
import com.filestorage.adapter.out.persistence.repository.RefreshTokenJpaRepository;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
import com.filestorage.domain.port.in.user.DeactivateUserUseCase;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeactivateUserService implements DeactivateUserUseCase {

    private final UserJpaRepository userRepository;
    private final RefreshTokenJpaRepository refreshTokenRepository;
    private final UserViewMapper userViewMapper;

    @Override
    @Transactional
    public com.filestorage.domain.model.user.UserView execute(DeactivateUserCommand command) {

        User target = userRepository.findByIdWithRoleAndTenant(command.targetUserId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        UserWritePolicy.requireSameTenantForTenantAdmin(target, command.actorTenantId(), command.actorRole());
        UserWritePolicy.requireNotSelfDeactivation(command.actorUserId(), target.getId(), command.actorRole());

        if (target.isActive()) {
            target.setActive(false);
            userRepository.save(target);
        }

        refreshTokenRepository.deleteByUserId(target.getId());
        return userViewMapper.toUserView(target);
    }
}

