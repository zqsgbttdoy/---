package com.supervision.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class MeetingMinutesDTO {

    @NotBlank(message = "编号不能为空")
    private String code;

    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    @NotNull(message = "会议日期不能为空")
    private LocalDate meetingDate;

    private Long authorId;

    @NotBlank(message = "标题不能为空")
    private String title;

    private String content;
    private String attendees;
    private String location;
    private String status;
    private String photos;
    private String attachments;
}
