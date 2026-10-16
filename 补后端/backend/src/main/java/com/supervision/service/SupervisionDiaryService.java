package com.supervision.service;

import com.supervision.common.PageResult;
import com.supervision.common.PageUtils;
import com.supervision.dto.ApproveDTO;
import com.supervision.dto.SupervisionDiaryDTO;
import com.supervision.entity.SupervisionDiary;
import com.supervision.exception.BusinessException;
import com.supervision.repository.SupervisionDiaryRepository;
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
public class SupervisionDiaryService {

    private final SupervisionDiaryRepository supervisionDiaryRepository;

    public PageResult<SupervisionDiary> list(int pageNum, int pageSize, Long projectId, String code, String status) {
        pageNum = PageUtils.normalizePageNum(pageNum);
        // 统计页需一次拉取全量数据做聚合，使用放宽的统计上限（2000）
        pageSize = PageUtils.normalizePageSizeForStats(pageSize);
        Specification<SupervisionDiary> spec = (root, query, cb) -> {
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
        Page<SupervisionDiary> page = supervisionDiaryRepository.findAll(spec,
                PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime")));
        return new PageResult<>(page.getContent(), page.getTotalElements(), pageNum, pageSize);
    }

    public SupervisionDiary getById(Long id) {
        return supervisionDiaryRepository.findById(id)
                .orElseThrow(() -> new BusinessException("监理日记不存在"));
    }

    public SupervisionDiary create(SupervisionDiaryDTO dto) {
        supervisionDiaryRepository.findByCode(dto.getCode())
                .filter(e -> !"inactive".equals(e.getStatus()))
                .ifPresent(e -> { throw new BusinessException("编号已存在"); });
        SupervisionDiary supervisionDiary = new SupervisionDiary();
        supervisionDiary.setCode(dto.getCode());
        supervisionDiary.setProjectId(dto.getProjectId());
        supervisionDiary.setDiaryDate(dto.getDiaryDate());
        supervisionDiary.setAuthorId(dto.getAuthorId());
        supervisionDiary.setWeather(dto.getWeather());
        supervisionDiary.setWind(dto.getWind());
        supervisionDiary.setTemperature(dto.getTemperature());
        supervisionDiary.setConstruction(dto.getConstruction());
        supervisionDiary.setManagement(dto.getManagement());
        supervisionDiary.setInspection(dto.getInspection());
        supervisionDiary.setOtherMatters(dto.getOtherMatters());
        supervisionDiary.setMorningMeeting(dto.getMorningMeeting());
        supervisionDiary.setDrawingReview(dto.getDrawingReview());
        supervisionDiary.setPhotos(dto.getPhotos());
        supervisionDiary.setAttachments(dto.getAttachments());
        supervisionDiary.setStatus("draft");
        supervisionDiary.setVersion(1);
        return supervisionDiaryRepository.save(supervisionDiary);
    }

    public SupervisionDiary update(Long id, SupervisionDiaryDTO dto) {
        SupervisionDiary existing = getById(id);
        if (dto.getCode() != null && !dto.getCode().equals(existing.getCode())) {
            supervisionDiaryRepository.findByCode(dto.getCode())
                    .filter(e -> !"inactive".equals(e.getStatus()))
                    .ifPresent(e -> { throw new BusinessException("编号已存在"); });
        }
        existing.setCode(dto.getCode());
        existing.setProjectId(dto.getProjectId());
        existing.setDiaryDate(dto.getDiaryDate());
        existing.setAuthorId(dto.getAuthorId());
        existing.setWeather(dto.getWeather());
        existing.setWind(dto.getWind());
        existing.setTemperature(dto.getTemperature());
        existing.setConstruction(dto.getConstruction());
        existing.setManagement(dto.getManagement());
        existing.setInspection(dto.getInspection());
        existing.setOtherMatters(dto.getOtherMatters());
        existing.setMorningMeeting(dto.getMorningMeeting());
        existing.setDrawingReview(dto.getDrawingReview());
        existing.setPhotos(dto.getPhotos());
        existing.setAttachments(dto.getAttachments());
        return supervisionDiaryRepository.save(existing);
    }

    @Transactional
    public void delete(Long id) {
        SupervisionDiary existing = getById(id);
        existing.setStatus("inactive");
        supervisionDiaryRepository.save(existing);
    }

    @Transactional
    public SupervisionDiary approve(ApproveDTO dto) {
        if (dto.getStatus() == null) {
            throw new BusinessException("审核状态不能为空");
        }
        SupervisionDiary existing = getById(dto.getId());
        if (!"draft".equals(existing.getStatus()) && !"submitted".equals(existing.getStatus())) {
            throw new BusinessException("只有草稿或已提交状态的日记才能审批");
        }
        if ("approved".equals(dto.getStatus())) {
            existing.setStatus("approved");
            existing.setRejectReason(null);
        } else if ("rejected".equals(dto.getStatus())) {
            existing.setStatus("rejected");
            existing.setRejectReason(dto.getRejectReason());
        }
        return supervisionDiaryRepository.save(existing);
    }

    public SupervisionDiary newVersion(Long id) {
        SupervisionDiary existing = getById(id);
        SupervisionDiary newVer = new SupervisionDiary();
        newVer.setCode(existing.getCode());
        newVer.setProjectId(existing.getProjectId());
        newVer.setDiaryDate(existing.getDiaryDate());
        newVer.setAuthorId(existing.getAuthorId());
        newVer.setWeather(existing.getWeather());
        newVer.setWind(existing.getWind());
        newVer.setTemperature(existing.getTemperature());
        newVer.setConstruction(existing.getConstruction());
        newVer.setManagement(existing.getManagement());
        newVer.setInspection(existing.getInspection());
        newVer.setOtherMatters(existing.getOtherMatters());
        newVer.setMorningMeeting(existing.getMorningMeeting());
        newVer.setDrawingReview(existing.getDrawingReview());
        newVer.setPhotos(existing.getPhotos());
        newVer.setAttachments(existing.getAttachments());
        newVer.setStatus("draft");
        newVer.setVersion(existing.getVersion() + 1);
        return supervisionDiaryRepository.save(newVer);
    }

    public List<SupervisionDiary> findByProjectId(Long projectId) {
        return supervisionDiaryRepository.findByProjectId(projectId);
    }
}
