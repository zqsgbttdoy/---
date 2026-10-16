package com.supervision.controller;

import com.supervision.common.ApiResponse;
import com.supervision.common.PageResult;
import com.supervision.dto.ReplyDTO;
import com.supervision.dto.SupervisionNoticeDTO;
import com.supervision.entity.SupervisionNotice;
import com.supervision.service.SupervisionNoticeService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/supervision-notices")
@AllArgsConstructor
public class SupervisionNoticeController {

    private final SupervisionNoticeService supervisionNoticeService;

    @GetMapping
    public ApiResponse<PageResult<SupervisionNotice>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(supervisionNoticeService.list(pageNum, pageSize, projectId, code, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<SupervisionNotice> getById(@PathVariable Long id) {
        return ApiResponse.success(supervisionNoticeService.getById(id));
    }

    @PostMapping
    public ApiResponse<SupervisionNotice> create(@Valid @RequestBody SupervisionNoticeDTO dto) {
        return ApiResponse.success(supervisionNoticeService.create(dto));
    }

    @PutMapping("/{id}")
    public ApiResponse<SupervisionNotice> update(@PathVariable Long id, @Valid @RequestBody SupervisionNoticeDTO dto) {
        return ApiResponse.success(supervisionNoticeService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        supervisionNoticeService.delete(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/reply")
    public ApiResponse<SupervisionNotice> reply(@PathVariable Long id, @Valid @RequestBody ReplyDTO dto) {
        dto.setId(id);
        return ApiResponse.success(supervisionNoticeService.reply(dto));
    }
}
