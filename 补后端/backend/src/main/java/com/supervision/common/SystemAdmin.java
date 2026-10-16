package com.supervision.common;

import com.supervision.entity.Role;
import com.supervision.entity.User;

/**
 * 内置系统管理员约定：角色编码 admin + 账号名 admin。
 * 后端鉴权（@RequireRole / ProjectAccessService.isAdmin）只认该角色编码，
 * 因此该角色与该账号受保护：不可删除、不可停用、不可被撤掉全部权限。
 */
public final class SystemAdmin {

    public static final String ROLE_CODE = "admin";
    public static final String USER_NAME = "admin";

    private SystemAdmin() {
    }

    public static boolean isAdminRole(Role role) {
        return role != null && ROLE_CODE.equals(role.getCode());
    }

    public static boolean isBuiltInAdmin(User user) {
        return user != null && USER_NAME.equals(user.getName());
    }
}
