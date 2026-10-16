package com.supervision;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.supervision.annotation.RequireRole;
import com.supervision.common.AuthContext;
import com.supervision.interceptor.RoleCheckInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RoleCheckInterceptor 单元测试。
 * 覆盖：无注解放行、admin 放行、普通角色 403、roles 为空/null 403、
 * 类级注解生效、方法级注解优先于类级、非 HandlerMethod 放行、OPTIONS 放行、403 响应契约。
 */
@DisplayName("RoleCheckInterceptor 接口级角色校验单元测试")
class RoleCheckInterceptorTest {

    private final RoleCheckInterceptor interceptor = new RoleCheckInterceptor();

    /** 无任何注解的控制器 */
    static class PlainController {
        public void open() {
        }
    }

    /** 类级 @RequireRole("admin")，其中一个方法用方法级注解覆盖为 supervisor */
    @RequireRole("admin")
    static class AdminClassController {
        public void inherited() {
        }

        @RequireRole("supervisor")
        public void overridden() {
        }
    }

    /** 仅方法级 @RequireRole("admin") */
    static class MethodLevelController {
        @RequireRole("admin")
        public void adminOnly() {
        }
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    private HandlerMethod handler(Object bean, String methodName) throws Exception {
        return new HandlerMethod(bean, bean.getClass().getMethod(methodName));
    }

    private void login(List<String> roles) {
        AuthContext.set(new AuthContext.CurrentUser(9L, "测试用户", roles));
    }

    private boolean invoke(HandlerMethod hm, MockHttpServletResponse response) throws Exception {
        return interceptor.preHandle(new MockHttpServletRequest("GET", "/any"), response, hm);
    }

    @Test
    @DisplayName("无注解的接口：任何登录用户直接放行")
    void testNoAnnotationPass() throws Exception {
        login(Collections.emptyList());
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertTrue(invoke(handler(new PlainController(), "open"), response));
        assertEquals(200, response.getStatus());
    }

    @Test
    @DisplayName("方法级 @RequireRole(\"admin\")：admin 角色放行")
    void testAdminPass() throws Exception {
        login(List.of("admin"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertTrue(invoke(handler(new MethodLevelController(), "adminOnly"), response));
    }

    @Test
    @DisplayName("方法级 @RequireRole(\"admin\")：普通角色返回 403 且响应体符合 ApiResponse 契约")
    void testNormalRoleForbidden() throws Exception {
        login(List.of("user"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(invoke(handler(new MethodLevelController(), "adminOnly"), response));
        assertEquals(403, response.getStatus());
        JSONObject body = JSON.parseObject(response.getContentAsString(StandardCharsets.UTF_8));
        assertEquals(403, body.getIntValue("code"));
        assertEquals("权限不足", body.getString("message"));
        assertNull(body.get("data"));
    }

    @Test
    @DisplayName("roles 为空列表：403")
    void testEmptyRolesForbidden() throws Exception {
        login(Collections.emptyList());
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(invoke(handler(new MethodLevelController(), "adminOnly"), response));
        assertEquals(403, response.getStatus());
    }

    @Test
    @DisplayName("roles 为 null 或未登录上下文：403")
    void testNullRolesForbidden() throws Exception {
        login(null);
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(invoke(handler(new MethodLevelController(), "adminOnly"), response));
        assertEquals(403, response.getStatus());

        AuthContext.clear();
        MockHttpServletResponse response2 = new MockHttpServletResponse();
        assertFalse(invoke(handler(new MethodLevelController(), "adminOnly"), response2));
        assertEquals(403, response2.getStatus());
    }

    @Test
    @DisplayName("类级注解：未标注方法继承类级 admin 要求")
    void testClassLevelAnnotation() throws Exception {
        login(List.of("user"));
        MockHttpServletResponse forbidden = new MockHttpServletResponse();
        assertFalse(invoke(handler(new AdminClassController(), "inherited"), forbidden));
        assertEquals(403, forbidden.getStatus());

        login(List.of("admin"));
        MockHttpServletResponse ok = new MockHttpServletResponse();
        assertTrue(invoke(handler(new AdminClassController(), "inherited"), ok));
    }

    @Test
    @DisplayName("方法级注解优先于类级：supervisor 放行、admin 被拒")
    void testMethodOverridesClass() throws Exception {
        login(List.of("supervisor"));
        MockHttpServletResponse ok = new MockHttpServletResponse();
        assertTrue(invoke(handler(new AdminClassController(), "overridden"), ok));

        login(List.of("admin"));
        MockHttpServletResponse forbidden = new MockHttpServletResponse();
        assertFalse(invoke(handler(new AdminClassController(), "overridden"), forbidden));
        assertEquals(403, forbidden.getStatus());
    }

    @Test
    @DisplayName("非 HandlerMethod（静态资源等）直接放行")
    void testNonHandlerMethodPass() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertTrue(interceptor.preHandle(new MockHttpServletRequest("GET", "/uploads/x.jpg"), response, new Object()));
    }

    @Test
    @DisplayName("OPTIONS 预检请求直接放行")
    void testOptionsPass() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertTrue(interceptor.preHandle(new MockHttpServletRequest("OPTIONS", "/users"), response,
                handler(new MethodLevelController(), "adminOnly")));
    }
}
