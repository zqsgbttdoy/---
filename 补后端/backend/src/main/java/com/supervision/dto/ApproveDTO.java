package com.supervision.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ApproveDTO {

    // id 由路径参数注入（controller 中 dto.setId(id)），无需请求体携带
    private Long id;

    @NotNull(message = "审核状态不能为空")
    @Pattern(regexp = "approved|rejected", message = "审核状态只能是approved或rejected")
    private String status;

    private String rejectReason;
}
