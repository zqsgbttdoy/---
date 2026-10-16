package com.supervision.service;

import com.supervision.common.PageResult;
import com.supervision.common.PageUtils;
import com.supervision.dto.SupervisionMonthlyReportDTO;
import com.supervision.entity.SupervisionMonthlyReport;
import com.supervision.exception.BusinessException;
import com.supervision.repository.SupervisionMonthlyReportRepository;
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
@Transactional
@AllArgsConstructor
public class SupervisionMonthlyReportService {

    private final SupervisionMonthlyReportRepository supervisionMonthlyReportRepository;
    private final ProjectAccessService access;
    private final AttachmentAccessService attachmentAccess;

    public PageResult<SupervisionMonthlyReport> list(int pageNum, int pageSize, Long projectId, String code, String status) {
        pageNum = PageUtils.normalizePageNum(pageNum);
        pageSize = PageUtils.normalizePageSize(pageSize);
        Specification<SupervisionMonthlyReport> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(access.scope(root, cb));
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
        Page<SupervisionMonthlyReport> page = supervisionMonthlyReportRepository.findAll(spec,
                PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime")));
        return new PageResult<>(page.getContent(), page.getTotalElements(), pageNum, pageSize);
    }

    public SupervisionMonthlyReport getById(Long id) {
        SupervisionMonthlyReport record = supervisionMonthlyReportRepository.findById(id)
                .orElseThrow(() -> new BusinessException("监理月报不存在"));
        access.requireRead(record.getProjectId());
        return record;
    }

    public SupervisionMonthlyReport create(SupervisionMonthlyReportDTO dto) {
        access.requireRead(dto.getProjectId());
        attachmentAccess.bind(dto.getProjectId(), dto.getPhotos(), dto.getAttachments());
        supervisionMonthlyReportRepository.findByCode(dto.getCode())
                .filter(e -> !"inactive".equals(e.getStatus()))
                .ifPresent(e -> { throw new BusinessException("编号已存在"); });
        SupervisionMonthlyReport supervisionMonthlyReport = new SupervisionMonthlyReport();
        supervisionMonthlyReport.setCode(dto.getCode());
        supervisionMonthlyReport.setProjectId(dto.getProjectId());
        supervisionMonthlyReport.setReportMonth(dto.getReportMonth());
        supervisionMonthlyReport.setAuthorId(access.currentUser().getId());
        supervisionMonthlyReport.setContent(dto.getContent());
        supervisionMonthlyReport.setPhotos(dto.getPhotos());
        supervisionMonthlyReport.setAttachments(dto.getAttachments());
        supervisionMonthlyReport.setStatus("draft");
        return supervisionMonthlyReportRepository.save(supervisionMonthlyReport);
    }

    public SupervisionMonthlyReport update(Long id, SupervisionMonthlyReportDTO dto) {
        SupervisionMonthlyReport existing = getById(id);
        access.requireEdit(existing.getProjectId(), existing.getAuthorId());
        if (!java.util.Objects.equals(dto.getProjectId(), existing.getProjectId())) throw new BusinessException(400, "不可更换记录所属项目");
        attachmentAccess.bind(existing.getProjectId(), dto.getPhotos(), dto.getAttachments());
        if (dto.getCode() != null && !dto.getCode().equals(existing.getCode())) {
            supervisionMonthlyReportRepository.findByCode(dto.getCode())
                    .filter(e -> !"inactive".equals(e.getStatus()))
                    .ifPresent(e -> { throw new BusinessException("编号已存在"); });
        }
        existing.setCode(dto.getCode());
        existing.setProjectId(dto.getProjectId());
        existing.setReportMonth(dto.getReportMonth());
        existing.setContent(dto.getContent());
        existing.setPhotos(dto.getPhotos());
        existing.setAttachments(dto.getAttachments());
        return supervisionMonthlyReportRepository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        SupervisionMonthlyReport existing = getById(id);
        access.requireEdit(existing.getProjectId(), existing.getAuthorId());
        existing.setStatus("inactive");
        supervisionMonthlyReportRepository.save(existing);
    }
}
