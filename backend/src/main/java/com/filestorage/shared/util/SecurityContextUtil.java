package com.filestorage.shared.util;

import com.filestorage.adapter.in.rest.security.SecurityUser;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

public final class SecurityContextUtil {

    private SecurityContextUtil() {
    }

    /**
     * Returns the current {@link SecurityUser} if a valid, authenticated principal
     * is present in the {@code SecurityContext}, otherwise {@link Optional#empty()}.
     */
    public static Optional<SecurityUser> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof SecurityUser securityUser) {
            return Optional.of(securityUser);
        }
        return Optional.empty();
    }

    /**
     * Returns the current user's {@code userId} as a {@link UUID}, or throws
     * {@link AppException} with {@code ACCESS_DENIED} if not authenticated.
     */
    public static UUID getCurrentUserUuidOrThrow() {
        String userId = getCurrentUser()
                .map(SecurityUser::getUserId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCESS_DENIED));
        return UUID.fromString(userId);
    }
}

