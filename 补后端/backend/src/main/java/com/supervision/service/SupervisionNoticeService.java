package com.supervision.service;

import com.supervision.common.PageResult;
import com.supervision.common.PageUtils;
import com.supervision.dto.ReplyDTO;
import com.supervision.dto.SupervisionNoticeDTO;
import com.supervision.entity.SupervisionNotice;
import com.supervision.exception.BusinessException;
import com.supervision.repository.SupervisionNoticeRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
@AllArgsConstructor
public class SupervisionNoticeService {

    private final SupervisionNoticeRepository supervisionNoticeRepository;
    private final ProjectAccessService access;
    private final AttachmentAccessService attachmentAccess;

    public PageResult<SupervisionNotice> list(int pageNum, int pageSize, Long projectId, String code, String status) {
        pageNum = PageUtils.normalizePageNum(pageNum);
        pageSize = PageUtils.normalizePageSize(pageSize);
        Specification<SupervisionNotice> spec = (root, query, cb) -> {
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
        Page<SupervisionNotice> page = supervisionNoticeRepository.findAll(spec,
                PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime")));
        return new PageResult<>(page.getContent(), page.getTotalElements(), pageNum, pageSize);
    }

    public SupervisionNotice getById(Long id) {
        SupervisionNotice record = supervisionNoticeRepository.findById(id)
                .orElseThrow(() -> new BusinessException("监理通知不存在"));
        access.requireRead(record.getProjectId());
        return record;
    }

    public SupervisionNotice create(SupervisionNoticeDTO dto) {
        access.requireRead(dto.getProjectId());
        attachmentAccess.bind(dto.getProjectId(), dto.getPhotos(), dto.getAttachments());
        supervisionNoticeRepository.findByCode(dto.getCode())
                .filter(e -> !"inactive".equals(e.getStatus()))
                .ifPresent(e -> { throw new BusinessException("编号已存在"); });
        SupervisionNotice supervisionNotice = new SupervisionNotice();
        supervisionNotice.setCode(dto.getCode());
        supervisionNotice.setProjectId(dto.getProjectId());
        supervisionNotice.setNoticeDate(dto.getNoticeDate());
        supervisionNotice.setAuthorId(access.currentUser().getId());
        supervisionNotice.setTitle(dto.getTitle());
        supervisionNotice.setContent(dto.getContent());
        supervisionNotice.setDeadline(dto.getDeadline());
        supervisionNotice.setPriority(dto.getPriority());
        supervisionNotice.setPhotos(dto.getPhotos());
        supervisionNotice.setAttachments(dto.getAttachments());
        String initialStatus = dto.getStatus() != null ? dto.getStatus() : "draft";
        // 创建只允许草稿或直接下发；回复/验证等后续状态必须走对应业务流程，禁止创建即终态跳过审批流。
        if (!"draft".equals(initialStatus) && !"issued".equals(initialStatus)) {
            throw new BusinessException(400, "创建通知单仅允许 draft/issued 初始状态，当前状态请通过后续流程流转");
        }
        supervisionNotice.setStatus(initialStatus);
        return supervisionNoticeRepository.save(supervisionNotice);
    }

    public SupervisionNotice update(Long id, SupervisionNoticeDTO dto) {
        SupervisionNotice existing = getById(id);
        access.requireEdit(existing.getProjectId(), existing.getAuthorId());
        if (!java.util.Objects.equals(dto.getProjectId(), existing.getProjectId())) throw new BusinessException(400, "不可更换记录所属项目");
        attachmentAccess.bind(existing.getProjectId(), dto.getPhotos(), dto.getAttachments());
        if (dto.getCode() != null && !dto.getCode().equals(existing.getCode())) {
            supervisionNoticeRepository.findByCode(dto.getCode())
                    .filter(e -> !"inactive".equals(e.getStatus()))
                    .ifPresent(e -> { throw new BusinessException("编号已存在"); });
        }
        existing.setCode(dto.getCode());
        existing.setProjectId(dto.getProjectId());
        existing.setNoticeDate(dto.getNoticeDate());
        existing.setTitle(dto.getTitle());
        existing.setContent(dto.getContent());
        existing.setDeadline(dto.getDeadline());
        existing.setPriority(dto.getPriority());
        existing.setPhotos(dto.getPhotos());
        existing.setAttachments(dto.getAttachments());
        return supervisionNoticeRepository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        SupervisionNotice existing = getById(id);
        access.requireEdit(existing.getProjectId(), existing.getAuthorId());
        existing.setStatus("inactive");
        supervisionNoticeRepository.save(existing);
    }

    public SupervisionNotice reply(ReplyDTO dto) {
        SupervisionNotice existing = getById(dto.getId());
        if (!"issued".equals(existing.getStatus()) && !"overdue".equals(existing.getStatus())) {
            throw new BusinessException("只有已下发或已逾期状态的通知单才能回复");
        }
        existing.setReplyContent(dto.getReplyContent());
        existing.setReplyDate(LocalDate.now());
        existing.setStatus("responded");
        return supervisionNoticeRepository.save(existing);
    }

    public List<SupervisionNotice> findByStatus(String status) {
        return supervisionNoticeRepository.findAll((root, query, cb) -> cb.and(access.scope(root, cb), cb.equal(root.get("status"), status)));
    }
}
