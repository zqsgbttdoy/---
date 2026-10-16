package com.supervision.controller;

import com.supervision.common.ApiResponse;
import com.supervision.common.PageResult;
import com.supervision.dto.WorkStandardConfigDTO;
import com.supervision.entity.WorkStandardConfig;
import com.supervision.service.WorkStandardConfigService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/work-standard-configs")
@AllArgsConstructor
public class WorkStandardConfigController {

    private final WorkStandardConfigService workStandardConfigService;

    @GetMapping
    public ApiResponse<PageResult<WorkStandardConfig>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(workStandardConfigService.list(pageNum, pageSize, name, module, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<WorkStandardConfig> getById(@PathVariable Long id) {
        return ApiResponse.success(workStandardConfigService.getById(id));
    }

    @PostMapping
    public ApiResponse<WorkStandardConfig> create(@Valid @RequestBody WorkStandardConfigDTO dto) {
        return ApiResponse.success(workStandardConfigService.create(dto));
    }

    @PutMapping("/{id}")
    public ApiResponse<WorkStandardConfig> update(@PathVariable Long id, @Valid @RequestBody WorkStandardConfigDTO dto) {
        return ApiResponse.success(workStandardConfigService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        workStandardConfigService.delete(id);
        return ApiResponse.success(null);
    }
}
