package com.supervision.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SupervisionMonthlyReportDTO {

    @NotBlank(message = "编号不能为空")
    private String code;

    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    @NotBlank(message = "报告月份不能为空")
    private String reportMonth;

    private Long authorId;
    private String content;
    private String status;
    private String photos;
    private String attachments;
}
