package com.filestorage.application.usecase.user;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Manages HttpOnly cookie creation/deletion for access and refresh tokens.
 */
@Service
@RequiredArgsConstructor
public class CookieService {

    private static final String COOKIE_NAME_ACCESS_TOKEN  = "access-token";
    private static final String COOKIE_NAME_REFRESH_TOKEN = "refresh-token";
    private static final String COOKIE_PATH               = "/";
    private static final String SAME_SITE_STRICT          = "Strict";

    @Value("${app.security.jwt.accessTokenExpirationMs}")
    private long accessTokenExpirationMs;

    @Value("${app.security.jwt.refreshTokenExpirationMs}")
    private long refreshTokenExpirationMs;

    @Value("${app.security.securedCookie}")
    private boolean isSecured;

    public ResponseCookie createAccessTokenCookie(String accessToken) {
        return buildCookie(COOKIE_NAME_ACCESS_TOKEN, accessToken, accessTokenExpirationMs);
    }

    public ResponseCookie createRefreshTokenCookie(String refreshToken) {
        return buildCookie(COOKIE_NAME_REFRESH_TOKEN, refreshToken, refreshTokenExpirationMs);
    }

    public ResponseCookie cleanAccessTokenCookie() {
        return buildExpiredCookie(COOKIE_NAME_ACCESS_TOKEN);
    }

    public ResponseCookie cleanRefreshTokenCookie() {
        return buildExpiredCookie(COOKIE_NAME_REFRESH_TOKEN);
    }

    private ResponseCookie buildCookie(String name, String value, long maxAgeMs) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(isSecured)
                .path(COOKIE_PATH)
                .maxAge(Duration.ofMillis(maxAgeMs))
                .sameSite(SAME_SITE_STRICT)
                .build();
    }

    private ResponseCookie buildExpiredCookie(String name) {
        return ResponseCookie.from(name)
                .httpOnly(true)
                .secure(isSecured)
                .path(COOKIE_PATH)
                .maxAge(0)
                .build();
    }
}

