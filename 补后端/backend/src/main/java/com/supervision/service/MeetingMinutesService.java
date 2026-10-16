package com.supervision.service;

import com.supervision.common.PageResult;
import com.supervision.common.PageUtils;
import com.supervision.dto.MeetingMinutesDTO;
import com.supervision.entity.MeetingMinutes;
import com.supervision.exception.BusinessException;
import com.supervision.repository.MeetingMinutesRepository;
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
public class MeetingMinutesService {

    private final MeetingMinutesRepository meetingMinutesRepository;
    private final ProjectAccessService access;
    private final AttachmentAccessService attachmentAccess;

    public PageResult<MeetingMinutes> list(int pageNum, int pageSize, Long projectId, String code, String status) {
        pageNum = PageUtils.normalizePageNum(pageNum);
        pageSize = PageUtils.normalizePageSize(pageSize);
        Specification<MeetingMinutes> spec = (root, query, cb) -> {
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
        Page<MeetingMinutes> page = meetingMinutesRepository.findAll(spec,
                PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime")));
        return new PageResult<>(page.getContent(), page.getTotalElements(), pageNum, pageSize);
    }

    public MeetingMinutes getById(Long id) {
        MeetingMinutes record = meetingMinutesRepository.findById(id)
                .orElseThrow(() -> new BusinessException("会议纪要不存在"));
        access.requireRead(record.getProjectId());
        return record;
    }

    public MeetingMinutes create(MeetingMinutesDTO dto) {
        access.requireRead(dto.getProjectId());
        attachmentAccess.bind(dto.getProjectId(), dto.getPhotos(), dto.getAttachments());
        meetingMinutesRepository.findByCode(dto.getCode())
                .filter(e -> !"inactive".equals(e.getStatus()))
                .ifPresent(e -> { throw new BusinessException("编号已存在"); });
        MeetingMinutes meetingMinutes = new MeetingMinutes();
        meetingMinutes.setCode(dto.getCode());
        meetingMinutes.setProjectId(dto.getProjectId());
        meetingMinutes.setMeetingDate(dto.getMeetingDate());
        meetingMinutes.setAuthorId(access.currentUser().getId());
        meetingMinutes.setTitle(dto.getTitle());
        meetingMinutes.setContent(dto.getContent());
        meetingMinutes.setAttendees(dto.getAttendees());
        meetingMinutes.setLocation(dto.getLocation());
        meetingMinutes.setPhotos(dto.getPhotos());
        meetingMinutes.setAttachments(dto.getAttachments());
        meetingMinutes.setStatus("draft");
        return meetingMinutesRepository.save(meetingMinutes);
    }

    public MeetingMinutes update(Long id, MeetingMinutesDTO dto) {
        MeetingMinutes existing = getById(id);
        access.requireEdit(existing.getProjectId(), existing.getAuthorId());
        if (!java.util.Objects.equals(dto.getProjectId(), existing.getProjectId())) throw new BusinessException(400, "不可更换记录所属项目");
        attachmentAccess.bind(existing.getProjectId(), dto.getPhotos(), dto.getAttachments());
        if (dto.getCode() != null && !dto.getCode().equals(existing.getCode())) {
            meetingMinutesRepository.findByCode(dto.getCode())
                    .filter(e -> !"inactive".equals(e.getStatus()))
                    .ifPresent(e -> { throw new BusinessException("编号已存在"); });
        }
        existing.setCode(dto.getCode());
        existing.setProjectId(dto.getProjectId());
        existing.setMeetingDate(dto.getMeetingDate());
        existing.setTitle(dto.getTitle());
        existing.setContent(dto.getContent());
        existing.setAttendees(dto.getAttendees());
        existing.setLocation(dto.getLocation());
        existing.setPhotos(dto.getPhotos());
        existing.setAttachments(dto.getAttachments());
        return meetingMinutesRepository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        MeetingMinutes existing = getById(id);
        access.requireEdit(existing.getProjectId(), existing.getAuthorId());
        existing.setStatus("inactive");
        meetingMinutesRepository.save(existing);
    }
}
