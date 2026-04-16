package com.filestorage.application.usecase.user;

import com.filestorage.adapter.in.rest.dto.request.SignInRequest;
import com.filestorage.adapter.in.rest.dto.response.TokenResponse;
import com.filestorage.adapter.in.rest.dto.response.UserResponse;
import com.filestorage.adapter.in.rest.security.SecurityUser;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
import com.filestorage.infrastructure.security.JwtUtil;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import com.filestorage.shared.util.SecurityContextUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    private final JwtUtil jwtUtil;
    private final UserJpaRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final TokenBlacklistService tokenBlacklistService;
    private final AuthenticationManager authenticationManager;

    public TokenResponse processSignIn(SignInRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );

            SecurityUser authenticatedUser = (SecurityUser) authentication.getPrincipal();

            // Load full User entity (needed by RefreshTokenService to persist FK)
            User user = userRepository.findById(UUID.fromString(Objects.requireNonNull(authenticatedUser).getUserId()))
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

            // Update last login timestamp
            user.setLastLoginAt(Instant.now());
            userRepository.save(user);

            Instant now = Instant.now();
            String accessToken  = jwtUtil.generateAccessToken(authenticatedUser, now);
            String refreshToken = refreshTokenService.createRefreshToken(user, now);

            log.info("Sign-in successful for user: {}", authenticatedUser.getEmail());
            return TokenResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .build();

        } catch (DisabledException e) {
            throw new AppException(ErrorCode.ACCOUNT_DISABLED);
        } catch (AuthenticationException e) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }
    }

    public TokenResponse processRefresh(String refreshTokenString) {
        if (refreshTokenString == null || refreshTokenString.isBlank()) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }
        User tokenOwner = refreshTokenService.verify(refreshTokenString);

        // Build SecurityUser from entity for JWT generation
        SecurityUser securityUser = new SecurityUser(
                tokenOwner.getId().toString(),
                tokenOwner.getTenant().getId().toString(),
                tokenOwner.getRole().getCode().name(),
                tokenOwner.getEmail()
        );

        Instant now = Instant.now();
        String newAccessToken  = jwtUtil.generateAccessToken(securityUser, now);
        String newRefreshToken = refreshTokenService.rotate(refreshTokenString);

        log.info("Token refresh successful for user {}", tokenOwner.getId());
        return TokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }

    public void logout(String refreshTokenString, String accessTokenString) {
        refreshTokenService.invalidate(refreshTokenString);
        tokenBlacklistService.blacklist(accessTokenString);
        SecurityContextHolder.clearContext();
        log.info("Logout successful, access token blacklisted");
    }

    public UserResponse getUserInfo() {
        User currentUser = getCurrentUserEntity();
        return new UserResponse(
                currentUser.getId(),
                currentUser.getEmail(),
                currentUser.getFullName(),
                currentUser.getRole().getCode().name(),
                currentUser.getTenant().getId()
        );
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    private User getCurrentUserEntity() {
        UUID userId = SecurityContextUtil.getCurrentUserUuidOrThrow();
        return userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }
}


