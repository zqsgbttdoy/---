package com.supervision.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WorkStandardConfigDTO {

    @NotBlank(message = "名称不能为空")
    @Size(max = 100, message = "名称不能超过100个字符")
    private String name;

    @Size(max = 50, message = "模块不能超过50个字符")
    private String module;

    @Size(max = 50, message = "标准不能超过50个字符")
    private String standard;

    @Size(max = 500, message = "描述不能超过500个字符")
    private String description;

    private String status;
}
