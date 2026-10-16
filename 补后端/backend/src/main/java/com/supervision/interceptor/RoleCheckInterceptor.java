package com.supervision.interceptor;

import com.alibaba.fastjson2.JSON;
import com.supervision.annotation.RequireRole;
import com.supervision.common.ApiResponse;
import com.supervision.common.AuthContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * 角色校验拦截器：在 AuthInterceptor 之后执行（依赖其写入的 AuthContext）。
 * 读取 HandlerMethod 上的 {@link RequireRole} 注解（方法级优先于类级）：
 * - 无注解：直接放行（保持"登录即可访问"）；
 * - 有注解：当前用户角色与注解 value 有交集则放行，否则写统一 403 响应。
 * 403 与 401 严格区分：401 仅由 AuthInterceptor 产生并触发前端登出，403 表示已登录但权限不足。
 */
@Component
public class RoleCheckInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 放行跨域预检请求
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        // 非控制器方法（静态资源等）不做角色校验
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // 方法级注解优先于类级注解
        RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (requireRole == null) {
            requireRole = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }
        if (requireRole == null) {
            return true;
        }

        AuthContext.CurrentUser user = AuthContext.get();
        List<String> roles = user == null ? null : user.roles();
        if (roles != null && !roles.isEmpty()) {
            for (String required : requireRole.value()) {
                if (roles.contains(required)) {
                    return true;
                }
            }
        }

        writeForbidden(response);
        return false;
    }

    private void writeForbidden(HttpServletResponse response) throws Exception {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(JSON.toJSONString(ApiResponse.error(403, "权限不足")));
    }
}
