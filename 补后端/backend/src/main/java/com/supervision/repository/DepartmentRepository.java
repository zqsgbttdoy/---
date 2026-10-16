package com.supervision.repository;

import com.supervision.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface DepartmentRepository extends JpaRepository<Department, Long>, JpaSpecificationExecutor<Department> {

    List<Department> findByParentId(Long parentId);

    // 是否存在未删除的子部门，用于部门删除前校验
    boolean existsByParentIdAndStatusNot(Long parentId, String status);

    List<Department> findByStatus(String status);
}
