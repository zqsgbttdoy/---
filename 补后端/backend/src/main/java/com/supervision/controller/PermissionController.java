package com.supervision.controller;

import com.supervision.annotation.RequireRole;
import com.supervision.common.ApiResponse;
import com.supervision.common.PageResult;
import com.supervision.entity.Permission;
import com.supervision.service.PermissionService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// 权限接口仅被管理页面（permission-settings.html）使用，整类要求 admin 角色
@RequireRole("admin")
@RestController
@RequestMapping("/permissions")
@AllArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    @GetMapping
    public ApiResponse<PageResult<Permission>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String module) {
        return ApiResponse.success(permissionService.list(pageNum, pageSize, name, module));
    }

    @GetMapping("/{id}")
    public ApiResponse<Permission> getById(@PathVariable Long id) {
        return ApiResponse.success(permissionService.getById(id));
    }

    @PostMapping
    public ApiResponse<Permission> create(@Valid @RequestBody Permission permission) {
        return ApiResponse.success(permissionService.create(permission));
    }

    @PutMapping("/{id}")
    public ApiResponse<Permission> update(@PathVariable Long id, @Valid @RequestBody Permission permission) {
        return ApiResponse.success(permissionService.update(id, permission));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        permissionService.delete(id);
        return ApiResponse.success(null);
    }

    @GetMapping("/tree")
    public ApiResponse<List<Map<String, Object>>> getTree() {
        return ApiResponse.success(permissionService.getTree());
    }
}
