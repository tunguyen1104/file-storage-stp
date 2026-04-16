package com.filestorage.application.usecase.user;

import com.filestorage.adapter.in.rest.security.SecurityUser;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads user from DB by email for {@code DaoAuthenticationProvider} during login.
 * Returns a {@link SecurityUser} carrying the password hash so Spring Security
 * can compare it with the submitted credential.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserJpaRepository userJpaRepository;

    @Override
    public @NonNull UserDetails loadUserByUsername(@NonNull String email) throws UsernameNotFoundException {
        User user = userJpaRepository.findByEmailWithRoleAndTenant(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        return new SecurityUser(
                user.getId().toString(),
                user.getTenant().getId().toString(),
                user.getRole().getCode().name(),
                user.getEmail(),
                user.getPasswordHash(),
                user.isActive()
        );
    }
}

