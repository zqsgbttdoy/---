package com.supervision.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口级角色校验注解：标注在 Controller 类或方法上。
 * 语义：当前登录用户的角色列表与 value 存在交集即放行，否则返回 403。
 * 方法级注解优先于类级注解；未标注的接口保持"登录即可访问"。
 * 由 {@link com.supervision.interceptor.RoleCheckInterceptor} 在 AuthInterceptor 之后执行校验。
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    /** 允许访问的角色编码列表（如 "admin"），有交集即放行 */
    String[] value();
}
