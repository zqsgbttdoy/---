package com.supervision.repository;

import com.supervision.entity.SupervisionMonthlyReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface SupervisionMonthlyReportRepository extends JpaRepository<SupervisionMonthlyReport, Long>, JpaSpecificationExecutor<SupervisionMonthlyReport> {

    Optional<SupervisionMonthlyReport> findByCode(String code);

    List<SupervisionMonthlyReport> findByProjectId(Long projectId);
}
