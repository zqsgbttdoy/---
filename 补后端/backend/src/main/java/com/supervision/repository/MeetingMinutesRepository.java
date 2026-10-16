package com.supervision.repository;

import com.supervision.entity.MeetingMinutes;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MeetingMinutesRepository extends JpaRepository<MeetingMinutes, Long>, JpaSpecificationExecutor<MeetingMinutes> {

    Optional<MeetingMinutes> findByCode(String code);

    Page<MeetingMinutes> findByProjectId(Long projectId, Pageable pageable);

    Page<MeetingMinutes> findByAuthorId(Long authorId, Pageable pageable);

    Page<MeetingMinutes> findByStatus(String status, Pageable pageable);

    List<MeetingMinutes> findByProjectId(Long projectId);

    List<MeetingMinutes> findByAuthorId(Long authorId);

    List<MeetingMinutes> findByStatus(String status);
}
