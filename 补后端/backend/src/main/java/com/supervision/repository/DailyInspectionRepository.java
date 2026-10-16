package com.supervision.repository;

import com.supervision.entity.DailyInspection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface DailyInspectionRepository extends JpaRepository<DailyInspection, Long>, JpaSpecificationExecutor<DailyInspection> {

    Optional<DailyInspection> findByCode(String code);

    List<DailyInspection> findByProjectId(Long projectId);

    List<DailyInspection> findByProjectIdAndStatus(Long projectId, String status);
}
