package com.supervision.repository;

import com.supervision.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long>, JpaSpecificationExecutor<Message> {

    List<Message> findByReceiverId(Long receiverId);

    List<Message> findByReceiverIdAndIsRead(Long receiverId, String isRead);

    long countByReceiverIdAndIsRead(Long receiverId, String isRead);
}
