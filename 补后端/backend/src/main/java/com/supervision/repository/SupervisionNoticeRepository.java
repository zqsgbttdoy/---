package com.supervision.repository;

import com.supervision.entity.SupervisionNotice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface SupervisionNoticeRepository extends JpaRepository<SupervisionNotice, Long>, JpaSpecificationExecutor<SupervisionNotice> {

    Optional<SupervisionNotice> findByCode(String code);

    List<SupervisionNotice> findByStatus(String status);

    List<SupervisionNotice> findByProjectId(Long projectId);
}
