package com.supervision.repository;

import com.supervision.entity.OperationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface OperationLogRepository extends JpaRepository<OperationLog, Long>, JpaSpecificationExecutor<OperationLog> {

    List<OperationLog> findByUserId(Long userId);

    List<OperationLog> findByModule(String module);

    List<OperationLog> findByUserIdAndModule(Long userId, String module);
}
