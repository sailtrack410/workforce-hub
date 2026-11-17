package com.workforcehub.common.audit;

import java.lang.annotation.*;

/**
 * 审计日志注解 - 标记需要记录审计的方法
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {

    /**
     * 动作类型，如 CREATE/UPDATE/DELETE/APPROVE 等
     */
    String action();

    /**
     * 目标类型，如 USER/DEPARTMENT/AGENT 等
     */
    String targetType() default "";

    /**
     * 是否记录请求参数
     */
    boolean logParams() default true;
}
