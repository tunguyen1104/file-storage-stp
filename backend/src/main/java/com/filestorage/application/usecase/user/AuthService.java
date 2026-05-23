package com.filestorage.application.usecase.user;

import com.filestorage.adapter.in.rest.dto.request.SignInRequest;
import com.filestorage.adapter.in.rest.dto.response.TokenResponse;
import com.filestorage.adapter.in.rest.dto.response.UserResponse;
import com.filestorage.adapter.in.rest.security.SecurityUser;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
import com.filestorage.domain.model.audit.AuditAction;
import com.filestorage.domain.port.in.audit.LogAuditEventUseCase;
import com.filestorage.domain.port.in.audit.LogAuditEventUseCase.AuditEvent;
import com.filestorage.infrastructure.security.JwtUtil;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import com.filestorage.shared.util.RequestContext;
import com.filestorage.shared.util.SecurityContextUtil;
import jakarta.servlet.http.HttpServletRequest;
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
import java.util.HashMap;
import java.util.Map;
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
    private final LogAuditEventUseCase auditLogger;

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
            auditLoginSuccess(user);
            return TokenResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .build();

        } catch (DisabledException e) {
            auditLoginFailed(request.email(), ErrorCode.ACCOUNT_DISABLED);
            throw new AppException(ErrorCode.ACCOUNT_DISABLED);
        } catch (AuthenticationException e) {
            auditLoginFailed(request.email(), ErrorCode.INVALID_CREDENTIALS);
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
        SecurityUser actor = SecurityContextUtil.getCurrentUser().orElse(null);
        refreshTokenService.invalidate(refreshTokenString);
        tokenBlacklistService.blacklist(accessTokenString);
        SecurityContextHolder.clearContext();
        log.info("Logout successful, access token blacklisted");
        auditLogout(actor);
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

    private void auditLoginSuccess(User user) {
        HttpServletRequest request = RequestContext.getCurrentRequest().orElse(null);
        Map<String, Object> extra = baseExtra(request);
        extra.put("actor_role", user.getRole().getCode().name());
        extra.put("actor_tenant_id", user.getTenant().getId().toString());
        auditLogger.logAsync(new AuditEvent(
                AuditAction.LOGIN_SUCCESS,
                user.getTenant().getId(),
                user.getId(),
                null,
                user.getRole().getCode().name(),
                RequestContext.getClientIp(request),
                RequestContext.getUserAgent(request),
                RequestContext.getRequestId(request),
                extra
        ));
    }

    private void auditLoginFailed(String emailAttempted, ErrorCode reason) {
        // D6: skip audit if email cannot be resolved (tenant_id NOT NULL).
        User user = userRepository.findByEmailWithRoleAndTenant(emailAttempted == null ? "" : emailAttempted.trim().toLowerCase()).orElse(null);
        if (user == null || user.getTenant() == null) {
            return;
        }
        HttpServletRequest request = RequestContext.getCurrentRequest().orElse(null);
        Map<String, Object> extra = baseExtra(request);
        extra.put("actor_role", "ANONYMOUS");
        extra.put("actor_tenant_id", user.getTenant().getId().toString());
        extra.put("email_attempted", emailAttempted);
        extra.put("failed", true);
        extra.put("error_code", reason.name());
        auditLogger.logAsync(new AuditEvent(
                AuditAction.LOGIN_FAILED,
                user.getTenant().getId(),
                null,
                null,
                "ANONYMOUS",
                RequestContext.getClientIp(request),
                RequestContext.getUserAgent(request),
                RequestContext.getRequestId(request),
                extra
        ));
    }

    private void auditLogout(SecurityUser actor) {
        if (actor == null) {
            return;
        }
        HttpServletRequest request = RequestContext.getCurrentRequest().orElse(null);
        UUID tenantId = parseUuid(actor.getTenantId());
        UUID userId = parseUuid(actor.getUserId());
        if (tenantId == null) {
            return;
        }
        Map<String, Object> extra = baseExtra(request);
        extra.put("actor_role", actor.getRole());
        extra.put("actor_tenant_id", tenantId.toString());
        auditLogger.logAsync(new AuditEvent(
                AuditAction.LOGOUT,
                tenantId,
                userId,
                null,
                actor.getRole(),
                RequestContext.getClientIp(request),
                RequestContext.getUserAgent(request),
                RequestContext.getRequestId(request),
                extra
        ));
    }

    private Map<String, Object> baseExtra(HttpServletRequest request) {
        Map<String, Object> extra = new HashMap<>();
        String requestId = RequestContext.getRequestId(request);
        if (requestId != null) {
            extra.put("request_id", requestId);
        }
        return extra;
    }

    private UUID parseUuid(String s) {
        try {
            return s == null ? null : UUID.fromString(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}


