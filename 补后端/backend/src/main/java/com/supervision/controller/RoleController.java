package com.supervision.controller;

import com.supervision.annotation.RequireRole;
import com.supervision.common.ApiResponse;
import com.supervision.common.PageResult;
import com.supervision.entity.Role;
import com.supervision.service.RoleService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// 角色接口仅被管理页面（permission-settings.html）使用，整类要求 admin 角色
@RequireRole("admin")
@RestController
@RequestMapping("/roles")
@AllArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    public ApiResponse<PageResult<Role>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(roleService.list(pageNum, pageSize, name, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<Role> getById(@PathVariable Long id) {
        return ApiResponse.success(roleService.getById(id));
    }

    @PostMapping
    public ApiResponse<Role> create(@Valid @RequestBody Role role) {
        return ApiResponse.success(roleService.create(role));
    }

    @PutMapping("/{id}")
    public ApiResponse<Role> update(@PathVariable Long id, @Valid @RequestBody Role role) {
        return ApiResponse.success(roleService.update(id, role));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return ApiResponse.success(null);
    }

    @GetMapping("/{id}/permissions")
    public ApiResponse<List<Long>> getPermissionIds(@PathVariable Long id) {
        return ApiResponse.success(roleService.getPermissionIds(id));
    }

    @PostMapping("/{id}/permissions")
    public ApiResponse<Void> assignPermissions(@PathVariable Long id, @Valid @RequestBody List<Long> permissionIds) {
        roleService.assignPermissions(id, permissionIds);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/users")
    public ApiResponse<Void> assignUsers(@PathVariable Long id, @Valid @RequestBody List<Long> userIds) {
        roleService.assignUsers(id, userIds);
        return ApiResponse.success(null);
    }
}
