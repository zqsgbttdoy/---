package com.supervision.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "biz_supervision_notice")
public class SupervisionNotice extends BaseEntity {

    @Column(nullable = false, length = 50)
    private String code;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "notice_date", nullable = false)
    private LocalDate noticeDate;

    @Column(name = "author_id")
    private Long authorId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column
    private LocalDate deadline;

    @Column(name = "reply_content", columnDefinition = "TEXT")
    private String replyContent;

    @Column(name = "reply_date")
    private LocalDate replyDate;

    @Column(length = 20)
    private String status;

    @Column(length = 20)
    private String priority;

    @Column(columnDefinition = "TEXT")
    private String photos;

    @Column(columnDefinition = "TEXT")
    private String attachments;
}
