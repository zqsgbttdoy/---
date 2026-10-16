package com.supervision.controller;

import com.supervision.common.ApiResponse;
import com.supervision.common.PageResult;
import com.supervision.dto.ReplyDTO;
import com.supervision.dto.WorkContactDTO;
import com.supervision.entity.WorkContact;
import com.supervision.service.WorkContactService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/work-contacts")
@AllArgsConstructor
public class WorkContactController {

    private final WorkContactService workContactService;

    @GetMapping
    public ApiResponse<PageResult<WorkContact>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(workContactService.list(pageNum, pageSize, projectId, code, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<WorkContact> getById(@PathVariable Long id) {
        return ApiResponse.success(workContactService.getById(id));
    }

    @PostMapping
    public ApiResponse<WorkContact> create(@Valid @RequestBody WorkContactDTO dto) {
        return ApiResponse.success(workContactService.create(dto));
    }

    @PutMapping("/{id}")
    public ApiResponse<WorkContact> update(@PathVariable Long id, @Valid @RequestBody WorkContactDTO dto) {
        return ApiResponse.success(workContactService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        workContactService.delete(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/reply")
    public ApiResponse<WorkContact> reply(@PathVariable Long id, @Valid @RequestBody ReplyDTO dto) {
        dto.setId(id);
        return ApiResponse.success(workContactService.reply(dto));
    }
}
