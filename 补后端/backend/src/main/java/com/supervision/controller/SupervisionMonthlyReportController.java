package com.supervision.controller;

import com.supervision.common.ApiResponse;
import com.supervision.common.PageResult;
import com.supervision.dto.SupervisionMonthlyReportDTO;
import com.supervision.entity.SupervisionMonthlyReport;
import com.supervision.service.SupervisionMonthlyReportService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/supervision-monthly-reports")
@AllArgsConstructor
public class SupervisionMonthlyReportController {

    private final SupervisionMonthlyReportService supervisionMonthlyReportService;

    @GetMapping
    public ApiResponse<PageResult<SupervisionMonthlyReport>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(supervisionMonthlyReportService.list(pageNum, pageSize, projectId, code, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<SupervisionMonthlyReport> getById(@PathVariable Long id) {
        return ApiResponse.success(supervisionMonthlyReportService.getById(id));
    }

    @PostMapping
    public ApiResponse<SupervisionMonthlyReport> create(@Valid @RequestBody SupervisionMonthlyReportDTO dto) {
        return ApiResponse.success(supervisionMonthlyReportService.create(dto));
    }

    @PutMapping("/{id}")
    public ApiResponse<SupervisionMonthlyReport> update(@PathVariable Long id, @Valid @RequestBody SupervisionMonthlyReportDTO dto) {
        return ApiResponse.success(supervisionMonthlyReportService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        supervisionMonthlyReportService.delete(id);
        return ApiResponse.success(null);
    }
}
