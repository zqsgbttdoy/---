package com.supervision.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "biz_uploaded_file")
public class UploadedFile extends BaseEntity {
    @Column(nullable = false, unique = true, length = 500)
    private String url;
    @Column(name = "owner_id", nullable = false)
    private Long ownerId;
    @Column(name = "project_id")
    private Long projectId;
}
