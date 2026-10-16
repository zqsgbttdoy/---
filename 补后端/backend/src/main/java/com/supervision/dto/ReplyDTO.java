package com.supervision.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReplyDTO {

    // id 由路径参数注入（controller 中 dto.setId(id)），无需请求体携带
    private Long id;

    @NotBlank(message = "回复内容不能为空")
    private String replyContent;
}
