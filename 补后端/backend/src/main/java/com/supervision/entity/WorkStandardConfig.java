package com.supervision.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "sys_work_standard_config")
public class WorkStandardConfig extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 50)
    private String module;

    @Column(length = 50)
    private String standard;

    @Column(length = 500)
    private String description;

    @Column(length = 20)
    private String status;
}
