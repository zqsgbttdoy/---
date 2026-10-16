package com.supervision;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.supervision.common.AuthContext;
import com.supervision.controller.UserController;
import com.supervision.interceptor.RoleCheckInterceptor;
import com.supervision.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * UserController 接口级权限校验集成测试（standalone MockMvc + RoleCheckInterceptor）。
 * 验证 P0 越权修复：PUT /users/{id}/reset-password
 * - 普通角色 / 无角色用户 -> 403，且响应体为统一 ApiResponse 错误契约（与 401 可区分）；
 * - admin 角色 -> 200 正常执行业务。
 */
@DisplayName("UserController reset-password 越权防护集成测试")
class UserControllerRoleCheckTest {

    private MockMvc mockMvc;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = Mockito.mock(UserService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService))
                .addInterceptors(new RoleCheckInterceptor())
                .build();
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    private void login(List<String> roles) {
        AuthContext.set(new AuthContext.CurrentUser(9L, "测试用户", roles));
    }

    @Test
    @DisplayName("普通角色调用 reset-password -> 403 权限不足（非 401，不触发前端登出）")
    void testResetPasswordForbiddenForNormalRole() throws Exception {
        login(List.of("user"));
        MockHttpServletResponse response = mockMvc.perform(put("/users/1/reset-password"))
                .andReturn().getResponse();

        assertEquals(403, response.getStatus());
        JSONObject body = JSON.parseObject(response.getContentAsString(StandardCharsets.UTF_8));
        assertEquals(3, String.valueOf(body.getIntValue("code")).length());
        assertEquals(403, body.getIntValue("code"));
        assertEquals("权限不足", body.getString("message"));
        assertNull(body.get("data"));
        // 业务方法不得被执行
        Mockito.verify(userService, Mockito.never()).resetPassword(any(), any());
    }

    @Test
    @DisplayName("无角色用户调用 reset-password -> 403")
    void testResetPasswordForbiddenForNoRole() throws Exception {
        login(Collections.emptyList());
        MockHttpServletResponse response = mockMvc.perform(put("/users/1/reset-password"))
                .andReturn().getResponse();
        assertEquals(403, response.getStatus());
        Mockito.verify(userService, Mockito.never()).resetPassword(any(), any());
    }

    @Test
    @DisplayName("admin 角色调用 reset-password -> 200 正常返回新密码")
    void testResetPasswordAllowedForAdmin() throws Exception {
        login(List.of("admin"));
        Mockito.when(userService.resetPassword(eq(1L), any())).thenReturn("Ab@12345678");

        MockHttpServletResponse response = mockMvc.perform(put("/users/1/reset-password"))
                .andReturn().getResponse();

        assertEquals(200, response.getStatus());
        JSONObject body = JSON.parseObject(response.getContentAsString(StandardCharsets.UTF_8));
        assertEquals(200, body.getIntValue("code"));
        assertEquals("Ab@12345678", body.getString("data"));
        Mockito.verify(userService).resetPassword(eq(1L), any());
    }

    @Test
    @DisplayName("无注解的 GET /users/{id}：普通角色仍可访问（读接口不受影响）")
    void testGetUserStillOpenForNormalRole() throws Exception {
        login(List.of("user"));
        Mockito.when(userService.getById(1L)).thenReturn(null);

        MockHttpServletResponse response = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/users/1"))
                .andReturn().getResponse();
        assertEquals(200, response.getStatus());
    }
}
