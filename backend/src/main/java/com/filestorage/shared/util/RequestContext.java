package com.filestorage.shared.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

public final class RequestContext {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String REQUEST_ID_ATTRIBUTE = "requestId";
    private static final int USER_AGENT_MAX = 1024;

    private RequestContext() {
    }

    public static Optional<HttpServletRequest> getCurrentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttrs) {
            return Optional.of(servletAttrs.getRequest());
        }
        return Optional.empty();
    }

    public static String getClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }

    public static String getUserAgent(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String ua = request.getHeader("User-Agent");
        if (ua == null) {
            return null;
        }
        return ua.length() > USER_AGENT_MAX ? ua.substring(0, USER_AGENT_MAX) : ua;
    }

    public static String getRequestId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object attr = request.getAttribute(REQUEST_ID_ATTRIBUTE);
        return attr == null ? null : attr.toString();
    }
}
