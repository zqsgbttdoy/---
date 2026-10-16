# 监理现场管理系统 — 上线前检查清单（Production Checklist）

> 逐项勾选，全部通过后方可对外开放访问。配套文档见 [DEPLOYMENT.md](./DEPLOYMENT.md)。

---

## 一、安全

- [ ] **JWT 密钥长度与随机性**：`APP_JWT_SECRET` ≥32 字符且由随机生成器产生（如 `openssl rand -base64 48`），不等于开发默认密钥，也不得保留模板占位值「请替换…」（占位值也会被启动校验拦截）——检查环境变量值长度与来源。
- [ ] **附件签名密钥独立注入**：`APP_UPLOAD_SIGNING_SECRET` ≥32 字符、单独生成、不复用 JWT 密钥——缺失或复用会被启动拦截；多实例部署时各机器取值必须一致。
- [ ] **数据库专用账号非 root**：`SPRING_DATASOURCE_USERNAME` 为 `supervision_app` 等专用账号，仅授予 supervision_db 的 DML 权限——执行 `SHOW GRANTS FOR 'supervision_app'@'localhost';` 确认。
- [ ] **数据库强密码**：`SPRING_DATASOURCE_PASSWORD` 非 root/123456、长度 ≥8 且同时含字母与数字——不满足会被启动校验拦截，启动成功即侧面验证。
- [ ] **CORS 明确域名**：`APP_CORS_ALLOWED_ORIGINS` 为逗号分隔的明确域名，不含 `*`——检查环境变量值并用非法来源发起跨域请求应被拒绝。
- [ ] **数据库连接启用 SSL**：连接串含 `useSSL=true`（prod 默认）——检查 `SPRING_DATASOURCE_URL` 或确认未覆盖默认值。
- [ ] **默认重置密码已改**：`APP_DEFAULT_RESET_PASSWORD` 已设置且非 123456、长度 ≥8 且同时含字母与数字——管理员重置某用户密码后用新默认密码登录验证。
- [ ] **上传白名单确认**：`APP_UPLOAD_ALLOWED_EXTENSIONS` 符合业务需要——尝试上传 `.exe`/`.jsp` 等文件应被拒绝。

## 二、配置

- [ ] **prod profile 已激活**：`SPRING_PROFILES_ACTIVE=prod`——启动日志应显示 `The following 1 profile is active: "prod"`。
- [ ] **必填环境变量齐全**：APP_JWT_SECRET、APP_UPLOAD_SIGNING_SECRET、SPRING_DATASOURCE_USERNAME/PASSWORD、APP_CORS_ALLOWED_ORIGINS、APP_DEFAULT_RESET_PASSWORD、APP_UPLOAD_DIR 均已注入——应用能正常启动即通过（缺失会快速失败）。
- [ ] **ddl-auto=validate 生效**：生产禁止自动改表——启动日志无 `alter table`/`create table` 类 Hibernate DDL 输出。
- [ ] **show-sql=false 生效**：日志中不出现 SQL 语句明文——tail app.log 观察正常请求不打印 SQL。

## 三、数据库

- [ ] **schema.sql 已执行**：所有表已建、初始数据已导入——`SHOW TABLES;` 表数量与脚本一致（**当前为 24 张**，含 biz_project_member/biz_uploaded_file/biz_field_record_event 三张工作流表），且应用 validate 校验通过；存量库升级需另执行 schema-field-workflow.sql 增量脚本。
- [ ] **7 条复合索引已建**：sys_message、日记/日志/安全日志/巡检/旁站、sys_user 复合索引存在——`SHOW INDEX FROM sys_message;` 等能看到 `idx_*_receiver_read` 等索引名。
- [ ] **备份策略就绪**：每日 mysqldump 定时任务已配置且备份目录可写——手动触发一次并确认备份文件生成、可恢复。
- [ ] **字符集 utf8mb4**：库与表均为 utf8mb4/utf8mb4_unicode_ci——`SHOW CREATE DATABASE supervision_db;` 确认，并插入中文+emoji 验证。

## 四、日志与监控

- [ ] **磁盘余量充足**：日志盘余量 ≥10GB（日志总量上限 2GB + 上传文件增长）——`df -h` 检查 LOG_PATH 与 APP_UPLOAD_DIR 所在分区。
- [ ] **日志轮转生效**：logs/app.log 按天+50MB 滚动、保留 30 天——观察 `app.yyyy-MM-dd.N.log` 命名的历史文件生成。
- [ ] **慢请求 WARN 关注**：定期检索 WARN 级别日志并跟进——`grep WARN app.log` 纳入日常巡检，必要时按 traceId 追踪全链路。

## 五、Nginx

- [ ] **反代超时已配置**：proxy_connect_timeout/proxy_read_timeout 已按业务设置（普通 API connect 10s / read 120s，登录接口 read 30s）——`nginx -T | grep timeout` 确认。
- [ ] **client_max_body_size ≥100m**：与后端 max-request-size=100MB 匹配——上传接近 100MB 的文件不被 Nginx 413 拦截。
- [ ] **安全响应头已加**：含 Content-Security-Policy、X-Content-Type-Options: nosniff、X-Frame-Options——`curl -I http://<域名>/` 检查响应头。
- [ ] **隐藏版本号**：`server_tokens off;` 已配置——`curl -I` 的 Server 头不含 Nginx 版本号。
- [ ] **附件目录禁止匿名直出**：`location ^~ /api/uploads/` 与 `/uploads/` 均为 `return 403`——`curl -I https://<域名>/api/uploads/<任意路径>` 应返回 403，禁止改回 alias 直出。

## 六、验证（冒烟测试）

- [ ] **登录冒烟**：使用真实账号经前端登录成功并获得 token——浏览器登录后可进入首页。
- [ ] **列表冒烟**：监理日记/日志等列表接口正常分页返回（pageSize>200 会被钳制为 200）——前端翻页正常、无 500。
- [ ] **上传冒烟**：上传一张图片成功，返回的 `/api/uploads/...` 原路径仅作存库标识——直接访问该原路径应返回 403，登录态下前端预览通过 `/api/files/access` 受控签名链接正常显示。
- [ ] **退出冒烟**：退出登录后原 token 访问受保护接口返回 401——退出后刷新页面应跳回登录页。
- [ ] **prod 快速失败验证**：清空必填环境变量、仅设 `SPRING_PROFILES_ACTIVE=prod` 启动应直接失败并提示缺失项——在测试机验证后恢复正式配置。
