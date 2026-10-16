package com.supervision.controller;

import com.supervision.common.ApiResponse;
import com.supervision.common.PageResult;
import com.supervision.dto.ApproveDTO;
import com.supervision.dto.SafetyLogDTO;
import com.supervision.entity.SafetyLog;
import com.supervision.service.SafetyLogService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/safety-logs")
@AllArgsConstructor
public class SafetyLogController {

    private final SafetyLogService safetyLogService;

    @GetMapping
    public ApiResponse<PageResult<SafetyLog>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(safetyLogService.list(pageNum, pageSize, projectId, code, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<SafetyLog> getById(@PathVariable Long id) {
        return ApiResponse.success(safetyLogService.getById(id));
    }

    @PostMapping
    public ApiResponse<SafetyLog> create(@Valid @RequestBody SafetyLogDTO dto) {
        return ApiResponse.success(safetyLogService.create(dto));
    }

    @PutMapping("/{id}")
    public ApiResponse<SafetyLog> update(@PathVariable Long id, @Valid @RequestBody SafetyLogDTO dto) {
        return ApiResponse.success(safetyLogService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        safetyLogService.delete(id);
        return ApiResponse.success(null);
    }

    // 审批权限由服务端按项目审核授权（ProjectAccessService.requireReview，数据库实时）判定，
    // 不再依赖令牌内嵌角色声明，项目总监/被授权审核人无需 admin 角色即可审批。
    @PostMapping("/{id}/approve")
    public ApiResponse<SafetyLog> approve(@PathVariable Long id, @Valid @RequestBody ApproveDTO dto) {
        dto.setId(id);
        return ApiResponse.success(safetyLogService.approve(dto));
    }
}
