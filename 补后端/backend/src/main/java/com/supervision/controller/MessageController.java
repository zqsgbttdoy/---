package com.supervision.controller;

import com.supervision.common.ApiResponse;
import com.supervision.common.PageResult;
import com.supervision.dto.MessageDTO;
import com.supervision.entity.Message;
import com.supervision.service.MessageService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/messages")
@AllArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @GetMapping
    public ApiResponse<PageResult<Message>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long receiverId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String isRead) {
        return ApiResponse.success(messageService.list(pageNum, pageSize, receiverId, type, isRead));
    }

    @GetMapping("/{id}")
    public ApiResponse<Message> getById(@PathVariable Long id) {
        return ApiResponse.success(messageService.getById(id));
    }

    @PostMapping
    public ApiResponse<Message> create(@Valid @RequestBody MessageDTO dto) {
        return ApiResponse.success(messageService.create(dto));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        messageService.delete(id);
        return ApiResponse.success(null);
    }

    @PutMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable Long id) {
        messageService.markRead(id);
        return ApiResponse.success(null);
    }

    @GetMapping("/unread-count")
    public ApiResponse<Long> getUnreadCount(@RequestParam Long receiverId) {
        return ApiResponse.success(messageService.getUnreadCount(receiverId));
    }
}
