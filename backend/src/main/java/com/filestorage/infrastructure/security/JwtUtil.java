package com.filestorage.infrastructure.security;

import com.filestorage.adapter.in.rest.security.SecurityUser;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.text.ParseException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * JWT utility using {@code nimbus-jose-jwt} (HMAC-SHA256).
 * <p>
 * Claims embedded in every access token:
 * <ul>
 *   <li>{@code sub} — user email (principal)</li>
 *   <li>{@code userId} — UUID of the user</li>
 *   <li>{@code tenantId} — UUID of the tenant</li>
 *   <li>{@code role} — role code (e.g. TENANT_ADMIN)</li>
 *   <li>{@code jti} — unique token ID for blacklisting</li>
 * </ul>
 */
@Component
public class JwtUtil {

    @Value("${app.security.jwt.secret}")
    private String jwtSecret;

    @Value("${app.security.jwt.accessTokenExpirationMs}")
    private long accessTokenExpirationMs;

    private SecretKey secretKey;

    private SecretKey key() {
        if (secretKey == null) {
            byte[] keyBytes = Base64.getDecoder().decode(jwtSecret);
            secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");
        }
        return secretKey;
    }

    /**
     * Generate a signed access token embedding identity claims.
     */
    public String generateAccessToken(SecurityUser securityUser, Instant now) {
        try {
            JWSSigner signer = new MACSigner(key());

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(securityUser.getEmail())
                    .claim("userId", securityUser.getUserId())
                    .claim("tenantId", securityUser.getTenantId())
                    .claim("role", securityUser.getRole())
                    .jwtID(UUID.randomUUID().toString())
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusMillis(accessTokenExpirationMs)))
                    .build();

            SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            signedJWT.sign(signer);

            return signedJWT.serialize();
        } catch (JOSEException e) {
            throw new AppException(ErrorCode.TOKEN_GENERATION_FAILED);
        }
    }

    /**
     * Verify token signature and expiration.
     */
    public boolean isValidToken(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            JWSVerifier verifier = new MACVerifier(key());

            if (!signedJWT.verify(verifier)) {
                return false;
            }

            Date expiration = signedJWT.getJWTClaimsSet().getExpirationTime();
            return expiration != null && expiration.after(new Date());
        } catch (ParseException | JOSEException e) {
            return false;
        }
    }

    public String getEmailFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    public String getUserIdFromToken(String token) {
        return (String) parseClaims(token).getClaim("userId");
    }

    public String getTenantIdFromToken(String token) {
        return (String) parseClaims(token).getClaim("tenantId");
    }

    public String getRoleFromToken(String token) {
        return (String) parseClaims(token).getClaim("role");
    }

    /**
     * Return JTI (JWT ID) — used as key in Redis blacklist.
     */
    public String getJtiFromToken(String token) {
        return parseClaims(token).getJWTID();
    }

    /**
     * Return seconds remaining before token expires. Returns 0 if already expired.
     */
    public long getRemainingTtlSeconds(String token) {
        Date expiration = parseClaims(token).getExpirationTime();
        if (expiration == null) return 0L;
        long remaining = expiration.toInstant().getEpochSecond() - Instant.now().getEpochSecond();
        return Math.max(0L, remaining);
    }

    private JWTClaimsSet parseClaims(String token) {
        try {
            return SignedJWT.parse(token).getJWTClaimsSet();
        } catch (ParseException e) {
            throw new AppException(ErrorCode.TOKEN_PARSE_FAILED);
        }
    }
}
