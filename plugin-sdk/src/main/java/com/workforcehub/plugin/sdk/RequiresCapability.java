package com.workforcehub.plugin.sdk;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注 HostService 方法所需的能力码。
 * 每个方法必须声明且仅声明一个非空能力码；
 * 未注解的方法在注册时会被拒绝（fail closed）。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequiresCapability {
    String value();
}
