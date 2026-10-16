package com.supervision.controller;

import com.supervision.annotation.RequireRole;
import com.supervision.common.ApiResponse;
import com.supervision.common.PageResult;
import com.supervision.dto.ApproveDTO;
import com.supervision.dto.SupervisionDiaryDTO;
import com.supervision.entity.SupervisionDiary;
import com.supervision.service.SupervisionDiaryService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/supervision-diaries")
public class SupervisionDiaryController extends FieldRecordController {

    public SupervisionDiaryController(com.supervision.service.FieldRecordService records) {
        super(records, com.supervision.common.FieldRecordKind.DIARY);
    }

    // CRUD、提交、审核及历史接口统一继承，审核权限由项目授权决定。
}
