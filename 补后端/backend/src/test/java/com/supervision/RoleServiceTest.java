package com.supervision;

import com.supervision.entity.Permission;
import com.supervision.entity.Role;
import com.supervision.entity.RolePermission;
import com.supervision.entity.User;
import com.supervision.entity.UserRole;
import com.supervision.exception.BusinessException;
import com.supervision.repository.PermissionRepository;
import com.supervision.repository.RolePermissionRepository;
import com.supervision.repository.RoleRepository;
import com.supervision.repository.UserRepository;
import com.supervision.repository.UserRoleRepository;
import com.supervision.service.RoleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoleService 系统管理员保护单元测试")
class RoleServiceTest {

    @Mock private RoleRepository roleRepository;
    @Mock private RolePermissionRepository rolePermissionRepository;
    @Mock private UserRoleRepository userRoleRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private RoleService roleService;

    private Role role(long id, String code) {
        Role r = new Role();
        r.setId(id);
        r.setCode(code);
        r.setStatus("active");
        return r;
    }

    private Permission permission(long id) {
        Permission p = new Permission();
        p.setId(id);
        return p;
    }

    @SuppressWarnings("unchecked")
    private List<Long> savedPermissionIds() {
        ArgumentCaptor<List<RolePermission>> captor = ArgumentCaptor.forClass(List.class);
        verify(rolePermissionRepository).saveAll(captor.capture());
        return captor.getValue().stream().map(RolePermission::getPermissionId).toList();
    }

    @Test
    @DisplayName("admin 角色保存权限时忽略勾选，始终绑定全部权限项")
    void adminRoleAlwaysGetsAllPermissions() {
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role(1L, "admin")));
        when(permissionRepository.findAll()).thenReturn(List.of(permission(1L), permission(2L), permission(3L)));

        roleService.assignPermissions(1L, List.of(2L));

        verify(rolePermissionRepository).deleteByRoleId(1L);
        assertEquals(List.of(1L, 2L, 3L), savedPermissionIds());
    }

    @Test
    @DisplayName("普通角色按勾选保存权限，去重并忽略空值")
    void normalRoleSavesCheckedPermissionsDistinct() {
        when(roleRepository.findById(2L)).thenReturn(Optional.of(role(2L, "supervisor")));

        roleService.assignPermissions(2L, java.util.Arrays.asList(5L, 5L, null, 6L));

        verify(permissionRepository, never()).findAll();
        assertEquals(List.of(5L, 6L), savedPermissionIds());
    }

    @Test
    @DisplayName("admin 角色不可删除")
    void adminRoleCannotBeDeleted() {
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role(1L, "admin")));

        assertThrows(BusinessException.class, () -> roleService.delete(1L));
        verify(roleRepository, never()).save(any());
    }

    @Test
    @DisplayName("admin 角色分配用户时始终保留内置 admin 账号")
    @SuppressWarnings("unchecked")
    void adminRoleKeepsBuiltInAdminUser() {
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role(1L, "admin")));
        User admin = new User();
        admin.setId(1L);
        admin.setName("admin");
        when(userRepository.findByName("admin")).thenReturn(Optional.of(admin));

        roleService.assignUsers(1L, List.of(7L));

        ArgumentCaptor<List<UserRole>> captor = ArgumentCaptor.forClass(List.class);
        verify(userRoleRepository).saveAll(captor.capture());
        List<Long> userIds = captor.getValue().stream().map(UserRole::getUserId).toList();
        assertTrue(userIds.contains(1L));
        assertTrue(userIds.contains(7L));
    }
}
