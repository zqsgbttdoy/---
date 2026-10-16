package com.supervision.repository;

import com.supervision.entity.WorkContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface WorkContactRepository extends JpaRepository<WorkContact, Long>, JpaSpecificationExecutor<WorkContact> {

    Optional<WorkContact> findByCode(String code);

    List<WorkContact> findByStatus(String status);

    List<WorkContact> findByProjectId(Long projectId);
}
