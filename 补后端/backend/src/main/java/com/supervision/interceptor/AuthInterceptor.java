package com.supervision.interceptor;

import com.alibaba.fastjson2.JSON;
import com.supervision.common.ApiResponse;
import com.supervision.common.AuthContext;
import com.supervision.service.RequestIdentityService;
import com.supervision.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 认证拦截器：校验请求头中的 Bearer JWT。
 * 令牌解析通过后，身份一律以数据库当前状态重建（见 {@link RequestIdentityService}）：
 * 账号不存在或已停用（inactive）返回 401，角色声明以 sys_user_role 当前数据为准，
 * 使撤权、停用即时生效，旧令牌内嵌的过期角色/状态声明不再作为授权依据；
 * 失败返回 401 的统一响应体。
 * 放行规则（登录接口、静态资源、预检请求）在 WebMvcConfig 中通过 excludePathPatterns 配置。
 */
@Component
@AllArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final RequestIdentityService requestIdentityService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        AuthContext.clear();
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        // 放行跨域预检请求
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            writeUnauthorized(response, "未登录或登录已过期，请重新登录");
            return false;
        }

        String token = header.substring(7).trim();
        try {
            Claims claims = jwtUtil.parse(token);
            Long userId = Long.valueOf(claims.getSubject());
            AuthContext.CurrentUser identity = requestIdentityService.refresh(userId);
            if (identity == null) {
                writeUnauthorized(response, "账号不可用或已被停用，请重新登录");
                return false;
            }
            AuthContext.set(identity);
            return true;
        } catch (Exception e) {
            writeUnauthorized(response, "登录已过期或令牌无效，请重新登录");
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContext.clear();
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(JSON.toJSONString(ApiResponse.error(401, message)));
    }
}
