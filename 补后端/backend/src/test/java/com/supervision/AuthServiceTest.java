package com.supervision;

import com.supervision.common.ApiResponse;
import com.supervision.dto.LoginDTO;
import com.supervision.entity.Role;
import com.supervision.entity.User;
import com.supervision.entity.UserRole;
import com.supervision.exception.BusinessException;
import com.supervision.repository.RoleRepository;
import com.supervision.repository.UserRepository;
import com.supervision.repository.UserRoleRepository;
import com.supervision.service.AuthService;
import com.supervision.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService 单元测试（角色批量加载）")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthService authService;

    private User buildActiveUser() {
        User user = new User();
        user.setId(1L);
        user.setName("admin");
        user.setPhone("13800000000");
        user.setStatus("active");
        user.setPassword("$2a$10$hashed");
        return user;
    }

    private UserRole buildUserRole(Long roleId) {
        UserRole ur = new UserRole();
        ur.setUserId(1L);
        ur.setRoleId(roleId);
        return ur;
    }

    private Role buildRole(Long id, String code) {
        Role role = new Role();
        role.setId(id);
        role.setCode(code);
        role.setName(code);
        return role;
    }

    @Test
    @DisplayName("登录成功 - 角色应通过 findAllById 一次批量加载，不逐条 findById（N+1修复）")
    void testLoginLoadsRolesInBatch() {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("13800000000");
        dto.setPassword("123456");

        when(userRepository.findByPhone("13800000000")).thenReturn(Optional.of(buildActiveUser()));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(userRoleRepository.findByUserId(1L))
                .thenReturn(Arrays.asList(buildUserRole(1L), buildUserRole(2L)));
        when(roleRepository.findAllById(anyList()))
                .thenReturn(Arrays.asList(buildRole(1L, "admin"), buildRole(2L, "supervisor")));
        when(jwtUtil.generateToken(anyLong(), anyString(), anyList())).thenReturn("mock-token");

        ApiResponse<Map<String, Object>> response = authService.login(dto);

        assertEquals(200, response.getCode());
        Map<String, Object> data = response.getData();
        assertEquals(1L, data.get("userId"));
        assertEquals("admin", data.get("userName"));
        assertEquals("mock-token", data.get("token"));
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) data.get("roles");
        assertEquals(Arrays.asList("admin", "supervisor"), roles);

        // 核心断言：批量加载仅调用一次 findAllById，绝不逐条 findById
        verify(roleRepository, times(1)).findAllById(Arrays.asList(1L, 2L));
        verify(roleRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("登录成功 - 用户无角色时不查询角色表，roles 为空列表")
    void testLoginWithNoRolesSkipsRoleQuery() {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("13800000000");
        dto.setPassword("123456");

        when(userRepository.findByPhone("13800000000")).thenReturn(Optional.of(buildActiveUser()));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(userRoleRepository.findByUserId(1L)).thenReturn(Collections.emptyList());
        when(jwtUtil.generateToken(anyLong(), anyString(), anyList())).thenReturn("mock-token");

        ApiResponse<Map<String, Object>> response = authService.login(dto);

        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) response.getData().get("roles");
        assertNotNull(roles);
        assertTrue(roles.isEmpty());
        // 空角色列表直接返回，不触达角色表
        verify(roleRepository, never()).findAllById(anyList());
        verify(roleRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("登录成功 - 角色 code 为 null 时应被过滤")
    void testLoginFiltersNullRoleCode() {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("13800000000");
        dto.setPassword("123456");

        when(userRepository.findByPhone("13800000000")).thenReturn(Optional.of(buildActiveUser()));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(userRoleRepository.findByUserId(1L))
                .thenReturn(Arrays.asList(buildUserRole(1L), buildUserRole(2L)));
        when(roleRepository.findAllById(anyList()))
                .thenReturn(Arrays.asList(buildRole(1L, "admin"), buildRole(2L, null)));
        when(jwtUtil.generateToken(anyLong(), anyString(), anyList())).thenReturn("mock-token");

        ApiResponse<Map<String, Object>> response = authService.login(dto);

        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) response.getData().get("roles");
        assertEquals(Collections.singletonList("admin"), roles);
    }

    @Test
    @DisplayName("登录失败 - 用户不存在应抛出BusinessException且不查询角色")
    void testLoginUserNotFound() {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("nobody");
        dto.setPassword("123456");

        when(userRepository.findByPhone("nobody")).thenReturn(Optional.empty());
        when(userRepository.findByName("nobody")).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.login(dto));
        assertEquals("用户名或密码错误", exception.getMessage());
        verify(roleRepository, never()).findAllById(anyList());
    }

    @Test
    @DisplayName("登录失败 - 密码错误应抛出BusinessException且不查询角色")
    void testLoginWrongPassword() {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("13800000000");
        dto.setPassword("wrong");

        when(userRepository.findByPhone("13800000000")).thenReturn(Optional.of(buildActiveUser()));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.login(dto));
        assertEquals("用户名或密码错误", exception.getMessage());
        verify(roleRepository, never()).findAllById(anyList());
        verify(jwtUtil, never()).generateToken(anyLong(), anyString(), anyList());
    }
}
