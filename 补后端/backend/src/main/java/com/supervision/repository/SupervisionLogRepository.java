package com.supervision.repository;

import com.supervision.entity.SupervisionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface SupervisionLogRepository extends JpaRepository<SupervisionLog, Long>, JpaSpecificationExecutor<SupervisionLog> {

    Optional<SupervisionLog> findByCode(String code);

    List<SupervisionLog> findByProjectId(Long projectId);

    List<SupervisionLog> findByProjectIdAndVersion(Long projectId, Integer version);
}
