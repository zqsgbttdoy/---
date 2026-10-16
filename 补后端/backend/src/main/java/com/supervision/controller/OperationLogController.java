package com.supervision.controller;

import com.supervision.annotation.RequireRole;
import com.supervision.common.ApiResponse;
import com.supervision.common.PageResult;
import com.supervision.entity.OperationLog;
import com.supervision.service.OperationLogService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

// 操作日志接口仅被管理页面（operation-log.html）使用，整类要求 admin 角色
@RequireRole("admin")
@RestController
@RequestMapping("/operation-logs")
@AllArgsConstructor
public class OperationLogController {

    private final OperationLogService operationLogService;

    @GetMapping
    public ApiResponse<PageResult<OperationLog>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String module) {
        return ApiResponse.success(operationLogService.list(pageNum, pageSize, userName, action, module));
    }
}
