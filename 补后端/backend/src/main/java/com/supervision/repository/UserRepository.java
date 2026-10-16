package com.supervision.repository;

import com.supervision.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByPhone(String phone);

    Optional<User> findByName(String name);

    List<User> findByDeptId(Long deptId);

    // 统计部门下非离职状态的人员数，用于部门删除前校验
    long countByDeptIdAndStatusNot(Long deptId, String status);

    List<User> findByStatus(String status);
}
