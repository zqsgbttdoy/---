package com.supervision.repository;

import com.supervision.entity.SupervisionDiary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface SupervisionDiaryRepository extends JpaRepository<SupervisionDiary, Long>, JpaSpecificationExecutor<SupervisionDiary> {

    Optional<SupervisionDiary> findByCode(String code);

    List<SupervisionDiary> findByProjectId(Long projectId);

    List<SupervisionDiary> findByProjectIdAndVersion(Long projectId, Integer version);
}
