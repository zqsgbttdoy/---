package com.supervision.controller;

import com.supervision.annotation.RequireRole;
import com.supervision.common.ApiResponse;
import com.supervision.common.PageResult;
import com.supervision.dto.UserDTO;
import com.supervision.entity.User;
import com.supervision.service.UserService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@AllArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ApiResponse<PageResult<User>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) Long projectId) {
        return ApiResponse.success(userService.list(pageNum, pageSize, name, status, deptId, projectId));
    }

    @GetMapping("/{id}")
    public ApiResponse<User> getById(@PathVariable Long id) {
        return ApiResponse.success(userService.getById(id));
    }

    @RequireRole("admin")
    @PostMapping
    public ApiResponse<User> create(@Valid @RequestBody UserDTO dto) {
        return ApiResponse.success(userService.create(dto));
    }

    @RequireRole("admin")
    @PutMapping("/{id}")
    public ApiResponse<User> update(@PathVariable Long id, @Valid @RequestBody UserDTO dto) {
        return ApiResponse.success(userService.update(id, dto));
    }

    @RequireRole("admin")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return ApiResponse.success(null);
    }

    @RequireRole("admin")
    @PutMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @RequestParam String status) {
        userService.updateStatus(id, status);
        return ApiResponse.success(null);
    }

    @RequireRole("admin")
    @PutMapping("/{id}/reset-password")
    public ApiResponse<String> resetPassword(@PathVariable Long id,
                                             @RequestParam(required = false) String password) {
        String newPassword = userService.resetPassword(id, password);
        return ApiResponse.success("密码重置成功", newPassword);
    }
}
