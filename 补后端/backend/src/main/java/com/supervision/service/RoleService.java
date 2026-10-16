package com.supervision.service;

import com.supervision.common.AuthContext;
import com.supervision.common.PageResult;
import com.supervision.common.PageUtils;
import com.supervision.common.SystemAdmin;
import com.supervision.entity.Permission;
import com.supervision.entity.Role;
import com.supervision.entity.RolePermission;
import com.supervision.entity.UserRole;
import com.supervision.exception.BusinessException;
import com.supervision.repository.PermissionRepository;
import com.supervision.repository.RolePermissionRepository;
import com.supervision.repository.RoleRepository;
import com.supervision.repository.UserRepository;
import com.supervision.repository.UserRoleRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@AllArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRoleRepository userRoleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;

    public PageResult<Role> list(int pageNum, int pageSize, String name, String status) {
        pageNum = PageUtils.normalizePageNum(pageNum);
        pageSize = PageUtils.normalizePageSize(pageSize);
        Specification<Role> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (name != null && !name.isEmpty()) {
                predicates.add(cb.like(root.get("name"), "%" + name + "%"));
            }
            if (status != null && !status.isEmpty()) {
                predicates.add(cb.equal(root.get("status"), status));
            } else {
                predicates.add(cb.notEqual(root.get("status"), "inactive"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<Role> page = roleRepository.findAll(spec,
                PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime")));
        return new PageResult<>(page.getContent(), page.getTotalElements(), pageNum, pageSize);
    }

    public Role getById(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new BusinessException("角色不存在"));
    }

    public Role create(Role role) {
        roleRepository.findByCode(role.getCode())
                .filter(e -> !"inactive".equals(e.getStatus()))
                .ifPresent(e -> { throw new BusinessException("角色编码已存在"); });
        role.setStatus("active");
        return roleRepository.save(role);
    }

    public Role update(Long id, Role role) {
        Role existing = getById(id);
        existing.setName(role.getName());
        existing.setDescription(role.getDescription());
        return roleRepository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        Role existing = getById(id);
        if (SystemAdmin.isAdminRole(existing)) {
            throw new BusinessException("系统管理员角色为内置角色，不可删除");
        }
        existing.setStatus("inactive");
        roleRepository.save(existing);
    }

    @Transactional
    public void assignPermissions(Long id, List<Long> permissionIds) {
        Role role = getById(id);
        Set<Long> ids = new LinkedHashSet<>(permissionIds == null ? List.of() : permissionIds);
        ids.remove(null);
        // 系统管理员角色固定拥有全部权限：忽略前端勾选，始终绑定当前全部权限项
        if (SystemAdmin.isAdminRole(role)) {
            ids = new LinkedHashSet<>(permissionRepository.findAll().stream().map(Permission::getId).toList());
        }
        rolePermissionRepository.deleteByRoleId(id);
        rolePermissionRepository.flush();
        // 构建列表后一次 saveAll，配合 hibernate.jdbc.batch_size 实现批量插入
        List<RolePermission> rolePermissions = new ArrayList<>();
        for (Long permissionId : ids) {
            RolePermission rp = new RolePermission();
            rp.setRoleId(id);
            rp.setPermissionId(permissionId);
            rolePermissions.add(rp);
        }
        rolePermissionRepository.saveAll(rolePermissions);
        log.info("分配角色权限：操作者userId={}, roleId={}, 权限数量={}",
                AuthContext.getUserId(), id, ids.size());
    }

    @Transactional
    public void assignUsers(Long id, List<Long> userIds) {
        Role role = getById(id);
        Set<Long> ids = new LinkedHashSet<>(userIds == null ? List.of() : userIds);
        ids.remove(null);
        // 内置 admin 账号始终保留系统管理员角色，防止误操作把唯一管理员移出
        if (SystemAdmin.isAdminRole(role)) {
            userRepository.findByName(SystemAdmin.USER_NAME).ifPresent(admin -> ids.add(admin.getId()));
        }
        userRoleRepository.deleteByRoleId(id);
        userRoleRepository.flush();
        // 构建列表后一次 saveAll，配合 hibernate.jdbc.batch_size 实现批量插入
        List<UserRole> userRoles = new ArrayList<>();
        for (Long userId : ids) {
            UserRole ur = new UserRole();
            ur.setUserId(userId);
            ur.setRoleId(id);
            userRoles.add(ur);
        }
        userRoleRepository.saveAll(userRoles);
        log.info("分配角色用户：操作者userId={}, roleId={}, 用户数量={}",
                AuthContext.getUserId(), id, ids.size());
    }

    public List<RolePermission> getRolePermissions(Long roleId) {
        return rolePermissionRepository.findByRoleId(roleId);
    }

    public List<Long> getPermissionIds(Long roleId) {
        getById(roleId);
        return rolePermissionRepository.findByRoleId(roleId).stream()
                .map(RolePermission::getPermissionId)
                .distinct()
                .toList();
    }
}
