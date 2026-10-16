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
@Table(name = "sys_user")
public class User extends BaseEntity {

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 10)
    private String gender;

    @Column(name = "dept_id")
    private Long deptId;

    @Column(length = 50)
    private String position;

    @Column(name = "cert_no", length = 50)
    private String certNo;

    @Column(length = 20)
    private String phone;

    @Column(name = "id_card", length = 20)
    private String idCard;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "hire_date")
    private LocalDate hireDate;

    @Column(length = 20)
    private String status;

    @Column(length = 100)
    private String password;

    @Column(length = 500)
    private String avatar;
}
