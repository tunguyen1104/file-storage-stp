package com.filestorage.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Delegates Spring Security 401/403 exceptions to the global
 * {@code @RestControllerAdvice} so they follow the same error response format.
 */
@Component
public class DelegatedSecurityExceptionHandler implements AccessDeniedHandler, AuthenticationEntryPoint {

    private final HandlerExceptionResolver resolver;

    public DelegatedSecurityExceptionHandler(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void handle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                       @NonNull AccessDeniedException accessDeniedException) {
        resolver.resolveException(request, response, null, accessDeniedException);
    }

    @Override
    public void commence(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                         @NonNull AuthenticationException authException) {
        resolver.resolveException(request, response, null, authException);
    }
}

