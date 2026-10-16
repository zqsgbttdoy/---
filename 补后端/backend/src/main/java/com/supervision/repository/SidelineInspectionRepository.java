package com.supervision.repository;

import com.supervision.entity.SidelineInspection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface SidelineInspectionRepository extends JpaRepository<SidelineInspection, Long>, JpaSpecificationExecutor<SidelineInspection> {

    Optional<SidelineInspection> findByCode(String code);

    List<SidelineInspection> findByProjectId(Long projectId);
}
