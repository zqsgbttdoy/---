package com.supervision.service;

import com.supervision.common.PageResult;
import com.supervision.common.PageUtils;
import com.supervision.entity.OperationLog;
import com.supervision.repository.OperationLogRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class OperationLogService {

    private final OperationLogRepository operationLogRepository;

    public PageResult<OperationLog> list(int pageNum, int pageSize, String userName, String action, String module) {
        pageNum = PageUtils.normalizePageNum(pageNum);
        pageSize = PageUtils.normalizePageSize(pageSize);
        Specification<OperationLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (userName != null && !userName.isEmpty()) {
                predicates.add(cb.like(root.get("userName"), "%" + userName + "%"));
            }
            if (action != null && !action.isEmpty()) {
                predicates.add(cb.like(root.get("action"), "%" + action + "%"));
            }
            if (module != null && !module.isEmpty()) {
                predicates.add(cb.equal(root.get("module"), module));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<OperationLog> page = operationLogRepository.findAll(spec,
                PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime")));
        return new PageResult<>(page.getContent(), page.getTotalElements(), pageNum, pageSize);
    }

    public OperationLog save(OperationLog operationLog) {
        return operationLogRepository.save(operationLog);
    }

    public List<OperationLog> findByUserId(Long userId) {
        return operationLogRepository.findByUserId(userId);
    }

    public List<OperationLog> findByModule(String module) {
        return operationLogRepository.findByModule(module);
    }
}
