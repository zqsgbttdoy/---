-- ============================================================
-- admin 最高权限初始化（幂等，可重复执行，不会产生重复数据）
-- 适用：生产/开发 MySQL 8.0（supervision_db）与本地演示 H2；后端每次启动都会由 AdminPermissionInitializer 自动执行本脚本，
--       一般无需手工执行，仅在启动日志出现“admin 权限自检失败”时按下行用法手工补救
-- 用法（服务器）：sudo mysql --default-character-set=utf8mb4 supervision_db < admin-full-permissions.sql
--
-- 权限生效规则（与后端源码一致）：
--   1) 后端接口鉴权只认角色编码 'admin'（@RequireRole("admin") 与 ProjectAccessService.isAdmin()），
--      admin 角色天然拥有全部接口、全部项目的读写与审核权限；
--   2) sys_permission / sys_role_permission 用于“权限设置”页展示与勾选，本脚本把全部菜单权限绑定给 admin 角色；
--   3) 角色变更即时生效（每次请求实时查库），无需重启服务、无需重新登录。
-- 编写约定：只用“字段 = 字面量”比较、FROM DUAL 取单行，兼容 MySQL 8（避免派生表排序规则冲突）与 H2。
-- 本脚本不修改 admin 密码。
-- ============================================================

-- 1. admin 账号启用
UPDATE sys_user SET status = 'active', update_time = CURRENT_TIMESTAMP
WHERE name = 'admin' AND (status IS NULL OR status <> 'active');

-- 2. 系统管理员角色（编码 admin）存在且启用
INSERT INTO sys_role (name, code, description, status, create_time, update_time)
SELECT '系统管理员', 'admin', '拥有系统所有权限', 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE code = 'admin');

UPDATE sys_role SET status = 'active', update_time = CURRENT_TIMESTAMP
WHERE code = 'admin' AND (status IS NULL OR status <> 'active');

-- 3. admin 账号绑定系统管理员角色
INSERT INTO sys_user_role (user_id, role_id, create_time, update_time)
SELECT u.id, r.id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM sys_user u, sys_role r
WHERE u.name = 'admin' AND r.code = 'admin'
  AND NOT EXISTS (SELECT 1 FROM sys_user_role ur WHERE ur.user_id = u.id AND ur.role_id = r.id);

-- 4. 菜单权限分组（7 个，与左侧导航分组一致）
INSERT INTO sys_permission (name, code, module, description, parent_id, sort_order, create_time, update_time)
SELECT '仪表盘', 'menu:home', 'home', '首页仪表盘', NULL, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'menu:home');
INSERT INTO sys_permission (name, code, module, description, parent_id, sort_order, create_time, update_time)
SELECT '项目管理', 'menu:project', 'project', '项目台账', NULL, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'menu:project');
INSERT INTO sys_permission (name, code, module, description, parent_id, sort_order, create_time, update_time)
SELECT '监理现场工作', 'menu:field', 'field', '巡检、旁站、通知单、联系单、日记、日志、会议、月报', NULL, 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'menu:field');
INSERT INTO sys_permission (name, code, module, description, parent_id, sort_order, create_time, update_time)
SELECT '项目资料', 'menu:document', 'document', '工程资料目录与上传', NULL, 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'menu:document');
INSERT INTO sys_permission (name, code, module, description, parent_id, sort_order, create_time, update_time)
SELECT '数据统计', 'menu:statistics', 'statistics', '日记、日志、安全日志统计与报表', NULL, 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'menu:statistics');
INSERT INTO sys_permission (name, code, module, description, parent_id, sort_order, create_time, update_time)
SELECT '团队管理', 'menu:team', 'team', '部门、人员、权限、流程', NULL, 6, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'menu:team');
INSERT INTO sys_permission (name, code, module, description, parent_id, sort_order, create_time, update_time)
SELECT '系统设置', 'menu:system', 'system', '操作日志、消息中心、工作标准配置', NULL, 7, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'menu:system');

-- 5. 功能页面权限（24 个，与左侧导航菜单一一对应；description 为页面文件名）
INSERT INTO sys_permission (name, code, module, description, parent_id, sort_order, create_time, update_time)
SELECT '仪表盘', 'page:index', p.module, 'index.html', p.id, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM sys_permission p
WHERE p.code = 'menu:home' AND NOT EXISTS (SELECT 1 FROM sys_permission x WHERE x.code = 'page:index');
INSERT INTO sys_permission (name, code, module, description, parent_id, sort_order, create_time, update_time)
SELECT '项目台账', 'page:project-ledger', p.module, 'project-ledger.html', p.id, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM sys_permission p
WHERE p.code = 'menu:project' AND NOT EXISTS (SELECT 1 FROM sys_permission x WHERE x.code = 'page:project-ledger');
INSERT INTO sys_permission (name, code, module, description, parent_id, sort_order, create_time, update_time)
SELECT '日常巡检', 'page:daily-inspection', p.module, 'daily-inspection.html', p.id, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM sys_permission p
WHERE p.code = 'menu:field' AND NOT EXISTS (SELECT 1 FROM sys_permission x WHERE x.code = 'page:daily-inspection');
INSERT INTO sys_permission (name, code, module, description, parent_id, sort_order, create_time, update_time)
SELECT '旁站', 'page:sideline-inspection', p.module, 'sideline-inspection.html', p.id, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM sys_permission p
WHERE p.code = 'menu:field' AND NOT EXISTS (SELECT 1 FROM sys_permission x WHERE x.code = 'page:sideline-inspection');
INSERT INTO sys_permission (name, code, module, description, parent_id, sort_order, create_time, update_time)
SELECT '监理通知单', 'page:supervision-notice', p.module, 'supervision-notice.html', p.id, 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM sys_permission p
WHERE p.code = 'menu:field' AND NOT EXISTS (SELECT 1 FROM sys_permission x WHERE x.code = 'page:supervision-notice');
INSERT INTO sys_permission (name, code, module, description, parent_id, sort_order, create_time, update_time)
SELECT '工作联系单', 'page:work-contact', p.module, 'work-contact.html', p.id, 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM sys_permission p
WHERE p.code = 'menu:field' AND NOT EXISTS (SELECT 1 FROM sys_permission x WHERE x.code = 'page:work-contact');
INSERT INTO sys_permission (name, code, module, description, parent_id, sort_order, create_time, update_time)
SELECT '监理日记', 'page:supervision-diary', p.module, 'supervision-diary.html', p.id, 5, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM sys_permission p
WHERE p.code = 'menu:field' AND NOT EXISTS (SELECT 1 FROM sys_permission x WHERE x.code = 'page:supervision-diary');
INSERT INTO sys_permission (name, code, module, description, parent_id, sort_order, create_time, update_time)
SELECT '监理日志', 'page:supervision-log', p.module, 'supervision-log.html', p.id, 6, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM sys_permission p
WHERE p.code = 'menu:field' AND NOT EXISTS (SELECT 1 FROM sys_permission x WHERE x.code = 'page:supervision-log');
INSERT INTO sys_permission (name, code, module, description, parent_id, sort_order, create_time, update_time)
SELECT '监理安全日志', 'page:safety-log', p.module, 'safety-log.html', p.id, 7, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM sys_permission p
WHERE p.code = 'menu:field' AND NOT EXISTS (SELECT 1 FROM sys_permission x WHERE x.code = 'page:safety-log');
INSERT INTO sys_permission (name, code, module, description, parent_id, sort_order, create_time, update_time)
SELECT '会议纪要', 'page:meeting-minutes', p.module, 'meeting-minutes.html', p.id, 8, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM sys_permission p
WHERE p.code = 'menu:field' AND NOT EXISTS (SELECT 1 FROM sys_permission x WHERE x.code = 'page:meeting-minutes');
INSERT INTO sys_permission (name, code, module, description, parent_id, sort_order, create_time, update_time)
SELECT '监理月报', 'page:supervision-monthly-report', p.module, 'supervision-monthly-report.html', p.id, 9, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM sys_permission p
WHERE p.code = 'menu:field' AND NOT EXISTS (SELECT 1 FROM sys_permission x WHERE x.code = 'page:supervision-monthly-report');
INSERT INTO sys_permission (name, code, module, description, parent_id, sort_order, create_time, update_time)
SELECT '工程资料目录设置', 'page:sub-item-database', p.module, 'sub-item-database.html', p.id, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM sys_permission p
WHERE p.code = 'menu:document' AND NOT EXISTS (SELECT 1 FROM sys_permission x WHERE x.code = 'page:sub-item-database');
INSERT INTO sys_permission (name, code, module, description, parent_id, sort_order, create_time, update_time)
SELECT '工程资料上传', 'page:project-data-upload', p.module, 'project-data-upload.html', p.id, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM sys_permission p
WHERE p.code = 'menu:document' AND NOT EXISTS (SELECT 1 FROM sys_permission x WHERE x.code = 'page:project-data-upload');
INSERT INTO sys_permission (name, code, module, description, parent_id, sort_order, create_time, update_time)
SELECT '日记统计', 'page:diary-statistics', p.module, 'diary-statistics.html', p.id, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM sys_permission p
WHERE p.code = 'menu:statistics' AND NOT EXISTS (SELECT 1 FROM sys_permission x WHERE x.code = 'page:diary-statistics');
INSERT INTO sys_permission (name, code, module, description, parent_id, sort_order, create_time, update_time)
SELECT '日记报表', 'page:diary-report', p.module, 'diary-report.html', p.id, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM sys_permission p
WHERE p.code = 'menu:statistics' AND NOT EXISTS (SELECT 1 FROM sys_permission x WHERE x.code = 'page:diary-report');
INSERT INTO sys_permission (name, code, module, description, parent_id, sort_order, create_time, update_time)
SELECT '日志统计', 'page:log-statistics', p.module, 'log-statistics.html', p.id, 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM sys_permission p
WHERE p.code = 'menu:statistics' AND NOT EXISTS (SELECT 1 FROM sys_permission x WHERE x.code = 'page:log-statistics');
INSERT INTO sys_permission (name, code, module, description, parent_id, sort_order, create_time, update_time)
SELECT '安全日志统计', 'page:safety-log-statistics', p.module, 'safety-log-statistics.html', p.id, 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM sys_permission p
WHERE p.code = 'menu:statistics' AND NOT EXISTS (SELECT 1 FROM sys_permission x WHERE x.code = 'page:safety-log-statistics');
INSERT INTO sys_permission (name, code, module, description, parent_id, sort_order, create_time, update_time)
SELECT '部门管理', 'page:department-management', p.module, 'department-management.html', p.id, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM sys_permission p
WHERE p.code = 'menu:team' AND NOT EXISTS (SELECT 1 FROM sys_permission x WHERE x.code = 'page:department-management');
INSERT INTO sys_permission (name, code, module, description, parent_id, sort_order, create_time, update_time)
SELECT '人员管理', 'page:personnel-management', p.module, 'personnel-management.html', p.id, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM sys_permission p
WHERE p.code = 'menu:team' AND NOT EXISTS (SELECT 1 FROM sys_permission x WHERE x.code = 'page:personnel-management');
INSERT INTO sys_permission (name, code, module, description, parent_id, sort_order, create_time, update_time)
SELECT '权限设置', 'page:permission-settings', p.module, 'permission-settings.html', p.id, 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM sys_permission p
WHERE p.code = 'menu:team' AND NOT EXISTS (SELECT 1 FROM sys_permission x WHERE x.code = 'page:permission-settings');
INSERT INTO sys_permission (name, code, module, description, parent_id, sort_order, create_time, update_time)
SELECT '流程管理', 'page:process-management', p.module, 'process-management.html', p.id, 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM sys_permission p
WHERE p.code = 'menu:team' AND NOT EXISTS (SELECT 1 FROM sys_permission x WHERE x.code = 'page:process-management');
INSERT INTO sys_permission (name, code, module, description, parent_id, sort_order, create_time, update_time)
SELECT '操作日志', 'page:operation-log', p.module, 'operation-log.html', p.id, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM sys_permission p
WHERE p.code = 'menu:system' AND NOT EXISTS (SELECT 1 FROM sys_permission x WHERE x.code = 'page:operation-log');
INSERT INTO sys_permission (name, code, module, description, parent_id, sort_order, create_time, update_time)
SELECT '消息中心', 'page:message-center', p.module, 'message-center.html', p.id, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM sys_permission p
WHERE p.code = 'menu:system' AND NOT EXISTS (SELECT 1 FROM sys_permission x WHERE x.code = 'page:message-center');
INSERT INTO sys_permission (name, code, module, description, parent_id, sort_order, create_time, update_time)
SELECT '工作标准配置', 'page:work-standard-config', p.module, 'work-standard-config.html', p.id, 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM sys_permission p
WHERE p.code = 'menu:system' AND NOT EXISTS (SELECT 1 FROM sys_permission x WHERE x.code = 'page:work-standard-config');

-- 6. 全部权限（含以后新增的权限项）绑定给 admin 角色
INSERT INTO sys_role_permission (role_id, permission_id, create_time, update_time)
SELECT r.id, p.id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM sys_role r, sys_permission p
WHERE r.code = 'admin'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- 7. 核验（期望：status=active，is_admin=1，permission_total 与 admin_bound 相等，首次执行均为 31）
SELECT u.id, u.name, u.status,
       (SELECT COUNT(*) FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id
         WHERE ur.user_id = u.id AND r.code = 'admin' AND r.status = 'active') AS is_admin,
       (SELECT COUNT(*) FROM sys_permission) AS permission_total,
       (SELECT COUNT(*) FROM sys_role_permission rp JOIN sys_role r ON r.id = rp.role_id
         WHERE r.code = 'admin') AS admin_bound
FROM sys_user u
WHERE u.name = 'admin';
