package com.supervision.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SubItemDatabaseDTO {

    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    @NotBlank(message = "名称不能为空")
    private String name;

    private Long parentId;
    private Integer sortOrder;
    private String description;
    private String status;
}
