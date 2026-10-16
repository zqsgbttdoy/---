package com.supervision.config;

import com.supervision.common.SystemAdmin;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;

/**
 * 启动自检：每次启动（开发 / 生产 / 演示，所有 profile）都执行一次幂等脚本 admin-full-permissions.sql，
 * 保证 admin 账号启用、拥有 admin 角色、全部权限项（含直接写库新增的）都绑定给 admin 角色。
 * 以前该脚本只在 e2e 演示库自动执行，MySQL 环境依赖人工执行，漏执行就会出现“admin 权限不足”。
 * 自检失败回滚并阻断启动，禁止以管理员权限不完整的状态对外服务。
 * 如需关闭：app.security.admin-bootstrap=false。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminPermissionInitializer implements ApplicationRunner {

    static final String SCRIPT = "admin-full-permissions.sql";

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.security.admin-bootstrap:true}")
    private boolean enabled;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.warn("admin 权限自检已关闭（app.security.admin-bootstrap=false）");
            return;
        }
        try {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource(SCRIPT));
            populator.setSqlScriptEncoding("UTF-8");
            DatabasePopulatorUtils.execute(populator, dataSource);
            Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_permission", Integer.class);
            Integer bound = jdbcTemplate.queryForObject(
                    "SELECT COUNT(DISTINCT rp.permission_id) FROM sys_role_permission rp JOIN sys_role r ON r.id = rp.role_id"
                            + " WHERE r.code = ?", Integer.class, SystemAdmin.ROLE_CODE);
            Integer adminUsers = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_user u JOIN sys_user_role ur ON ur.user_id = u.id JOIN sys_role r ON r.id = ur.role_id"
                            + " WHERE u.name = ? AND r.code = ? AND u.status = 'active'", Integer.class,
                    SystemAdmin.USER_NAME, SystemAdmin.ROLE_CODE);
            Integer missing = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_role r CROSS JOIN sys_permission p WHERE r.code = ?"
                            + " AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id)",
                    Integer.class, SystemAdmin.ROLE_CODE);
            if (total == null || total < 31 || adminUsers == null || adminUsers == 0 || missing == null || missing > 0) {
                throw new IllegalStateException("admin 账号或权限绑定不完整，拒绝启动");
            }
            log.info("admin 权限自检完成：admin 角色已绑定权限 {} / 共 {}，admin 账号已启用并持有 admin 角色={}",
                    bound, total, adminUsers != null && adminUsers > 0);
        } catch (Exception e) {
            log.error("admin 权限自检失败，请手工执行 {}（mysql --default-character-set=utf8mb4 supervision_db < {}）",
                    SCRIPT, SCRIPT, e);
            throw new IllegalStateException("admin 权限自检失败，启动已中止，请查看日志", e);
        }
    }
}
