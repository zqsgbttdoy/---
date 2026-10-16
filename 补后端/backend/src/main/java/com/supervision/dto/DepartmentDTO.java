package com.supervision.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DepartmentDTO {

    @NotBlank(message = "部门名称不能为空")
    @Size(max = 100, message = "部门名称不能超过100个字符")
    private String name;

    private Long parentId;

    @Size(max = 500, message = "描述不能超过500个字符")
    private String description;

    private String leader;
    private Integer sortOrder;
    private String status;
}
