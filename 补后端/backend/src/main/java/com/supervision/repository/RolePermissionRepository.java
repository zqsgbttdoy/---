package com.supervision.repository;

import com.supervision.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {

    List<RolePermission> findByRoleId(Long roleId);

    List<RolePermission> findByPermissionId(Long permissionId);

    void deleteByRoleId(Long roleId);

    void deleteByPermissionId(Long permissionId);
}
