package com.filestorage.application.usecase.audit;

import com.filestorage.adapter.in.rest.security.SecurityUser;
import com.filestorage.domain.port.in.audit.LogAuditEventUseCase;
import com.filestorage.domain.port.in.audit.LogAuditEventUseCase.AuditEvent;
import com.filestorage.infrastructure.audit.Auditable;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.util.RequestContext;
import com.filestorage.shared.util.SecurityContextUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditingAspect {

    private final LogAuditEventUseCase auditLogger;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer paramDiscoverer = new DefaultParameterNameDiscoverer();

    @Around("@annotation(auditable)")
    public Object around(ProceedingJoinPoint pjp, Auditable auditable) throws Throwable {
        // Snapshot actor + request info BEFORE proceed — must capture in caller thread.
        SecurityUser actor = SecurityContextUtil.getCurrentUser().orElse(null);
        HttpServletRequest request = RequestContext.getCurrentRequest().orElse(null);
        String ip = RequestContext.getClientIp(request);
        String ua = RequestContext.getUserAgent(request);
        String requestId = RequestContext.getRequestId(request);

        Object result = null;
        Throwable thrown = null;
        try {
            result = pjp.proceed();
            return result;
        } catch (Throwable t) {
            thrown = t;
            throw t;
        } finally {
            if (thrown == null || auditable.logOnFailure()) {
                try {
                    emit(pjp, auditable, result, thrown, actor, ip, ua, requestId);
                } catch (Exception ex) {
                    log.warn("audit_log_aspect_failed action={}: {}", auditable.action(), ex.getMessage());
                }
            }
        }
    }

    private void emit(ProceedingJoinPoint pjp, Auditable auditable, Object result, Throwable thrown,
                      SecurityUser actor, String ip, String ua, String requestId) {
        EvaluationContext ctx = buildContext(pjp, result);

        UUID actorUserId = actor == null ? null : parseUuidSilently(actor.getUserId());
        UUID actorTenantId = actor == null ? null : parseUuidSilently(actor.getTenantId());
        String actorRole = actor == null ? null : actor.getRole();

        UUID fileId = evalUuid(auditable.fileIdSpel(), ctx);
        UUID rowTenantId = evalUuid(auditable.tenantIdSpel(), ctx);
        if (rowTenantId == null) {
            rowTenantId = actorTenantId;
        }

        Map<String, Object> extra = new HashMap<>(evalExtra(auditable.extraSpel(), ctx));
        if (actorRole != null) {
            extra.put("actor_role", actorRole);
        }
        if (actorTenantId != null) {
            extra.put("actor_tenant_id", actorTenantId.toString());
        }
        if (requestId != null) {
            extra.put("request_id", requestId);
        }
        if (thrown != null) {
            extra.put("failed", true);
            extra.put("error_code", thrown instanceof AppException ae
                    ? ae.getError().name()
                    : thrown.getClass().getSimpleName());
        }

        AuditEvent event = new AuditEvent(
                auditable.action(),
                rowTenantId,
                actorUserId,
                fileId,
                actorRole,
                ip,
                ua,
                requestId,
                extra
        );
        auditLogger.logAsync(event);
    }

    private EvaluationContext buildContext(ProceedingJoinPoint pjp, Object result) {
        StandardEvaluationContext ctx = new StandardEvaluationContext();
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Method method = sig.getMethod();
        Object[] args = pjp.getArgs();
        String[] names = paramDiscoverer.getParameterNames(method);
        if (names != null) {
            for (int i = 0; i < names.length; i++) {
                ctx.setVariable(names[i], args[i]);
            }
        }
        ctx.setVariable("args", args);
        ctx.setVariable("result", result);
        return ctx;
    }

    private UUID evalUuid(String spel, EvaluationContext ctx) {
        if (spel == null || spel.isBlank()) {
            return null;
        }
        try {
            Object value = parser.parseExpression(spel).getValue(ctx);
            if (value == null) {
                return null;
            }
            if (value instanceof UUID u) {
                return u;
            }
            return UUID.fromString(value.toString());
        } catch (Exception ex) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> evalExtra(String spel, EvaluationContext ctx) {
        if (spel == null || spel.isBlank()) {
            return Map.of();
        }
        try {
            Object value = parser.parseExpression(spel).getValue(ctx);
            if (value instanceof Map<?, ?> m) {
                return (Map<String, Object>) m;
            }
            return Map.of();
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private UUID parseUuidSilently(String s) {
        try {
            return s == null ? null : UUID.fromString(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
