package com.supervision.service;

import com.supervision.common.PageResult;
import com.supervision.common.PageUtils;
import com.supervision.dto.WorkStandardConfigDTO;
import com.supervision.entity.WorkStandardConfig;
import com.supervision.exception.BusinessException;
import com.supervision.repository.WorkStandardConfigRepository;
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

@Service
@AllArgsConstructor
public class WorkStandardConfigService {

    private final WorkStandardConfigRepository workStandardConfigRepository;
    private final ProjectAccessService access;

    private void requireAdministrator() {
        if (!access.isAdmin()) throw new BusinessException(403, "仅管理员可以维护工作标准配置");
    }

    public PageResult<WorkStandardConfig> list(int pageNum, int pageSize, String name, String module, String status) {
        pageNum = PageUtils.normalizePageNum(pageNum);
        pageSize = PageUtils.normalizePageSize(pageSize);
        Specification<WorkStandardConfig> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (name != null && !name.isEmpty()) {
                predicates.add(cb.like(root.get("name"), "%" + name + "%"));
            }
            if (module != null && !module.isEmpty()) {
                predicates.add(cb.equal(root.get("module"), module));
            }
            if (status != null && !status.isEmpty()) {
                predicates.add(cb.equal(root.get("status"), status));
            } else {
                predicates.add(cb.notEqual(root.get("status"), "inactive"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<WorkStandardConfig> page = workStandardConfigRepository.findAll(spec,
                PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime")));
        return new PageResult<>(page.getContent(), page.getTotalElements(), pageNum, pageSize);
    }

    public WorkStandardConfig getById(Long id) {
        return workStandardConfigRepository.findById(id)
                .orElseThrow(() -> new BusinessException("工作标准配置不存在"));
    }

    @Transactional
    public WorkStandardConfig create(WorkStandardConfigDTO dto) {
        requireAdministrator();
        WorkStandardConfig workStandardConfig = new WorkStandardConfig();
        workStandardConfig.setName(dto.getName());
        workStandardConfig.setModule(dto.getModule());
        workStandardConfig.setStandard(dto.getStandard());
        workStandardConfig.setDescription(dto.getDescription());
        workStandardConfig.setStatus("active");
        return workStandardConfigRepository.save(workStandardConfig);
    }

    @Transactional
    public WorkStandardConfig update(Long id, WorkStandardConfigDTO dto) {
        requireAdministrator();
        WorkStandardConfig existing = getById(id);
        existing.setName(dto.getName());
        existing.setModule(dto.getModule());
        existing.setStandard(dto.getStandard());
        existing.setDescription(dto.getDescription());
        return workStandardConfigRepository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        requireAdministrator();
        WorkStandardConfig existing = getById(id);
        existing.setStatus("inactive");
        workStandardConfigRepository.save(existing);
    }

    public List<WorkStandardConfig> findByModule(String module) {
        return workStandardConfigRepository.findByModule(module);
    }
}
