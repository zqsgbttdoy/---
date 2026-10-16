package com.supervision.service;

import com.supervision.common.PageResult;
import com.supervision.common.PageUtils;
import com.supervision.dto.SidelineInspectionDTO;
import com.supervision.entity.SidelineInspection;
import com.supervision.exception.BusinessException;
import com.supervision.repository.SidelineInspectionRepository;
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
public class SidelineInspectionService {

    private final SidelineInspectionRepository sidelineInspectionRepository;

    public PageResult<SidelineInspection> list(int pageNum, int pageSize, Long projectId, String code, String status) {
        pageNum = PageUtils.normalizePageNum(pageNum);
        pageSize = PageUtils.normalizePageSize(pageSize);
        Specification<SidelineInspection> spec = (root, query, cb) -> {
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
        Page<SidelineInspection> page = sidelineInspectionRepository.findAll(spec,
                PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime")));
        return new PageResult<>(page.getContent(), page.getTotalElements(), pageNum, pageSize);
    }

    public SidelineInspection getById(Long id) {
        return sidelineInspectionRepository.findById(id)
                .orElseThrow(() -> new BusinessException("旁站检查记录不存在"));
    }

    public SidelineInspection create(SidelineInspectionDTO dto) {
        sidelineInspectionRepository.findByCode(dto.getCode())
                .filter(e -> !"inactive".equals(e.getStatus()))
                .ifPresent(e -> { throw new BusinessException("编号已存在"); });
        SidelineInspection sidelineInspection = new SidelineInspection();
        sidelineInspection.setCode(dto.getCode());
        sidelineInspection.setProjectId(dto.getProjectId());
        sidelineInspection.setInspectionDate(dto.getInspectionDate());
        sidelineInspection.setInspectorId(dto.getInspectorId());
        sidelineInspection.setLocation(dto.getLocation());
        sidelineInspection.setContent(dto.getContent());
        sidelineInspection.setStartTime(dto.getStartTime());
        sidelineInspection.setEndTime(dto.getEndTime());
        sidelineInspection.setPhotos(dto.getPhotos());
        sidelineInspection.setAttachments(dto.getAttachments());
        String initialStatus = dto.getStatus() != null ? dto.getStatus() : "draft";
        sidelineInspection.setStatus(initialStatus);
        return sidelineInspectionRepository.save(sidelineInspection);
    }

    public SidelineInspection update(Long id, SidelineInspectionDTO dto) {
        SidelineInspection existing = getById(id);
        if (dto.getCode() != null && !dto.getCode().equals(existing.getCode())) {
            sidelineInspectionRepository.findByCode(dto.getCode())
                    .filter(e -> !"inactive".equals(e.getStatus()))
                    .ifPresent(e -> { throw new BusinessException("编号已存在"); });
        }
        existing.setCode(dto.getCode());
        existing.setProjectId(dto.getProjectId());
        existing.setInspectionDate(dto.getInspectionDate());
        existing.setInspectorId(dto.getInspectorId());
        existing.setLocation(dto.getLocation());
        existing.setContent(dto.getContent());
        existing.setStartTime(dto.getStartTime());
        existing.setEndTime(dto.getEndTime());
        existing.setPhotos(dto.getPhotos());
        existing.setAttachments(dto.getAttachments());
        return sidelineInspectionRepository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        SidelineInspection existing = getById(id);
        existing.setStatus("inactive");
        sidelineInspectionRepository.save(existing);
    }

    public List<SidelineInspection> findByProjectId(Long projectId) {
        return sidelineInspectionRepository.findByProjectId(projectId);
    }
}
