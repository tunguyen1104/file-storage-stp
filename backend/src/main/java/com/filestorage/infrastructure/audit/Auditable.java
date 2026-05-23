package com.filestorage.infrastructure.audit;

import com.filestorage.domain.model.audit.AuditAction;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    AuditAction action();

    /** SpEL returning UUID of the audited file (e.g. {@code #command.fileId()}). Empty = null. */
    String fileIdSpel() default "";

    /** SpEL returning UUID for tenant_id of the row. Empty = fall back to actor's tenantId. */
    String tenantIdSpel() default "";

    /** SpEL returning a {@code Map<String,Object>} merged into extra_data. */
    String extraSpel() default "";

    /** If true and method throws, still log the row with {@code failed=true}. */
    boolean logOnFailure() default true;
}
