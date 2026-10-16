package com.supervision.service;

import com.supervision.common.PageResult;
import com.supervision.common.PageUtils;
import com.supervision.dto.MessageDTO;
import com.supervision.entity.Message;
import com.supervision.exception.BusinessException;
import com.supervision.repository.MessageRepository;
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
public class MessageService {

    private final MessageRepository messageRepository;
    private final ProjectAccessService access;

    private Long requireRecipient(Long receiverId) {
        Long currentId = access.currentUser().getId();
        if (receiverId != null && !currentId.equals(receiverId))
            throw new BusinessException(403, "只能访问本人的消息");
        return currentId;
    }

    public PageResult<Message> list(int pageNum, int pageSize, Long receiverId, String type, String isRead) {
        Long currentId = requireRecipient(receiverId);
        pageNum = PageUtils.normalizePageNum(pageNum);
        pageSize = PageUtils.normalizePageSize(pageSize);
        Specification<Message> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("receiverId"), currentId));
            if (type != null && !type.isEmpty()) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (isRead != null && !isRead.isEmpty()) {
                predicates.add(cb.equal(root.get("isRead"), isRead));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<Message> page = messageRepository.findAll(spec,
                PageRequest.of(pageNum - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime")));
        return new PageResult<>(page.getContent(), page.getTotalElements(), pageNum, pageSize);
    }

    public Message getById(Long id) {
        Long currentId = requireRecipient(null);
        Message message = messageRepository.findById(id)
                .orElseThrow(() -> new BusinessException(404, "消息不存在"));
        if (!currentId.equals(message.getReceiverId())) throw new BusinessException(403, "只能访问本人的消息");
        return message;
    }

    public Message create(MessageDTO dto) {
        Message message = new Message();
        message.setSenderId(access.currentUser().getId());
        message.setReceiverId(dto.getReceiverId());
        message.setTitle(dto.getTitle());
        message.setContent(dto.getContent());
        message.setType(dto.getType());
        message.setRelatedId(dto.getRelatedId());
        message.setRelatedType(dto.getRelatedType());
        message.setIsRead("0");
        return messageRepository.save(message);
    }

    @Transactional
    public void delete(Long id) {
        messageRepository.delete(getById(id));
    }

    @Transactional
    public void markRead(Long id) {
        Message existing = getById(id);
        existing.setIsRead("1");
        messageRepository.save(existing);
    }

    public long getUnreadCount(Long receiverId) {
        return messageRepository.countByReceiverIdAndIsRead(requireRecipient(receiverId), "0");
    }
}
