-- ============================================================
-- 性能优化：复合索引补齐脚本（从 schema.sql 抽离，独立维护）
-- 使用说明：
--   1. 全新建库：先执行 schema.sql（建表 + 初始数据），再执行本脚本一次；
--   2. 已有库：本脚本只执行一次即可；重复执行会报 Duplicate key name
--      错误（非幂等），可安全忽略，或改用文件末尾注释中的在线 DDL；
--   3. 本脚本不会被 Spring Boot 自动加载（dev 为 ddl-auto:update，
--      prod 为 ddl-auto:validate，与 schema.sql 一样均需手工执行）。
-- 已核对 schema.sql 现有索引均为单列索引，以下复合索引均不存在、无重复
-- ============================================================

-- 消息中心：按接收人+是否已读查未读列表/未读数（MessageService.list、countByReceiverIdAndIsRead）
ALTER TABLE sys_message ADD INDEX idx_sys_message_receiver_read (receiver_id, is_read);

-- 监理日记：按项目+日记日期的列表页日期范围查询与日记统计（diary-statistics）
ALTER TABLE biz_supervision_diary ADD INDEX idx_biz_supervision_diary_project_date (project_id, diary_date);

-- 监理日志：按项目+日志日期的列表页日期范围查询与日志统计（log-statistics）
ALTER TABLE biz_supervision_log ADD INDEX idx_biz_supervision_log_project_date (project_id, log_date);

-- 用户管理：按部门+状态筛选人员列表（UserService.list 同时传 deptId 与 status）
ALTER TABLE sys_user ADD INDEX idx_sys_user_dept_status (dept_id, status);

-- 安全日志：按项目+日志日期的列表页日期范围查询与安全日志统计（safety-log-statistics）
ALTER TABLE biz_safety_log ADD INDEX idx_biz_safety_log_project_date (project_id, log_date);

-- 日常巡检：按项目+巡检日期的列表页日期范围查询（daily-inspection 列表页）
ALTER TABLE biz_daily_inspection ADD INDEX idx_biz_daily_inspection_project_date (project_id, inspection_date);

-- 旁站巡检：按项目+旁站日期的列表页日期范围查询（sideline-inspection 列表页）
ALTER TABLE biz_sideline_inspection ADD INDEX idx_biz_sideline_inspection_project_date (project_id, inspection_date);

-- ------------------------------------------------------------
-- 线上已有库请使用以下 MySQL 8.0 在线 DDL（不锁表，业务无感）：
-- ALTER TABLE sys_message ADD INDEX idx_sys_message_receiver_read (receiver_id, is_read), ALGORITHM=INPLACE, LOCK=NONE;
-- ALTER TABLE biz_supervision_diary ADD INDEX idx_biz_supervision_diary_project_date (project_id, diary_date), ALGORITHM=INPLACE, LOCK=NONE;
-- ALTER TABLE biz_supervision_log ADD INDEX idx_biz_supervision_log_project_date (project_id, log_date), ALGORITHM=INPLACE, LOCK=NONE;
-- ALTER TABLE sys_user ADD INDEX idx_sys_user_dept_status (dept_id, status), ALGORITHM=INPLACE, LOCK=NONE;
-- ALTER TABLE biz_safety_log ADD INDEX idx_biz_safety_log_project_date (project_id, log_date), ALGORITHM=INPLACE, LOCK=NONE;
-- ALTER TABLE biz_daily_inspection ADD INDEX idx_biz_daily_inspection_project_date (project_id, inspection_date), ALGORITHM=INPLACE, LOCK=NONE;
-- ALTER TABLE biz_sideline_inspection ADD INDEX idx_biz_sideline_inspection_project_date (project_id, inspection_date), ALGORITHM=INPLACE, LOCK=NONE;
-- ------------------------------------------------------------
