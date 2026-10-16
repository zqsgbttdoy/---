package com.supervision.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "biz_daily_inspection")
public class DailyInspection extends FieldRecord {

    @Column(nullable = false, length = 50)
    private String code;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "inspection_date", nullable = false)
    private LocalDate inspectionDate;

    @Column(name = "inspector_id")
    private Long inspectorId;

    @Column(length = 200)
    private String location;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String issues;

    @Column(length = 20)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String photos;

    @Column(columnDefinition = "TEXT")
    private String attachments;
}
