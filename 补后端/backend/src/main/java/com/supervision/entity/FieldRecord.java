package com.supervision.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** PC 与小程序共用的同步元数据；业务版本和并发版本分别维护。 */
@Getter
@Setter
@MappedSuperclass
public abstract class FieldRecord extends BaseEntity {
    @Version
    @Column(name = "lock_version", nullable = false, columnDefinition = "bigint default 0")
    private Long lockVersion = 0L;

    @JsonIgnore
    @Column(name = "request_key", length = 64, unique = true)
    private String requestKey;

    @JsonIgnore
    @Column(name = "request_hash", length = 64)
    private String requestHash;

    @Column(name = "source_record_id")
    private Long sourceRecordId;

    public abstract Long getProjectId();
    public abstract String getStatus();
    public abstract void setStatus(String status);
    public abstract String getPhotos();
    public abstract String getAttachments();
}
