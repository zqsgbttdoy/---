package com.supervision.common;

import java.util.List;

/**
 * 当前登录用户上下文，由 AuthInterceptor 在请求进入时写入、请求结束时清理。
 * 供 Service / Controller 获取当前操作人信息（如 AuthService.info、操作日志记录）。
 */
public class AuthContext {

    public record CurrentUser(Long userId, String userName, List<String> roles) {
    }

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    public static Long getUserId() {
        CurrentUser u = HOLDER.get();
        return u == null ? null : u.userId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
