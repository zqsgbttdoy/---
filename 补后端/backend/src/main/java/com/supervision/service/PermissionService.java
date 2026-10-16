package com.supervision.service;

import com.supervision.common.PageResult;
import com.supervision.common.PageUtils;
import com.supervision.common.SystemAdmin;
import com.supervision.entity.Permission;
import com.supervision.entity.RolePermission;
import com.supervision.exception.BusinessException;
import com.supervision.repository.PermissionRepository;
import com.supervision.repository.RolePermissionRepository;
import com.supervision.repository.RoleRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@AllArgsConstructor
public class PermissionService {

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;

    public PageResult<Permission> list(int pageNum, int pageSize, String name, String module) {
        pageNum = PageUtils.normalizePageNum(pageNum);
        pageSize = PageUtils.normalizePageSize(pageSize);
        Specification<Permission> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (name != null && !name.isEmpty()) {
                predicates.add(cb.like(root.get("name"), "%" + name + "%"));
            }
            if (module != null && !module.isEmpty()) {
                predicates.add(cb.equal(root.get("module"), module));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<Permission> page = permissionRepository.findAll(spec,
                PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.ASC, "sortOrder")));
        return new PageResult<>(page.getContent(), page.getTotalElements(), pageNum, pageSize);
    }

    public Permission getById(Long id) {
        return permissionRepository.findById(id)
                .orElseThrow(() -> new BusinessException("权限不存在"));
    }

    @Transactional
    public Permission create(Permission permission) {
        permissionRepository.findByCode(permission.getCode())
                .ifPresent(e -> { throw new BusinessException("权限编码已存在"); });
        Permission saved = permissionRepository.save(permission);
        // 新增权限项自动授予系统管理员角色，保持 admin“全部开启”（直接写库新增的由启动自检 AdminPermissionInitializer 补绑）
        roleRepository.findAllByCode(SystemAdmin.ROLE_CODE).forEach(admin -> {
            RolePermission rp = new RolePermission();
            rp.setRoleId(admin.getId());
            rp.setPermissionId(saved.getId());
            rolePermissionRepository.save(rp);
        });
        return saved;
    }

    public Permission update(Long id, Permission permission) {
        Permission existing = getById(id);
        existing.setName(permission.getName());
        existing.setCode(permission.getCode());
        existing.setModule(permission.getModule());
        existing.setDescription(permission.getDescription());
        existing.setParentId(permission.getParentId());
        existing.setSortOrder(permission.getSortOrder());
        return permissionRepository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        Permission existing = getById(id);
        rolePermissionRepository.deleteByPermissionId(id);
        permissionRepository.delete(existing);
    }

    public List<Permission> findByModule(String module) {
        return permissionRepository.findByModule(module);
    }

    public List<Map<String, Object>> getTree() {
        List<Permission> all = permissionRepository.findAll();
        return buildTree(all, null);
    }

    private List<Map<String, Object>> buildTree(List<Permission> all, Long parentId) {
        List<Map<String, Object>> tree = new ArrayList<>();
        for (Permission perm : all) {
            if ((parentId == null && perm.getParentId() == null) ||
                    (parentId != null && parentId.equals(perm.getParentId()))) {
                Map<String, Object> node = new HashMap<>();
                node.put("id", perm.getId());
                node.put("name", perm.getName());
                node.put("code", perm.getCode());
                node.put("module", perm.getModule());
                node.put("parentId", perm.getParentId());
                node.put("sortOrder", perm.getSortOrder());
                node.put("description", perm.getDescription());
                node.put("children", buildTree(all, perm.getId()));
                tree.add(node);
            }
        }
        return tree;
    }
}
