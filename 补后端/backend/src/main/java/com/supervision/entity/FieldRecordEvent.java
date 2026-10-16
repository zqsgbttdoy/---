package com.supervision.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** 每次保存和状态变更的不可编辑快照。 */
@Getter
@Setter
@Entity
@Table(name = "biz_field_record_event", indexes = @Index(columnList = "record_type,record_id"))
public class FieldRecordEvent extends BaseEntity {
    @Column(name = "record_type", nullable = false, length = 40)
    private String recordType;
    @Column(name = "record_id", nullable = false)
    private Long recordId;
    @Column(name = "actor_id", nullable = false)
    private Long actorId;
    @Column(name = "actor_name", length = 100)
    private String actorName;
    @Column(nullable = false, length = 40)
    private String action;
    @Column(length = 500)
    private String reason;
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String snapshot;
}
