package com.supervision.service;

import com.supervision.common.AuthContext;
import com.supervision.entity.*;
import com.supervision.exception.BusinessException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** 服务端按项目授权；兼容旧人员单项目分配，显式停用成员后不再回退。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectAccessService {
    private final EntityManager em;

    public User currentUser() {
        Long id = AuthContext.getUserId();
        if (id == null) throw new BusinessException(401, "请先登录");
        User user = em.find(User.class, id);
        if (user == null || !"active".equals(user.getStatus())) throw new BusinessException(401, "账号不可用，请重新登录");
        return user;
    }

    public boolean isAdmin() {
        User user = currentUser();
        return em.createQuery("select count(ur) from UserRole ur, Role r where ur.roleId=r.id and ur.userId=:uid and r.code='admin'", Long.class)
                .setParameter("uid", user.getId()).getSingleResult() > 0;
    }

    public void requireAdmin() {
        if (!isAdmin()) throw new BusinessException(403, "仅管理员可以维护项目授权");
    }

    public List<Long> projectIds(boolean reviewOnly) {
        User user = currentUser();
        List<Project> projects = em.createQuery("select p from Project p where p.status <> 'inactive'", Project.class).getResultList();
        if (isAdmin()) return projects.stream().map(Project::getId).toList();
        Map<Long, ProjectMember> memberships = new HashMap<>();
        em.createQuery("select m from ProjectMember m where m.userId=:uid", ProjectMember.class)
                .setParameter("uid", user.getId()).getResultList().forEach(m -> memberships.put(m.getProjectId(), m));
        return projects.stream().filter(p -> {
            ProjectMember member = memberships.get(p.getId());
            if (member != null) return member.isEnabled() && (!reviewOnly || member.isCanReview());
            if (Objects.equals(p.getManagerId(), user.getId())) return true;
            return !reviewOnly && Objects.equals(user.getProjectId(), p.getId());
        }).map(Project::getId).toList();
    }

    public void requireRead(Long projectId) {
        if (projectId == null || !projectIds(false).contains(projectId)) throw new BusinessException(403, "无权访问该项目");
    }

    public boolean canReview(Long projectId) { return projectIds(true).contains(projectId); }

    public void requireEdit(Long projectId, Long authorId) {
        requireRead(projectId);
        if (!Objects.equals(authorId, currentUser().getId()) && !canReview(projectId))
            throw new BusinessException(403, "仅本人或项目审核人可以修改该记录");
    }

    public void requireReview(Long projectId) {
        if (!canReview(projectId)) throw new BusinessException(403, "没有该项目的审核权限");
    }

    public Predicate scope(Root<?> root, CriteriaBuilder cb) {
        List<Long> ids = projectIds(false);
        return ids.isEmpty() ? cb.disjunction() : root.get("projectId").in(ids);
    }

    public List<Map<String, Object>> members(Long projectId) {
        requireAdmin();
        List<Map<String, Object>> result = new ArrayList<>();
        for (ProjectMember m : em.createQuery("select m from ProjectMember m where m.projectId=:pid", ProjectMember.class)
                .setParameter("pid", projectId).getResultList()) {
            User user = em.find(User.class, m.getUserId());
            result.add(Map.of("userId", m.getUserId(), "userName", user == null ? "已移除人员" : user.getName(),
                    "enabled", m.isEnabled(), "canReview", m.isCanReview()));
        }
        return result;
    }

    @Transactional
    public ProjectMember setMember(Long projectId, Long userId, boolean enabled, boolean canReview) {
        requireAdmin();
        Project project = em.find(Project.class, projectId, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (project == null || "inactive".equals(project.getStatus())) throw new BusinessException(404, "项目不存在");
        User user = em.find(User.class, userId);
        if (user == null) throw new BusinessException(404, "人员不存在");
        ProjectMember member = em.createQuery("select m from ProjectMember m where m.projectId=:pid and m.userId=:uid", ProjectMember.class)
                .setParameter("pid", projectId).setParameter("uid", userId).getResultStream().findFirst().orElseGet(ProjectMember::new);
        member.setProjectId(projectId);
        member.setUserId(userId);
        member.setEnabled(enabled);
        member.setCanReview(canReview);
        if (member.getId() == null) em.persist(member);
        OperationLog event = new OperationLog();
        event.setModule("项目授权");
        event.setAction("成员授权变更");
        event.setUserId(currentUser().getId());
        event.setDetail("projectId=" + projectId + ", userId=" + userId + ", enabled=" + enabled + ", canReview=" + canReview);
        em.persist(event);
        return member;
    }
}
