-- ============================================================
-- 仅本地 E2E 验证使用的种子数据（配合 application-e2e.yml，H2 内存库）
-- 密码哈希复用 schema.sql 种子行，对应明文：123456
-- 登录链路：AuthService 按 phone 或 name 查用户，要求 status='active'
-- ============================================================

INSERT INTO sys_department (id, name, parent_id, description, leader, sort_order, status, create_time, update_time) VALUES
(1, '总公司',   NULL, '监理总公司',     '赵总监', 1, 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, '监理一部', 1,    '第一监理事业部', '张监理', 1, 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO sys_user (id, name, gender, dept_id, position, cert_no, phone, id_card, project_id, hire_date, status, password, avatar, create_time, update_time) VALUES
(1, 'admin',  '男', 1, '系统管理员', NULL,      '13800000000', NULL, NULL, '2024-01-01', 'active', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, '赵总监', '男', 1, '总监',       'JLZ-001', '13800000001', NULL, 1,    '2024-01-01', 'active', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO sys_role (id, name, code, description, status, create_time, update_time) VALUES
(1, '系统管理员', 'admin', '拥有系统所有权限', 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO sys_user_role (id, user_id, role_id, create_time, update_time) VALUES
(1, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(2, 2, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
