package com.supervision.controller;

import com.supervision.common.ApiResponse;
import com.supervision.common.PageResult;
import com.supervision.dto.ProjectDTO;
import com.supervision.entity.Project;
import com.supervision.service.ProjectService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/projects")
@AllArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    public ApiResponse<PageResult<Project>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(projectService.list(pageNum, pageSize, name, code, type, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<Project> getById(@PathVariable Long id) {
        return ApiResponse.success(projectService.getById(id));
    }

    @PostMapping
    public ApiResponse<Project> create(@Valid @RequestBody ProjectDTO dto) {
        return ApiResponse.success(projectService.create(dto));
    }

    @PutMapping("/{id}")
    public ApiResponse<Project> update(@PathVariable Long id, @Valid @RequestBody ProjectDTO dto) {
        return ApiResponse.success(projectService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return ApiResponse.success(null);
    }

    @PutMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @RequestParam String status) {
        projectService.updateStatus(id, status);
        return ApiResponse.success(null);
    }
}
