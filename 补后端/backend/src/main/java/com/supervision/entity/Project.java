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
@Table(name = "biz_project")
public class Project extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(length = 500)
    private String address;

    /** 建设单位（业主单位），前端列表/表单必填项 */
    @Column(name = "build_unit", length = 200)
    private String buildUnit;

    @Column(length = 50)
    private String type;

    @Column(length = 20)
    private String status;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "manager_id")
    private Long managerId;
}
