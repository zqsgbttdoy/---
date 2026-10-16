package com.supervision.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "biz_supervision_monthly_report")
public class SupervisionMonthlyReport extends BaseEntity {

    @Column(nullable = false, length = 50)
    private String code;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "report_month", nullable = false, length = 10)
    private String reportMonth;

    @Column(name = "author_id")
    private Long authorId;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(length = 20)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String photos;

    @Column(columnDefinition = "TEXT")
    private String attachments;
}
