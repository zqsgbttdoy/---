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
@Table(name = "biz_meeting_minutes")
public class MeetingMinutes extends BaseEntity {

    @Column(nullable = false, length = 50)
    private String code;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "meeting_date", nullable = false)
    private LocalDate meetingDate;

    @Column(name = "author_id")
    private Long authorId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(length = 500)
    private String attendees;

    @Column(length = 200)
    private String location;

    @Column(length = 20)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String photos;

    @Column(columnDefinition = "TEXT")
    private String attachments;
}
