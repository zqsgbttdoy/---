package com.supervision.controller;

import com.supervision.common.ApiResponse;
import com.supervision.common.PageResult;
import com.supervision.dto.MeetingMinutesDTO;
import com.supervision.entity.MeetingMinutes;
import com.supervision.service.MeetingMinutesService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/meeting-minutes")
@AllArgsConstructor
public class MeetingMinutesController {

    private final MeetingMinutesService meetingMinutesService;

    @GetMapping
    public ApiResponse<PageResult<MeetingMinutes>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(meetingMinutesService.list(pageNum, pageSize, projectId, code, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<MeetingMinutes> getById(@PathVariable Long id) {
        return ApiResponse.success(meetingMinutesService.getById(id));
    }

    @PostMapping
    public ApiResponse<MeetingMinutes> create(@Valid @RequestBody MeetingMinutesDTO dto) {
        return ApiResponse.success(meetingMinutesService.create(dto));
    }

    @PutMapping("/{id}")
    public ApiResponse<MeetingMinutes> update(@PathVariable Long id, @Valid @RequestBody MeetingMinutesDTO dto) {
        return ApiResponse.success(meetingMinutesService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        meetingMinutesService.delete(id);
        return ApiResponse.success(null);
    }
}
