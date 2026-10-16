package com.supervision.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 显式成员配置优先于旧的人员所属项目字段。 */
@Getter
@Setter
@Entity
@Table(name = "biz_project_member", uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "user_id"}))
public class ProjectMember extends BaseEntity {
    @Column(name = "project_id", nullable = false)
    private Long projectId;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(nullable = false)
    private boolean enabled = true;
    @Column(name = "can_review", nullable = false)
    private boolean canReview;
}
