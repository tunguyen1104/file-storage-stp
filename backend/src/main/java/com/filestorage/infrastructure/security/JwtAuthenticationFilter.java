package com.filestorage.infrastructure.security;

import com.filestorage.adapter.in.rest.security.SecurityUser;
import com.filestorage.application.usecase.user.TokenBlacklistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.util.Arrays;

/**
 * Reads access token from HttpOnly cookie, validates JWT signature + expiry + blacklist,
 * then populates SecurityContext with a claims-based {@link SecurityUser}.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;
    private final HandlerExceptionResolver exceptionResolver;

    public JwtAuthenticationFilter(JwtUtil jwtUtil,
                                   TokenBlacklistService tokenBlacklistService,
                                   @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        this.jwtUtil = jwtUtil;
        this.tokenBlacklistService = tokenBlacklistService;
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String accessToken = extractAccessTokenFromCookie(request);

        try {
            // (a) signature + expiry valid?  (b) not blacklisted?
            boolean tokenIsUsable = accessToken != null
                    && jwtUtil.isValidToken(accessToken)
                    && !tokenBlacklistService.isBlacklisted(accessToken);

            if (tokenIsUsable) {
                // Build SecurityUser directly from JWT claims — no DB round-trip per request
                String userId   = jwtUtil.getUserIdFromToken(accessToken);
                String tenantId = jwtUtil.getTenantIdFromToken(accessToken);
                String role     = jwtUtil.getRoleFromToken(accessToken);
                String email    = jwtUtil.getEmailFromToken(accessToken);

                SecurityUser principal = new SecurityUser(userId, tenantId, role, email);

                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                principal, null, principal.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }

        } catch (Exception ex) {
            SecurityContextHolder.clearContext();
            exceptionResolver.resolveException(request, response, null, ex);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String extractAccessTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(cookie -> "access-token".equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}

