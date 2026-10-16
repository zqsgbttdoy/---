# 运维交接手册（监理现场管理系统）

> **面向对象：** 兼职/非全职运维人员。本手册为**速查手册**，遇到问题按目录直接跳到对应小节复制命令执行。
> **占位符：** 全文 `your-domain.com` 需替换为真实域名，`<ECS 公网 IP>` 替换为服务器公网 IP。
> **首次部署请先看：** [./ALIYUN_DEPLOYMENT.md](./ALIYUN_DEPLOYMENT.md) 第 4 节「完整部署流程」。本手册只管**已部署系统的日常维护**。

---

## 1. 系统概览

### 1.1 组件与端口

| 组件 | 端口 | 监听范围 | 进程/服务名 | 作用 |
| --- | --- | --- | --- | --- |
| Nginx | **80 / 443** | 公网 | `nginx` | 80 端口 301 跳转 HTTPS；443 托管前端静态页、反代 `/api/`、直出 `/api/uploads/` |
| 后端 Spring Boot | **8080** | 仅 `127.0.0.1` | `supervision` | REST API，context-path 为 `/api` |
| MySQL | **3306** | **仅本机 localhost** | `mysql` | 数据库 `supervision_db` |

> 安全底线：**3306 与 8080 绝不能出现在阿里云安全组的公网放行规则中**。

### 1.2 关键目录

| 路径 | 用途 | 权限 | 创建者 |
| --- | --- | --- | --- |
| `/opt/supervision/backend/` | 后端 jar（`supervision-site-management-1.0.0.jar`）+ `supervision.env` + `.backup.cnf` | 755 / env 文件 600 | `deploy/aliyun-init.sh` |
| `/opt/supervision/backend/logs/` | **应用日志 `app.log`**（服务工作目录 + logback 默认 `logs`） | 755 | 应用运行时自动创建 |
| `/opt/supervision/uploads/` | 上传文件根目录（`APP_UPLOAD_DIR`），按 `yyyyMMdd` 分子目录 | 755（Nginx 需读） | `aliyun-init.sh` |
| `/opt/supervision/static/` | 前端静态 HTML + `assets/app.js`（Nginx `root`） | 755 | `aliyun-init.sh` |
| `/opt/supervision/backups/` | 数据库备份 `.sql.gz` + 旧 jar `.bak` | **700** | `aliyun-init.sh` |
| `/opt/supervision/certs/` | SSL 证书 `fullchain.pem` / `privkey.pem` | **700** | `aliyun-init.sh` |
| `/opt/supervision/logs/` | 脚本日志（如 `backup.log`） | 755 | `aliyun-init.sh` |
| `/etc/systemd/system/supervision.service` | systemd 服务单元 | 644 | 手工安装 |
| `/etc/nginx/conf.d/supervision.conf` | Nginx 站点配置 | 644 | 手工安装 |

> **应用日志的确切位置：** systemd 单元的 `WorkingDirectory=/opt/supervision/backend`，logback 默认 `LOG_PATH=logs`，因此日志文件是 **`/opt/supervision/backend/logs/app.log`**（与 `deploy/deploy.sh` 失败提示中的路径一致）。
> 若希望统一到 `/opt/supervision/logs/`，在 `supervision.env` 中增加一行 `LOG_PATH=/opt/supervision/logs` 后重启服务即可。

### 1.3 运行身份

- 应用以系统用户 **`supervision`** 运行（`nologin`，不可交互登录）
- 数据库账号 **`supervision_app@localhost`**，仅有 `supervision_db` 库的 `SELECT/INSERT/UPDATE/DELETE`（**无 DDL 权限**，因此生产 `ddl-auto=validate`）

---

## 2. 服务启停

### 2.1 常用命令

| 目的 | 命令 |
| --- | --- |
| 启动后端 | `sudo systemctl start supervision` |
| 停止后端 | `sudo systemctl stop supervision` |
| 重启后端（改环境变量后必做） | `sudo systemctl restart supervision` |
| 查看后端状态 | `sudo systemctl status supervision` |
| 重载 Nginx（改配置/换证书后） | `sudo nginx -t && sudo systemctl reload nginx` |
| 重启 Nginx | `sudo systemctl restart nginx` |
| 重启 MySQL | `sudo systemctl restart mysql` |

### 2.2 开机自启确认

```bash
systemctl is-enabled supervision    # 期望输出 enabled
systemctl is-enabled nginx          # 期望输出 enabled
systemctl is-enabled mysql          # 期望输出 enabled

# 若某项为 disabled，执行：
sudo systemctl enable supervision
```

### 2.3 服务是否真的在跑（三重确认）

```bash
# 1. systemd 视角
systemctl is-active supervision                 # 期望 active

# 2. 端口视角
sudo ss -lntp | grep -E ':(80|443|8080|3306)'   # 8080 应为 127.0.0.1，3306 应为 127.0.0.1

# 3. 接口视角（返回任意 HTTP 状态码即端口已就绪，与 deploy.sh 探活逻辑相同）
curl -s -o /dev/null -w "%{http_code}\n" -X POST \
  -H "Content-Type: application/json" -d '{}' http://127.0.0.1:8080/api/auth/login
```

### 2.4 两种日志查看方式

```bash
# 方式一：systemd 日志（进程级：启动失败原因、JVM 崩溃、OOM 都在这里）
sudo journalctl -u supervision -f                    # 实时跟踪
sudo journalctl -u supervision -n 200 --no-pager     # 最近 200 行
sudo journalctl -u supervision --since "10 min ago"  # 最近 10 分钟
sudo journalctl -u supervision -p err --no-pager     # 只看 error 级

# 方式二：应用日志（业务级：含 traceId、慢请求 WARN、业务异常堆栈）
sudo tail -f /opt/supervision/backend/logs/app.log
sudo tail -n 300 /opt/supervision/backend/logs/app.log
```

| 场景 | 该看哪个 |
| --- | --- |
| 服务起不来 / 502 | `journalctl -u supervision`（启动校验失败、端口占用只在这里） |
| 接口报错 / 数据不对 / 慢 | `app.log`（有 traceId 可串联单次请求） |

> 应用日志滚动策略（`logback-spring.xml`）：按天 + 单文件 50MB 滚动，保留 **30 天**，总量上限 **2GB**，历史文件名为 `app.yyyy-MM-dd.N.log`。无需手工清理。

---

## 3. 排障方法

### 3.1 用 traceId 串联单次请求

每个 HTTP 请求进入时，`RequestLoggingInterceptor` 会生成 **8 位短 traceId** 写入 MDC，日志格式中的 `[%X{traceId}]` 输出该值。

日志行样例：

```
2026-07-30 10:23:41.512 [WARN ] [http-nio-8080-exec-3] c.s.i.RequestLoggingInterceptor [3f9a1c02] - 请求较慢: ...
```

排查流程：

```bash
# 1. 从报错日志中找到 traceId（方括号中的 8 位十六进制）
grep -n "不支持的文件类型" /opt/supervision/backend/logs/app.log | tail -5

# 2. 用 traceId 拉出这一次请求的全部日志（请求开始 → 业务日志 → 耗时与状态码）
grep "3f9a1c02" /opt/supervision/backend/logs/app.log

# 3. 跨滚动文件搜索
grep -r "3f9a1c02" /opt/supervision/backend/logs/
```

### 3.2 慢请求 WARN 的含义

`RequestLoggingInterceptor` 的慢请求阈值为 **1000ms**：请求耗时超过 1 秒时以 **WARN** 级别记录（含请求方法、URI、状态码、耗时），未超过则为 DEBUG（生产日志级别为 info，故不输出）。

```bash
# 找出所有慢请求
grep "WARN" /opt/supervision/backend/logs/app.log | grep -i "ms"

# 统计今天 WARN 数量（判断是否需要介入）
grep -c "WARN" /opt/supervision/backend/logs/app.log
```

判断口径：

| 现象 | 结论 |
| --- | --- |
| 偶发 1～2s，集中在导出/统计接口 | 正常（统计接口 `pageSize` 可达 2000） |
| 同一接口持续 > 3s | 需查慢查询日志：`sudo tail -f /var/log/mysql/slow.log` |
| 大面积慢请求 + CPU 高 | 可能是连接池耗尽或索引缺失，见 [./DEPLOYMENT.md](./DEPLOYMENT.md) 第 9 节索引升级 |

### 3.3 常见故障决策树

#### 故障 A：页面 502 Bad Gateway

```
502
 └─ systemctl is-active supervision
     ├─ inactive/failed → 后端进程未运行
     │    └─ sudo journalctl -u supervision -n 100 --no-pager
     │         ├─ 报「Could not resolve placeholder」/「必须配置」/「弱密码」
     │         │    → ★最常见根因：supervision.env 环境变量缺失或使用弱值，
     │         │      被 ProductionSafetyValidator 启动期快速失败拦截
     │         │    → 检查 8 个必填变量（见第 5 节），补齐后 systemctl restart supervision
     │         ├─ 报「Access denied for user」→ 数据库凭证错误，见故障 B
     │         ├─ 报「Schema-validation: missing table/column」→ ddl-auto=validate 校验失败，
     │         │    库结构与实体不一致，需补执行 schema.sql / schema-indexes.sql 差异部分
     │         ├─ 报「Port 8080 was already in use」→ sudo ss -lntp | grep 8080 找占用进程
     │         └─ 报「OutOfMemoryError」→ 调大 supervision.service 的 -Xmx 后 daemon-reload + restart
     └─ active → 后端在跑，问题在 Nginx 侧
          ├─ sudo nginx -t                        （配置语法）
          ├─ 确认 proxy_pass 为 http://127.0.0.1:8080/api/
          └─ sudo tail -f /var/log/nginx/error.log
```

必备命令：

```bash
sudo systemctl status supervision
sudo journalctl -u supervision -n 100 --no-pager
sudo tail -f /var/log/nginx/error.log
```

#### 故障 B：数据库连不上

```bash
# 1. MySQL 服务是否在跑
sudo systemctl status mysql

# 2. 凭证是否正确（用 supervision.env 中的账号密码手工登录验证）
mysql -u supervision_app -p -e "USE supervision_db; SHOW TABLES;" | head

# 3. 环境变量是否与实际密码一致
sudo grep SPRING_DATASOURCE /opt/supervision/backend/supervision.env

# 4. 若使用阿里云 RDS：检查白名单与内网连通性
mysql -h <RDS内网地址> -u supervision_app -p -e "SELECT 1;"
```

| 报错关键字 | 原因 | 处理 |
| --- | --- | --- |
| `Access denied for user` | 密码错 / 账号不存在 | 修正 `supervision.env` 中 `SPRING_DATASOURCE_PASSWORD`，或用 root 重置账号密码 |
| `Communications link failure` | MySQL 未启动 / 地址端口不通 | `systemctl start mysql`；RDS 场景检查白名单 |
| `Unknown database 'supervision_db'` | 库不存在 | 重跑 `deploy/init-database.sh` |
| `Too many connections` | 连接数不足 | MySQL `max_connections` 需 > Druid `max-active(50)`，建议 200 |

#### 故障 C：上传成功但访问图片/附件 404

```bash
# 1. 确认后端上传目录配置
sudo grep APP_UPLOAD_DIR /opt/supervision/backend/supervision.env

# 2. 确认 Nginx alias 与上面完全一致（含结尾斜杠）
sudo grep -A2 "location ^~ /api/uploads/" /etc/nginx/conf.d/supervision.conf

# 3. 确认物理文件真实存在（URL /api/uploads/20260730/x.jpg → 物理 <APP_UPLOAD_DIR>/20260730/x.jpg）
sudo ls -l /opt/supervision/uploads/$(date +%Y%m%d)/ | tail

# 4. 确认目录权限（Nginx 以 www-data 运行，需要读 + 目录可进入）
sudo ls -ld /opt/supervision /opt/supervision/uploads
# 期望：drwxr-xr-x supervision supervision

# 5. 修复权限
sudo chown -R supervision:supervision /opt/supervision/uploads
sudo chmod -R 755 /opt/supervision/uploads
```

**根因排序：① `APP_UPLOAD_DIR` 与 Nginx `alias` 不一致（换数据盘后最常见）→ ② 目录权限不足 → ③ 文件确实没落盘（看 app.log 是否有「创建上传目录失败」）。**

#### 故障 D：磁盘满（Disk Full）

```bash
# 1. 整体使用率
df -h

# 2. 定位大目录（逐层下钻）
sudo du -sh /opt/supervision/*
sudo du -sh /opt/supervision/backend/logs /opt/supervision/uploads /opt/supervision/backups
sudo du -sh /var/log/*

# 3. 找出最大的 20 个文件
sudo du -ah /opt/supervision | sort -rh | head -20
```

| 罪魁祸首 | 处理方式 |
| --- | --- |
| `backups/` 过大 | `backup.sh` 已自动清理 30 天前备份；旧 jar `.bak` 需手工清理：`sudo ls -lt /opt/supervision/backups/*.bak` 保留最近 3 个 |
| `logs/` 过大 | logback 已限总量 2GB / 保留 30 天；异常增长说明有循环报错，先 `grep ERROR` 定位 |
| `uploads/` 过大 | 业务数据不可删。扩容云盘或挂载数据盘（见 [./ALIYUN_DEPLOYMENT.md](./ALIYUN_DEPLOYMENT.md) 1.2 节） |
| `/var/log/journal` 过大 | `sudo journalctl --vacuum-time=15d` |
| MySQL binlog 过大 | `sudo mysql -u root -p -e "PURGE BINARY LOGS BEFORE DATE_SUB(NOW(), INTERVAL 7 DAY);"` |

#### 故障 E：证书过期（浏览器/小程序报证书错误）

```bash
# 1. 查看当前证书有效期
sudo openssl x509 -in /opt/supervision/certs/fullchain.pem -noout -dates
# 或从公网侧查看
echo | openssl s_client -servername your-domain.com -connect your-domain.com:443 2>/dev/null | openssl x509 -noout -dates

# 2A. 阿里云免费 DV 证书（有效期 3 个月）：控制台重新申请 → 下载 Nginx 格式 → 替换文件
sudo cp /tmp/新证书.pem /opt/supervision/certs/fullchain.pem
sudo cp /tmp/新证书.key /opt/supervision/certs/privkey.pem
sudo chmod 600 /opt/supervision/certs/*.pem
sudo nginx -t && sudo systemctl reload nginx

# 2B. Let's Encrypt（certbot）：
sudo certbot renew --force-renewal
sudo systemctl reload nginx
```

> 证书过期会导致**小程序全量接口失败**（微信强制 HTTPS 且校验证书），务必纳入每月巡检。

---

## 4. 数据库备份与恢复

### 4.1 备份：`backup.sh`

**首次使用前必须创建凭据文件**（脚本不接受命令行密码）：

```bash
sudo tee /opt/supervision/backend/.backup.cnf >/dev/null <<'EOF'
[client]
user=supervision_app
password=请替换为数据库专用账号密码
host=localhost
EOF

sudo chmod 600 /opt/supervision/backend/.backup.cnf
sudo chown supervision:supervision /opt/supervision/backend/.backup.cnf
```

手工执行一次备份：

```bash
sudo cp /tmp/deploy/backup.sh /opt/supervision/backend/backup.sh   # 首次安装脚本
sudo bash /opt/supervision/backend/backup.sh
```

脚本行为：`mysqldump --single-transaction`（不锁表，业务无感）→ `gzip` 压缩 → 输出到 `/opt/supervision/backups/supervision_db_<yyyyMMdd_HHmmss>.sql.gz` → 自动删除 **30 天**前的旧备份。

**配置定时任务（每日凌晨 2:00）：**

```bash
sudo crontab -e
```

加入以下一行（与 `backup.sh` 头部注释完全一致）：

```cron
0 2 * * * /bin/bash /opt/supervision/backend/backup.sh >> /opt/supervision/logs/backup.log 2>&1
```

验证：

```bash
sudo crontab -l                                  # 确认 cron 行存在
ls -lh /opt/supervision/backups/                 # 确认次日出现新的 .sql.gz
sudo tail -20 /opt/supervision/logs/backup.log   # 确认无报错
```

### 4.2 恢复：`restore.sh`

```bash
# 1. 强烈建议先停应用，避免恢复过程中写入
sudo systemctl stop supervision

# 2. 执行恢复（参数为备份文件路径，支持 .sql 与 .sql.gz）
bash /tmp/deploy/restore.sh /opt/supervision/backups/supervision_db_20260730_020000.sql.gz

# 3. 交互确认：必须输入大写 YES（其他任意输入即取消）
#    随后输入 MySQL root 密码（恢复涉及 DROP/CREATE TABLE，需要 DDL 权限）

# 4. 脚本结束会打印表数量（预期 24 张，含现场记录工作流 3 张新表）；重启应用
sudo systemctl start supervision
sudo systemctl status supervision
```

> ⚠️ **恢复会覆盖 `supervision_db` 现有数据且不可撤销。** 生产恢复前建议先对现库做一次即时备份：`sudo bash /opt/supervision/backend/backup.sh`。

### 4.3 每月恢复演练（强烈建议）

备份没验证过等于没有备份。建议每月一次演练，**在测试库上**进行，不影响生产：

```bash
# 1. 建演练库
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS supervision_db_drill DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 2. 把最新备份导入演练库
LATEST=$(ls -t /opt/supervision/backups/supervision_db_*.sql.gz | head -1)
gunzip -c "$LATEST" | mysql -u root -p --default-character-set=utf8mb4 supervision_db_drill

# 3. 校验表数量与关键表行数
mysql -u root -p -e "SELECT COUNT(*) AS tables FROM information_schema.tables WHERE table_schema='supervision_db_drill';"
mysql -u root -p -e "SELECT COUNT(*) FROM supervision_db_drill.sys_user;"

# 4. 演练完成后删除演练库
mysql -u root -p -e "DROP DATABASE supervision_db_drill;"
```

演练结论记录到运维台账：备份文件日期、表数量是否 24、耗时、是否可用。

---

## 5. 环境变量管理

### 5.1 八个必填变量（模板：`deploy/supervision.env.example`）

配置文件位置：`/opt/supervision/backend/supervision.env`（由 `supervision.service` 通过 `EnvironmentFile=` 加载）。变量名与 `backend/src/main/resources/application-prod.yml` 一一对应。

| # | 变量名 | 含义 | 取值要求 | 缺失/弱值后果 |
| --- | --- | --- | --- | --- |
| 1 | `SPRING_PROFILES_ACTIVE` | 激活生产配置 profile | 固定 `prod` | 不激活则用开发配置（`ddl-auto=update`、SQL 打印、CORS `*`），**严重风险** |
| 2 | `SPRING_DATASOURCE_USERNAME` | 数据库账号 | `supervision_app`，禁用 root | 启动失败 |
| 3 | `SPRING_DATASOURCE_PASSWORD` | 数据库密码 | 与 `init-database.sh` 设置的一致，禁弱密码 | 启动失败 / 弱密码被启动校验拦截 |
| 4 | `APP_JWT_SECRET` | JWT 签名密钥 | ≥32 字符，`openssl rand -base64 48` 生成，不得为开发默认值 | 启动失败；泄露则可伪造任意用户令牌 |
| 5 | `APP_CORS_ALLOWED_ORIGINS` | 允许的跨域来源 | 明确域名含协议，逗号分隔，**禁止 `*`** | 启动失败；配 `*` 被拦截 |
| 6 | `APP_DEFAULT_RESET_PASSWORD` | 管理员重置用户密码时下发的默认密码 | 强密码，禁止 `123456` | 启动失败 |
| 7 | `APP_UPLOAD_DIR` | 上传文件物理根目录 | 绝对路径，须与 Nginx `alias` 一致 | 默认落到工作目录 `uploads`，Nginx 取不到文件（404） |
| 8 | `APP_UPLOAD_SIGNING_SECRET` | 附件短时链接独立签名密钥 | ≥32 字符，`openssl rand -base64 48` 单独生成，**不得复用 `APP_JWT_SECRET`**；多实例部署须同一密钥 | 启动失败（`application-prod.yml` 以 `${APP_UPLOAD_SIGNING_SECRET}` 占位注入，缺失即 Spring 快速失败）；泄露可伪造附件访问签名 |

> DEPLOYMENT.md 中的默认值说明仅适用于开发环境，生产统一按本表执行。

两个可选变量：

| 变量名 | 何时需要 | 默认值 |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | 数据库不在本机 3306 或改用 RDS 时 | `jdbc:mysql://localhost:3306/supervision_db?...useSSL=true&serverTimezone=Asia/Shanghai` |
| `APP_UPLOAD_ALLOWED_EXTENSIONS` | 需收紧上传类型白名单时 | `jpg,jpeg,png,gif,bmp,pdf,doc,docx,xls,xlsx,ppt,pptx,txt,zip,rar` |

（另可加 `LOG_PATH` 自定义日志目录，见 1.2 节说明。）

### 5.2 变更流程（三步，缺一不可）

```bash
# 1. 编辑
sudo vi /opt/supervision/backend/supervision.env

# 2. 重启（EnvironmentFile 只在服务启动时加载，改完不重启不生效）
sudo systemctl restart supervision

# 3. 验证：状态 active + 日志无启动异常 + 接口连通
sudo systemctl status supervision
sudo journalctl -u supervision -n 50 --no-pager
curl -s -o /dev/null -w "%{http_code}\n" -X POST -H "Content-Type: application/json" \
  -d '{}' http://127.0.0.1:8080/api/auth/login
```

变更后务必确认权限未被改坏：

```bash
ls -l /opt/supervision/backend/supervision.env   # 期望 -rw------- supervision supervision
sudo chmod 600 /opt/supervision/backend/supervision.env
sudo chown supervision:supervision /opt/supervision/backend/supervision.env
```

### 5.3 密钥安全红线

| 禁止 | 原因 | 正确做法 |
| --- | --- | --- |
| ❌ 把密码/密钥写进命令行（如 `mysql -pXXXX`、`export APP_JWT_SECRET=xxx`） | 会留在 shell history 与进程列表 `ps aux` 中 | 用 `supervision.env`（600）与 `.backup.cnf`（600）；`init-database.sh` / `restore.sh` 已用 `read -s` 交互输入 |
| ❌ 把 `supervision.env` 提交到代码库 | 密钥永久留在 git 历史 | 只提交 `supervision.env.example` 模板 |
| ❌ 通过微信/邮件明文传递密钥 | 易泄露 | 使用密码管理器共享 |
| ❌ 多环境复用同一 `APP_JWT_SECRET` | 一处泄露全线沦陷 | 每环境独立生成 |
| ❌ `chmod 644` 图省事 | 服务器上任何用户可读密钥 | 固定 600 |

---

## 6. 日常巡检

### 6.1 每周巡检（约 5 分钟）

```bash
# ---------- 1. 服务健康 ----------
systemctl is-active supervision nginx mysql

# ---------- 2. WARN / ERROR 日志 ----------
grep -c "WARN"  /opt/supervision/backend/logs/app.log
grep -c "ERROR" /opt/supervision/backend/logs/app.log
grep "ERROR" /opt/supervision/backend/logs/app.log | tail -20

# ---------- 3. 磁盘空间（使用率 > 80% 需处理）----------
df -h
sudo du -sh /opt/supervision/*

# ---------- 4. 备份文件存在且非 0 字节 ----------
ls -lh /opt/supervision/backups/ | tail -8
find /opt/supervision/backups -name "supervision_db_*.sql.gz" -mtime -2 -size +1k
# ↑ 应至少输出一条：说明最近 2 天内产生了大于 1KB 的有效备份

# ---------- 5. 内存与负载 ----------
free -h
uptime
```

| 检查项 | 合格标准 | 不合格时 |
| --- | --- | --- |
| 三个服务状态 | 均为 `active` | 见第 3 节排障 |
| ERROR 日志 | 无新增或可解释 | 用 traceId 追查（3.1） |
| 磁盘使用率 | < 80% | 见故障 D |
| 昨日备份 | 存在且 > 1KB | 检查 cron 与 `.backup.cnf`（4.1） |
| 内存剩余 | > 10% | 调 `-Xmx` 或 `innodb_buffer_pool_size` |

### 6.2 每月巡检（约 30 分钟）

```bash
# ---------- 1. 数据库恢复演练（见 4.3）----------

# ---------- 2. SSL 证书有效期（剩余 < 20 天须立即续期）----------
sudo openssl x509 -in /opt/supervision/certs/fullchain.pem -noout -dates
echo | openssl s_client -servername your-domain.com -connect your-domain.com:443 2>/dev/null \
  | openssl x509 -noout -enddate

# ---------- 3. 系统安全更新 ----------
sudo apt-get update
apt list --upgradable
sudo apt-get upgrade -y
# 若内核更新，安排业务低峰期重启： sudo reboot

# ---------- 4. 登录安全审计 ----------
sudo fail2ban-client status sshd          # 查看被封禁 IP
sudo lastb | head -20                     # 失败登录记录
sudo last | head -20                      # 成功登录记录

# ---------- 5. 慢查询回顾 ----------
sudo tail -100 /var/log/mysql/slow.log

# ---------- 6. 备份体积趋势（判断数据增长与磁盘规划）----------
du -sh /opt/supervision/backups
ls -lh /opt/supervision/backups/ | tail -30
```

### 6.3 巡检记录模板

| 日期 | 巡检人 | 服务状态 | ERROR 数 | 磁盘% | 备份 | 证书剩余天数 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 2026-07-30 | | ✅ | 0 | 42% | ✅ | 68 | |

---

## 7. 升级发版流程

| 步骤 | 操作 | 命令 |
| --- | --- | --- |
| 1 | 本地出包 | 在 `补后端\backend` 目录执行 `mvn clean package -DskipTests`；若仓库根目录提供了 `打包发布.bat`，双击运行即可一键出包 |
| 2 | 确认产物 | `backend\target\supervision-site-management-1.0.0.jar` |
| 3 | 上传到服务器 | `scp backend\target\supervision-site-management-1.0.0.jar root@<ECS 公网 IP>:/tmp/` |
| 4 | 前端有改动时同步上传 | `scp *.html root@<ECS 公网 IP>:/tmp/` → `sudo cp /tmp/*.html /opt/supervision/static/` |
| 5 | 发版前手工备份数据库 | `sudo bash /opt/supervision/backend/backup.sh` |
| 6 | 执行部署脚本 | `sudo bash /tmp/deploy/deploy.sh /tmp/supervision-site-management-1.0.0.jar` |
| 7 | 验证 | 见下方冒烟命令；并按 [./PRODUCTION_CHECKLIST.md](./PRODUCTION_CHECKLIST.md) 第六节「验证（冒烟测试）」执行 |

`deploy.sh` 自动完成：备份旧 jar 到 `/opt/supervision/backups/*.bak` → 替换 jar 并 `chown` → `systemctl restart supervision` → 探活最多 60 秒。失败时会直接打印排查命令与回滚命令。

发版后冒烟：

```bash
curl -I http://your-domain.com                       # 期望 301
curl -I https://your-domain.com/login.html           # 期望 200
curl -i https://your-domain.com/api/projects         # 期望 401（未带 token）
sudo tail -50 /opt/supervision/backend/logs/app.log  # 确认无启动异常
```

### 7.1 回滚

```bash
# 1. 找到本次发版前备份的 jar（时间戳最新的 .bak）
ls -lt /opt/supervision/backups/*.bak | head -3

# 2. 覆盖回滚并重启
sudo cp /opt/supervision/backups/supervision-site-management-1.0.0.jar.<时间戳>.bak \
        /opt/supervision/backend/supervision-site-management-1.0.0.jar
sudo chown supervision:supervision /opt/supervision/backend/supervision-site-management-1.0.0.jar
sudo systemctl restart supervision
sudo systemctl status supervision
```

> 若本次发版涉及数据库结构变更，回滚 jar 后还需按 [./DEPLOYMENT.md](./DEPLOYMENT.md) 第 11 节「回滚预案」处理库结构。

---

## 8. 附录

### 8.1 附录 A：可选开启 Druid 监控面板

项目使用 `druid-spring-boot-3-starter` 作为连接池，可开启内置监控页查看 SQL 执行统计与连接池状态。

> ⚠️ **默认不开启。** 该面板会暴露 SQL 语句与数据库连接信息，属于高价值攻击目标。若非排查性能问题，请保持关闭。

在 `supervision.env` 中追加以下变量（等价于 `application-prod.yml` 的 Druid 配置）：

```bash
# ---- Druid 监控面板（临时排障用，排查完请删除本段并重启）----
SPRING_DATASOURCE_DRUID_STAT_VIEW_SERVLET_ENABLED=true
SPRING_DATASOURCE_DRUID_STAT_VIEW_SERVLET_URL_PATTERN=/druid/*
SPRING_DATASOURCE_DRUID_STAT_VIEW_SERVLET_LOGIN_USERNAME=druidadmin
SPRING_DATASOURCE_DRUID_STAT_VIEW_SERVLET_LOGIN_PASSWORD=<强密码，勿用 admin/123456>
# 仅允许指定运维 IP 访问（多个用逗号分隔）
SPRING_DATASOURCE_DRUID_STAT_VIEW_SERVLET_ALLOW=127.0.0.1,203.0.113.10
SPRING_DATASOURCE_DRUID_STAT_VIEW_SERVLET_DENY=
SPRING_DATASOURCE_DRUID_WEB_STAT_FILTER_ENABLED=true
SPRING_DATASOURCE_DRUID_WEB_STAT_FILTER_URL_PATTERN=/*
SPRING_DATASOURCE_DRUID_WEB_STAT_FILTER_EXCLUSIONS=*.js,*.css,*.html,/druid/*
```

等价的 YAML 写法（如选择改配置文件而非环境变量）：

```yaml
spring:
  datasource:
    druid:
      stat-view-servlet:
        enabled: true
        url-pattern: /druid/*
        login-username: druidadmin
        login-password: <强密码>
        allow: 127.0.0.1,203.0.113.10
      web-stat-filter:
        enabled: true
        url-pattern: /*
        exclusions: "*.js,*.css,*.html,/druid/*"
```

**安全注意（四条必守）：**

1. **必须设强密码**，`login-username/password` 不得为 `admin/admin`；
2. **必须用 `allow` 限制来源 IP**，只放行运维固定 IP 与 `127.0.0.1`；
3. **不要通过公网 Nginx 暴露 `/druid/`** —— 推荐用 SSH 隧道本地访问，无需改 Nginx：
   ```bash
   # 本地执行，随后浏览器访问 http://127.0.0.1:8080/api/druid/
   ssh -L 8080:127.0.0.1:8080 root@<ECS 公网 IP>
   ```
4. **排障结束立即关闭**：删除上述变量后 `sudo systemctl restart supervision`。

### 8.2 附录 B：deploy/ 脚本速查

| 脚本 | 用法 | 权限 | 幂等性 |
| --- | --- | --- | --- |
| `aliyun-init.sh` | `sudo bash aliyun-init.sh` | root | 是（用户/目录已存在则跳过） |
| `init-database.sh` | `bash init-database.sh <schema.sql> <schema-indexes.sql>` | 需 MySQL root 密码 | **否**（索引脚本重复执行会报错） |
| `deploy.sh` | `sudo bash deploy.sh <新jar路径>` | root | 是 |
| `backup.sh` | `sudo bash backup.sh`（依赖 `.backup.cnf`） | 可读 cnf 的用户 | 是 |
| `restore.sh` | `bash restore.sh <备份文件>`，需输入 `YES` + root 密码 | 需 MySQL root 密码 | 是（覆盖式） |
| `supervision.service` | `cp` 到 `/etc/systemd/system/` 后 `daemon-reload` + `enable` | root | — |
| `nginx-supervision.conf` | 替换域名后 `cp` 到 `/etc/nginx/conf.d/supervision.conf` | root | — |
| `supervision.env.example` | `cp` 为 `/opt/supervision/backend/supervision.env` 并 `chmod 600` | root | — |

### 8.3 附录 C：紧急联络与升级路径

| 情形 | 处理级别 | 动作 |
| --- | --- | --- |
| 站点完全不可访问 | P1 | 按故障 A 决策树处理；15 分钟内无进展则回滚（7.1） |
| 数据疑似丢失/被误删 | P1 | **立即停止应用**防止继续写入 → 用最近备份在演练库验证 → 确认后按 4.2 恢复 |
| 单接口报错 | P2 | 用 traceId 追查（3.1），记录后交研发 |
| 慢但可用 | P3 | 记录慢请求 URI 与慢查询日志，纳入优化 |

---

## 相关文档

- [./ALIYUN_DEPLOYMENT.md](./ALIYUN_DEPLOYMENT.md) — 阿里云部署指南（ECS 选型、安全组、ICP 备案、SSL 证书、完整部署流程、MySQL 生产参数、RDS 替代方案、安全防护）
- [./WECHAT_MINIPROGRAM_GUIDE.md](./WECHAT_MINIPROGRAM_GUIDE.md) — 微信小程序适配指南（合法域名、示例代码、上传限制）
- [./DEPLOYMENT.md](./DEPLOYMENT.md) — 通用部署文档（第 8 节日志说明、第 9 节存量库索引升级、第 11 节回滚预案、第 12 节常见问题排查）
- [./PRODUCTION_CHECKLIST.md](./PRODUCTION_CHECKLIST.md) — 上线前检查清单（安全 / 配置 / 数据库 / 日志与监控 / Nginx / 冒烟验证）
