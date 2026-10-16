package com.supervision.repository;

import com.supervision.entity.WorkStandardConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface WorkStandardConfigRepository extends JpaRepository<WorkStandardConfig, Long>, JpaSpecificationExecutor<WorkStandardConfig> {

    List<WorkStandardConfig> findByModule(String module);
}
