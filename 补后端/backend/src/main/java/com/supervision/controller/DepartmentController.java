package com.supervision.controller;

import com.supervision.annotation.RequireRole;
import com.supervision.common.ApiResponse;
import com.supervision.common.PageResult;
import com.supervision.dto.DepartmentDTO;
import com.supervision.entity.Department;
import com.supervision.service.DepartmentService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

// 部门接口仅被管理页面（department-management.html、personnel-management.html）使用，整类要求 admin 角色
@RequireRole("admin")
@RestController
@RequestMapping("/departments")
@AllArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    @GetMapping
    public ApiResponse<PageResult<Department>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(departmentService.list(pageNum, pageSize, name, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<Department> getById(@PathVariable Long id) {
        return ApiResponse.success(departmentService.getById(id));
    }

    @PostMapping
    public ApiResponse<Department> create(@Valid @RequestBody DepartmentDTO dto) {
        return ApiResponse.success(departmentService.create(dto));
    }

    @PutMapping("/{id}")
    public ApiResponse<Department> update(@PathVariable Long id, @Valid @RequestBody DepartmentDTO dto) {
        return ApiResponse.success(departmentService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        departmentService.delete(id);
        return ApiResponse.success(null);
    }
}
