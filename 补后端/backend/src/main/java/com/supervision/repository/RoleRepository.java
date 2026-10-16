package com.supervision.repository;

import com.supervision.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long>, JpaSpecificationExecutor<Role> {

    Optional<Role> findByCode(String code);

    /** 不依赖 code 唯一索引（开发库由 Hibernate 建表时可能没有），重复编码时不会抛异常 */
    List<Role> findAllByCode(String code);

    List<Role> findByStatus(String status);
}
