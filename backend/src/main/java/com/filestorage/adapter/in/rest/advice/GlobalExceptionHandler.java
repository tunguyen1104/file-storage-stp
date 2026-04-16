package com.filestorage.adapter.in.rest.advice;

import com.filestorage.adapter.in.rest.dto.response.ErrorResponse;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestControllerAdvice
@RequiredArgsConstructor
@Log4j2
public class GlobalExceptionHandler {

    private final MessageSource messageSource;

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponse<Object>> handleAppException(AppException ex) {
        String message = resolveMessage(ex.getError());
        return ResponseEntity.status(ex.getError().getHttpStatus())
                .body(ErrorResponse.buildError(message, ex.getError().getCode()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse<Map<String, List<String>>>> handleValidation(
            MethodArgumentNotValidException ex) {
        Locale locale = LocaleContextHolder.getLocale();
        Map<String, List<String>> errors = getFieldErrors(ex, locale);
        return ResponseEntity.badRequest()
                .body(ErrorResponse.buildError(errors, ErrorCode.INVALID_PAYLOAD.getCode()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse<Object>> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex) {
        String message = resolveMessage(ErrorCode.INVALID_PAYLOAD);
        return ResponseEntity.status(ErrorCode.INVALID_PAYLOAD.getHttpStatus())
                .body(ErrorResponse.buildError(message, ErrorCode.INVALID_PAYLOAD.getCode()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse<Object>> handleAuthenticationException(AuthenticationException ex) {
        log.debug("AuthenticationException: {}", ex.getMessage());
        String message = resolveMessage(ErrorCode.ACCESS_DENIED);
        return ResponseEntity.status(ErrorCode.ACCESS_DENIED.getHttpStatus())
                .body(ErrorResponse.buildError(message, ErrorCode.ACCESS_DENIED.getCode()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse<Object>> handleAccessDeniedException(AccessDeniedException ex) {
        log.debug("AccessDeniedException: {}", ex.getMessage());
        String message = resolveMessage(ErrorCode.FORBIDDEN);
        return ResponseEntity.status(ErrorCode.FORBIDDEN.getHttpStatus())
                .body(ErrorResponse.buildError(message, ErrorCode.FORBIDDEN.getCode()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse<Object>> handleGlobalException(Exception ex) {
        log.error("Unhandled exception: ", ex);
        String message = resolveMessage(ErrorCode.INTERNAL_SERVER_ERROR);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.buildError(message, ErrorCode.INTERNAL_SERVER_ERROR.getCode()));
    }

    // --- Helpers ---

    private Map<String, List<String>> getFieldErrors(MethodArgumentNotValidException ex, Locale locale) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            String message = messageSource.getMessage(fieldError, locale);
            errors.computeIfAbsent(fieldError.getField(), k -> new ArrayList<>()).add(message);
        }
        return errors;
    }

    /**
     * Resolves the message for an {@link ErrorCode}.
     * Falls back to the raw key string if no entry is found in the message bundle,
     * so codes like "Token expired" work without a properties entry.
     */
    private String resolveMessage(ErrorCode errorCode) {
        return messageSource.getMessage(
                errorCode.getKey(), null, errorCode.getKey(), LocaleContextHolder.getLocale());
    }
}

