package com.supervision.controller;

import com.supervision.common.ApiResponse;
import com.supervision.common.PageResult;
import com.supervision.dto.DailyInspectionDTO;
import com.supervision.entity.DailyInspection;
import com.supervision.service.DailyInspectionService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/daily-inspections")
public class DailyInspectionController extends FieldRecordController {

    public DailyInspectionController(com.supervision.service.FieldRecordService records) {
        super(records, com.supervision.common.FieldRecordKind.DAILY);
    }

    // 现场记录的权限、版本和状态变更均由共用服务处理。
}
