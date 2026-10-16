package com.supervision.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProjectDocumentDTO {

    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    private Long subItemId;

    @NotBlank(message = "文档名称不能为空")
    private String name;

    private String filePath;
    private Long fileSize;
    private String description;
}
