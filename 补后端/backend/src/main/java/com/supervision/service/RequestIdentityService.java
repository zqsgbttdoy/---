package com.supervision.service;

import com.supervision.common.AuthContext;
import com.supervision.entity.User;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 请求身份复核：每次携带令牌的请求都以数据库当前状态为准重建身份，
 * 使「角色撤销」「账号停用」即时生效，旧令牌中的声明不再作为授权依据。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RequestIdentityService {

    private final EntityManager em;

    /**
     * 按用户 ID 复核数据库当前状态与角色。
     * 用户不存在或状态为 inactive（离职）时返回 null，由调用方按 401 处理；
     * leave（休假）仍属在职人员，保留访问能力。
     */
    public AuthContext.CurrentUser refresh(Long userId) {
        if (userId == null) return null;
        User user = em.find(User.class, userId);
        if (user == null || "inactive".equals(user.getStatus())) return null;
        List<String> roles = em.createQuery(
                "select r.code from UserRole ur, Role r where ur.roleId = r.id and ur.userId = :uid", String.class)
                .setParameter("uid", userId).getResultList();
        return new AuthContext.CurrentUser(user.getId(), user.getName(), roles);
    }
}
