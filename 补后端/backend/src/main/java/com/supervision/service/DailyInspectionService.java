package com.supervision.service;

import com.supervision.common.PageResult;
import com.supervision.common.PageUtils;
import com.supervision.dto.DailyInspectionDTO;
import com.supervision.entity.DailyInspection;
import com.supervision.exception.BusinessException;
import com.supervision.repository.DailyInspectionRepository;
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
public class DailyInspectionService {

    private final DailyInspectionRepository dailyInspectionRepository;

    public PageResult<DailyInspection> list(int pageNum, int pageSize, Long projectId, String code, String status) {
        pageNum = PageUtils.normalizePageNum(pageNum);
        pageSize = PageUtils.normalizePageSize(pageSize);
        Specification<DailyInspection> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (projectId != null) {
                predicates.add(cb.equal(root.get("projectId"), projectId));
            }
            if (code != null && !code.isEmpty()) {
                predicates.add(cb.like(root.get("code"), "%" + code + "%"));
            }
            if (status != null && !status.isEmpty()) {
                predicates.add(cb.equal(root.get("status"), status));
            } else {
                predicates.add(cb.notEqual(root.get("status"), "inactive"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<DailyInspection> page = dailyInspectionRepository.findAll(spec,
                PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime")));
        return new PageResult<>(page.getContent(), page.getTotalElements(), pageNum, pageSize);
    }

    public DailyInspection getById(Long id) {
        return dailyInspectionRepository.findById(id)
                .orElseThrow(() -> new BusinessException("日常巡视记录不存在"));
    }

    public DailyInspection create(DailyInspectionDTO dto) {
        dailyInspectionRepository.findByCode(dto.getCode())
                .filter(e -> !"inactive".equals(e.getStatus()))
                .ifPresent(e -> { throw new BusinessException("编号已存在"); });
        DailyInspection dailyInspection = new DailyInspection();
        dailyInspection.setCode(dto.getCode());
        dailyInspection.setProjectId(dto.getProjectId());
        dailyInspection.setInspectionDate(dto.getInspectionDate());
        dailyInspection.setInspectorId(dto.getInspectorId());
        dailyInspection.setLocation(dto.getLocation());
        dailyInspection.setContent(dto.getContent());
        dailyInspection.setIssues(dto.getIssues());
        dailyInspection.setPhotos(dto.getPhotos());
        dailyInspection.setAttachments(dto.getAttachments());
        String initialStatus = dto.getStatus() != null ? dto.getStatus() : "draft";
        dailyInspection.setStatus(initialStatus);
        return dailyInspectionRepository.save(dailyInspection);
    }

    public DailyInspection update(Long id, DailyInspectionDTO dto) {
        DailyInspection existing = getById(id);
        if (dto.getCode() != null && !dto.getCode().equals(existing.getCode())) {
            dailyInspectionRepository.findByCode(dto.getCode())
                    .filter(e -> !"inactive".equals(e.getStatus()))
                    .ifPresent(e -> { throw new BusinessException("编号已存在"); });
        }
        existing.setCode(dto.getCode());
        existing.setProjectId(dto.getProjectId());
        existing.setInspectionDate(dto.getInspectionDate());
        existing.setInspectorId(dto.getInspectorId());
        existing.setLocation(dto.getLocation());
        existing.setContent(dto.getContent());
        existing.setIssues(dto.getIssues());
        existing.setPhotos(dto.getPhotos());
        existing.setAttachments(dto.getAttachments());
        return dailyInspectionRepository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        DailyInspection existing = getById(id);
        existing.setStatus("inactive");
        dailyInspectionRepository.save(existing);
    }

    public List<DailyInspection> findByProjectId(Long projectId) {
        return dailyInspectionRepository.findByProjectId(projectId);
    }

    public Map<String, Object> statistics(Long projectId) {
        List<DailyInspection> records = dailyInspectionRepository.findByProjectId(projectId);
        List<DailyInspection> activeRecords = records.stream()
                .filter(r -> !"inactive".equals(r.getStatus()))
                .toList();
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", (long) activeRecords.size());
        long draftCount = activeRecords.stream().filter(r -> "draft".equals(r.getStatus())).count();
        long recordedCount = activeRecords.stream().filter(r -> "recorded".equals(r.getStatus())).count();
        long fixedCount = activeRecords.stream().filter(r -> "fixed".equals(r.getStatus())).count();
        stats.put("draft", draftCount);
        stats.put("recorded", recordedCount);
        stats.put("fixed", fixedCount);
        return stats;
    }
}
