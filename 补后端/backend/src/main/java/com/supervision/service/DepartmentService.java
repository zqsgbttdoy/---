package com.supervision.service;

import com.supervision.common.PageResult;
import com.supervision.common.PageUtils;
import com.supervision.dto.DepartmentDTO;
import com.supervision.entity.Department;
import com.supervision.exception.BusinessException;
import com.supervision.repository.DepartmentRepository;
import com.supervision.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@AllArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;

    public PageResult<Department> list(int pageNum, int pageSize, String name, String status) {
        pageNum = PageUtils.normalizePageNum(pageNum);
        pageSize = PageUtils.normalizePageSize(pageSize);
        Specification<Department> spec = (root, query, cb) -> {
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
        Page<Department> page = departmentRepository.findAll(spec,
                PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.ASC, "sortOrder")));
        return new PageResult<>(page.getContent(), page.getTotalElements(), pageNum, pageSize);
    }

    public Department getById(Long id) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException("部门不存在"));
        // 软删除（status=inactive）的部门对外视为不存在，防止已删部门仍可被查询/恢复
        if ("inactive".equals(department.getStatus())) {
            throw new BusinessException("部门不存在");
        }
        return department;
    }

    public Department create(DepartmentDTO dto) {
        Department department = new Department();
        department.setName(dto.getName());
        department.setParentId(dto.getParentId());
        department.setDescription(dto.getDescription());
        department.setLeader(dto.getLeader());
        department.setSortOrder(dto.getSortOrder());
        department.setStatus("active");
        return departmentRepository.save(department);
    }

    public Department update(Long id, DepartmentDTO dto) {
        Department existing = getById(id);
        existing.setName(dto.getName());
        existing.setParentId(dto.getParentId());
        existing.setDescription(dto.getDescription());
        existing.setLeader(dto.getLeader());
        existing.setSortOrder(dto.getSortOrder());
        return departmentRepository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        Department existing = getById(id);
        // 删除前校验：与前端提示文案保持一致，含子部门或在编人员的部门不允许删除，避免孤儿 deptId 脏数据
        if (departmentRepository.existsByParentIdAndStatusNot(id, "inactive")) {
            throw new BusinessException("该部门下存在子部门，请先删除子部门");
        }
        long memberCount = userRepository.countByDeptIdAndStatusNot(id, "inactive");
        if (memberCount > 0) {
            throw new BusinessException("该部门下还有 " + memberCount + " 名在职/休假人员，请先转移或离职后再删除");
        }
        existing.setStatus("inactive");
        departmentRepository.save(existing);
    }

    public List<Department> findByParentId(Long parentId) {
        return departmentRepository.findByParentId(parentId);
    }

    public List<Map<String, Object>> tree() {
        List<Department> all = departmentRepository.findByStatus("active");
        return buildTree(all, null);
    }

    private List<Map<String, Object>> buildTree(List<Department> all, Long parentId) {
        List<Map<String, Object>> tree = new ArrayList<>();
        for (Department dept : all) {
            if ((parentId == null && dept.getParentId() == null) ||
                    (parentId != null && parentId.equals(dept.getParentId()))) {
                Map<String, Object> node = new java.util.HashMap<>();
                node.put("id", dept.getId());
                node.put("name", dept.getName());
                node.put("parentId", dept.getParentId());
                node.put("leader", dept.getLeader());
                node.put("sortOrder", dept.getSortOrder());
                node.put("description", dept.getDescription());
                node.put("status", dept.getStatus());
                node.put("children", buildTree(all, dept.getId()));
                tree.add(node);
            }
        }
        return tree;
    }
}
