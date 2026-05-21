package com.filestorage.application.usecase.search;

import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.port.in.search.SearchQuery;
import com.filestorage.domain.port.in.search.SuggestQuery;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Produces a stable hash for the search and SAYT cache keys.
 *
 * <p>The hash always includes a normalized {@code userId} — set to the real
 * {@code userId} only for {@code MEMBER}, and to a literal {@code null} for
 * {@code TENANT_ADMIN} / {@code SUPER_ADMIN}. This single rule blocks
 * cross-user cache leaks (two members in the same tenant have different shared
 * lists → different results) while keeping admin queries tenant-scoped only.
 *
 * <p>Full-text field list: {@code [userId, q, type, mimeType, dateFrom, dateTo,
 * sort, sortDirection, page, size]}<br>
 * SAYT field list: {@code [userId, q, type]} — {@code size} excluded. The
 * SAYT service always queries ES with the configured maxSize, caches the
 * largest possible payload, and trims down to the caller's requested size at
 * read time.
 */
public final class SearchQueryHasher {

    private SearchQueryHasher() {
    }

    public static String hash(SearchQuery query) {
        StringBuilder sb = new StringBuilder();
        sb.append(normalizedUserId(query.userId(), query.role())).append('|');
        sb.append(safe(query.q())).append('|');
        sb.append(query.type()).append('|');
        sb.append(query.mimeTypes() == null ? "" : query.mimeTypes()).append('|');
        sb.append(query.dateFrom()).append('|');
        sb.append(query.dateTo()).append('|');
        sb.append(query.sort()).append('|');
        sb.append(query.sortDirection()).append('|');
        sb.append(query.page()).append('|');
        sb.append(query.size());
        return sha256(sb.toString());
    }

    public static String hash(SuggestQuery query) {
        StringBuilder sb = new StringBuilder();
        sb.append(normalizedUserId(query.userId(), query.role())).append('|');
        sb.append(safe(query.q())).append('|');
        sb.append(query.type());
        return sha256(sb.toString());
    }

    private static String normalizedUserId(UUID userId, RoleCode role) {
        if (role == RoleCode.MEMBER && userId != null) {
            return userId.toString();
        }
        return "null";
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 8 && i < digest.length; i++) {
                hex.append(String.format("%02x", digest[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
