package com.supervision.controller;

import com.supervision.common.ApiResponse;
import com.supervision.common.PageResult;
import com.supervision.dto.SidelineInspectionDTO;
import com.supervision.entity.SidelineInspection;
import com.supervision.service.SidelineInspectionService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/sideline-inspections")
public class SidelineInspectionController extends FieldRecordController {

    public SidelineInspectionController(com.supervision.service.FieldRecordService records) {
        super(records, com.supervision.common.FieldRecordKind.SIDELINE);
    }

    // 现场记录的权限、版本和状态变更均由共用服务处理。
}
