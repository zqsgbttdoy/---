package com.supervision.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "sys_department")
public class Department extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(length = 500)
    private String description;

    @Column(length = 50)
    private String leader;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(length = 20)
    private String status;
}
