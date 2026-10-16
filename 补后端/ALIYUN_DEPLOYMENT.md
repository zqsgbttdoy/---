# 阿里云服务器部署指南（监理现场管理系统）

> ## ⚠️ 上线前统一替换占位符
>
> 本文全文使用两个占位符，**上线前必须全局替换为真实值**，否则命令与配置无法生效：
>
> | 占位符 | 含义 | 替换示例 |
> | --- | --- | --- |
> | `your-domain.com` | 已完成 ICP 备案的正式域名 | `supervision.example.com` |
> | `<ECS 公网 IP>` | 阿里云 ECS 实例的弹性公网 IP | `47.100.xx.xx` |
>
> 需要替换的位置至少包括：`deploy/nginx-supervision.conf`（`server_name`）、`deploy/supervision.env` 中的 `APP_CORS_ALLOWED_ORIGINS`、SSL 证书申请域名、微信小程序合法域名配置。

本文档面向**从零开始**在阿里云 ECS 上部署本系统的场景，每一步都对应 `deploy/` 目录下的真实脚本与配置文件。

相关文档：

- [./DEPLOYMENT.md](./DEPLOYMENT.md) — 通用部署文档（目录结构、环境变量清单、回滚预案、常见问题排查）
- [./PRODUCTION_CHECKLIST.md](./PRODUCTION_CHECKLIST.md) — 上线前检查清单（部署完成后逐项验收）
- [./WECHAT_MINIPROGRAM_GUIDE.md](./WECHAT_MINIPROGRAM_GUIDE.md) — 微信小程序适配指南
- [./OPS_HANDOVER.md](./OPS_HANDOVER.md) — 日常运维交接手册

---

## 1. ECS 实例选型

### 1.1 规格配置

| 项目 | 最低配置 | 推荐配置 | 说明 |
| --- | --- | --- | --- |
| CPU / 内存 | **2 核 4G** | **4 核 8G** | 最低配对应 `deploy/supervision.service` 中默认的 `-Xms1G -Xmx2G`；升级到 4 核 8G 后按该文件第 34 行注释改为 `-Xms2G -Xmx4G` |
| 系统盘 | 40G ESSD | 60G ESSD | 系统 + JDK + Nginx + MySQL + 应用日志 |
| 数据盘 | 可不挂 | 按上传量规划 | 挂载后将 `APP_UPLOAD_DIR` 指向数据盘路径（见 3.4） |
| 操作系统 | **Ubuntu 22.04 LTS 64 位** | 同左 | `deploy/aliyun-init.sh` 以 apt 为准；CentOS / Alibaba Cloud Linux 的等价 yum 命令已在脚本注释中给出 |
| 带宽 | 3 Mbps 固定 | 5 Mbps 或按量 | 上传/下载附件为主要带宽消耗 |

### 1.2 数据盘容量估算

上传文件存放在 `APP_UPLOAD_DIR`（默认 `/opt/supervision/uploads`）。估算公式：

```
所需容量 = 日均上传文件数 × 平均文件大小 × 保留天数 × 1.3（冗余系数）
```

例：日均 200 张现场照片、平均 2MB、保留 3 年 ≈ 200 × 2MB × 1095 × 1.3 ≈ 570GB。

如需挂载独立数据盘：

```bash
# 1. 控制台创建云盘并挂载到实例后，查看设备名
lsblk
# 2. 格式化并挂载到 /data
sudo mkfs.ext4 /dev/vdb
sudo mkdir -p /data
sudo mount /dev/vdb /data
# 3. 写入 /etc/fstab 实现开机自动挂载（UUID 方式更稳妥）
sudo blkid /dev/vdb
echo 'UUID=<上一步输出的UUID> /data ext4 defaults 0 2' | sudo tee -a /etc/fstab
# 4. 创建上传目录并授权给应用用户
sudo mkdir -p /data/supervision/uploads
sudo chown -R supervision:supervision /data/supervision
sudo chmod 755 /data/supervision /data/supervision/uploads
```

挂载数据盘后需同步修改一处：`supervision.env` 中 `APP_UPLOAD_DIR=/data/supervision/uploads`（上传目录仅供后端读写，Nginx 不对其做 alias 直出，附件统一走受控签名链接）。

---

## 2. 安全组与网络

### 2.1 安全组入方向规则

在 ECS 控制台 → 实例 → 安全组 → 配置规则 → 入方向，按下表配置：

| 序号 | 协议 | 端口范围 | 授权对象 | 优先级 | 说明 |
| --- | --- | --- | --- | --- | --- |
| 1 | TCP | 80/80 | `0.0.0.0/0` | 1 | HTTP，由 Nginx 301 跳转至 HTTPS |
| 2 | TCP | 443/443 | `0.0.0.0/0` | 1 | HTTPS，正式访问入口（小程序强制要求） |
| 3 | TCP | 22/22 | **仅运维人员固定源 IP**，如 `203.0.113.10/32` | 1 | SSH 运维通道，**严禁 `0.0.0.0/0`** |
| 4 | TCP | 3306/3306 | **不添加任何公网规则** | — | MySQL **绝不对公网开放**，仅本机 `localhost` 访问 |
| 5 | TCP | 8080/8080 | **不添加任何公网规则** | — | 后端仅由本机 Nginx 反向代理至 `127.0.0.1:8080` |

> 若运维人员为动态 IP，使用阿里云 **运维安全中心（堡垒机）** 或临时放行后立即回收，不要长期开放 22 端口到 `0.0.0.0/0`。

### 2.2 服务器本机防火墙（可选，与安全组双重防护）

```bash
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow from 203.0.113.10 to any port 22 proto tcp
sudo ufw enable
sudo ufw status verbose
```

---

## 3. 域名、备案与 SSL 证书

### 3.1 域名与 ICP 备案（小程序前置条件）

> **⏰ 请最早启动本步骤。** ICP 备案审批通常需要 **1～2 周**（工信部短信核验 + 阿里云初审 + 管局审核），是微信小程序配置合法域名的**硬性前置条件**：微信公众平台只接受已备案的域名，不接受 IP 地址与自定义端口。

| 步骤 | 操作位置 | 关键点 |
| --- | --- | --- |
| 1. 注册域名 | 阿里云域名控制台 | 实名认证需 1～3 天生效 |
| 2. 申请 ICP 备案 | 阿里云 ICP 代备案系统 | 需营业执照、法人身份证、网站负责人信息；服务器必须是**中国内地**的阿里云 ESS/ECS 并购买 ≥3 个月 |
| 3. 工信部短信核验 | 手机短信 | 提交后 24 小时内完成，逾期作废重提 |
| 4. 管局审核 | 各省通信管理局 | 一般 1～20 个工作日，以短信/邮件通知为准 |
| 5. 配置 DNS 解析 | 云解析 DNS | 添加 A 记录：主机记录 `@` 与 `www` → 记录值 `<ECS 公网 IP>`，TTL 600 |
| 6. 备案成功后 | 网站首页 | 在页面底部展示备案号（合规要求） |

验证解析是否生效：

```bash
dig +short your-domain.com
nslookup your-domain.com
```

### 3.2 方案 A：阿里云免费 DV 证书（推荐）

```
1. 进入「数字证书管理服务」控制台 → SSL 证书 → 免费证书 → 立即购买（0 元 / 20 张额度）
2. 证书管理 → 创建证书 → 填写域名 your-domain.com → 提交审核
3. 验证方式选「DNS 验证」，若域名托管在阿里云可勾选「自动添加 TXT 记录」
4. 审核通过（通常几分钟至数小时）后 → 下载 → 服务器类型选择 【Nginx / Tengine】
5. 解压得到两个文件： your-domain.com.pem（证书链）与 your-domain.com.key（私钥）
```

上传并按 `nginx-supervision.conf` 约定的文件名放置到 `/opt/supervision/certs/`：

```bash
# 本地（Windows PowerShell）上传
scp your-domain.com.pem your-domain.com.key root@<ECS 公网 IP>:/tmp/

# 服务器端就位（文件名必须为 fullchain.pem / privkey.pem）
sudo mv /tmp/your-domain.com.pem /opt/supervision/certs/fullchain.pem
sudo mv /tmp/your-domain.com.key /opt/supervision/certs/privkey.pem
sudo chown -R root:root /opt/supervision/certs
sudo chmod 700 /opt/supervision/certs
sudo chmod 600 /opt/supervision/certs/*.pem
```

> 目录 `/opt/supervision/certs` 由 `deploy/aliyun-init.sh` 步骤 5/5 创建并已 `chmod 700`。
> 阿里云免费 DV 证书有效期 **3 个月**，到期需重新申请并替换文件后 `sudo systemctl reload nginx`，请设置日历提醒。

### 3.3 方案 B：Let's Encrypt + certbot（备选，支持自动续期）

```bash
sudo apt-get install -y certbot python3-certbot-nginx

# 方式一：certbot 自动改写 Nginx 配置（会修改站点文件）
sudo certbot --nginx -d your-domain.com -d www.your-domain.com

# 方式二：只签发证书，不动 Nginx 配置（推荐，保持本项目模板不被改写）
sudo systemctl stop nginx
sudo certbot certonly --standalone -d your-domain.com
sudo systemctl start nginx
# 证书位置：/etc/letsencrypt/live/your-domain.com/{fullchain.pem,privkey.pem}
```

采用方式二时，将 `nginx-supervision.conf` 中证书路径改为软链，续期后无需再次拷贝：

```bash
sudo ln -sf /etc/letsencrypt/live/your-domain.com/fullchain.pem /opt/supervision/certs/fullchain.pem
sudo ln -sf /etc/letsencrypt/live/your-domain.com/privkey.pem  /opt/supervision/certs/privkey.pem
```

### 3.4 自动续期（cron）

```bash
# 测试续期流程（不真正签发）
sudo certbot renew --dry-run

# 编辑 root 的 crontab： sudo crontab -e
# 每天凌晨 3:30 检查续期，成功后重载 Nginx
30 3 * * * /usr/bin/certbot renew --quiet --deploy-hook "/usr/bin/systemctl reload nginx" >> /var/log/certbot-renew.log 2>&1
```

证书有效期自查命令见 [./OPS_HANDOVER.md](./OPS_HANDOVER.md) 的「日常巡检」章节。

---

## 4. 完整部署流程

以下 8 个步骤按顺序执行，每步给出可直接复制的命令。所有命令中的 `deploy/` 指本仓库 `补后端/deploy/` 目录。

### 步骤 1：上传部署文件到服务器

本地（Windows PowerShell）打包并上传：

```powershell
# 1. 本地构建后端 jar（在 补后端\backend 目录下执行）
mvn clean package -DskipTests
# 产物： backend\target\supervision-site-management-1.0.0.jar

# 2. 上传 jar、deploy 脚本、SQL 脚本、前端静态文件
scp backend\target\supervision-site-management-1.0.0.jar root@<ECS 公网 IP>:/tmp/
scp -r deploy root@<ECS 公网 IP>:/tmp/
scp backend\src\main\resources\schema.sql backend\src\main\resources\schema-indexes.sql root@<ECS 公网 IP>:/tmp/
scp *.html root@<ECS 公网 IP>:/tmp/static/
scp -r assets root@<ECS 公网 IP>:/tmp/static/
```

> 前端为纯静态 HTML，无需构建；`assets/app.js` 为公共脚本，必须一并上传。

### 步骤 2：初始化服务器环境 —— `aliyun-init.sh`

```bash
ssh root@<ECS 公网 IP>
cd /tmp/deploy
sudo bash aliyun-init.sh
```

该脚本完成 5 件事：更新 apt 索引 → 安装 `openjdk-17-jre-headless` → 安装并启用 `nginx` + `mysql-server` → 创建 `supervision` 系统用户（`nologin`）→ 创建并授权目录结构。

执行完成后按脚本提示加固 MySQL：

```bash
sudo mysql_secure_installation   # 设置 root 密码、移除匿名用户与远程 root 登录
```

验证目录已创建：

```bash
ls -l /opt/supervision
# 应包含 backend / uploads / logs / backups / static / certs 六个目录
```

### 步骤 3：初始化数据库 —— `init-database.sh`

脚本需要**两个位置参数**：建表脚本路径与索引脚本路径。

```bash
cd /tmp/deploy
bash init-database.sh /tmp/schema.sql /tmp/schema-indexes.sql
```

交互输入顺序（均不回显）：

1. MySQL root 密码
2. 新建专用账号 `supervision_app` 的密码（**请立即记录，步骤 4 要填入 `supervision.env`**）
3. 再次输入确认

脚本行为：创建 `supervision_db`（`utf8mb4` / `utf8mb4_unicode_ci`）→ 创建 `supervision_app@localhost` 并仅授予该库 `SELECT, INSERT, UPDATE, DELETE` → 执行 `schema.sql` → 执行 `schema-indexes.sql` → `SHOW TABLES` 计数校验（**当前预期 24 张表**，含现场记录工作流三表，不符则退出码 1）。

> ⚠️ `schema-indexes.sql` **只能执行一次**，重复执行会因索引已存在而报错。线上已有库的索引升级方式见 [./DEPLOYMENT.md](./DEPLOYMENT.md) 第 9 节「存量库索引升级」。
> ⚠️ 专用账号权限中**不含 DDL**，因此生产 `ddl-auto` 必须为 `validate`（`application-prod.yml` 已固定）。

### 步骤 4：配置环境变量文件 —— `supervision.env`

以 `deploy/supervision.env.example` 为模板（内含 8 个必填项与 2 个可选项的逐项注释）：

```bash
sudo cp /tmp/deploy/supervision.env.example /opt/supervision/backend/supervision.env

# 生成符合要求的 JWT 密钥（≥32 字符）
openssl rand -base64 48

sudo vi /opt/supervision/backend/supervision.env
```

必填 8 项（变量名与 `backend/src/main/resources/application-prod.yml` 完全对应）：

| 变量 | 取值要求 |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | 固定 `prod` |
| `SPRING_DATASOURCE_USERNAME` | `supervision_app`（禁用 root） |
| `SPRING_DATASOURCE_PASSWORD` | 步骤 3 设置的专用账号密码（禁弱密码） |
| `APP_JWT_SECRET` | `openssl rand -base64 48` 输出，≥32 字符且不得为开发默认值或模板占位值 |
| `APP_CORS_ALLOWED_ORIGINS` | `https://your-domain.com`（多个用逗号分隔，禁止 `*`） |
| `APP_DEFAULT_RESET_PASSWORD` | 强密码，禁止 `123456` |
| `APP_UPLOAD_DIR` | `/opt/supervision/uploads`（或数据盘路径） |
| `APP_UPLOAD_SIGNING_SECRET` | 单独执行 `openssl rand -base64 48` 生成，≥32 字符，**不得复用 APP_JWT_SECRET**；多实例必须同一密钥 |

**收紧权限（必做，否则密钥可被任意用户读取）：**

```bash
sudo chmod 600 /opt/supervision/backend/supervision.env
sudo chown supervision:supervision /opt/supervision/backend/supervision.env
ls -l /opt/supervision/backend/supervision.env   # 期望 -rw------- supervision supervision
```

> 上述变量缺失或使用弱值时，应用会被 `ProductionSafetyValidator` **启动期快速失败**拦截（这是 502 故障最常见的根因，见 [./OPS_HANDOVER.md](./OPS_HANDOVER.md)）。
> `SPRING_DATASOURCE_URL` 与 `APP_UPLOAD_ALLOWED_EXTENSIONS` 为可选项，默认值见 `application-prod.yml`。

### 步骤 5：安装 systemd 服务 —— `supervision.service`

```bash
sudo cp /tmp/deploy/supervision.service /etc/systemd/system/supervision.service
# 若为 4 核 8G，先按文件注释调整 JVM 参数： -Xms2G -Xmx4G
sudo vi /etc/systemd/system/supervision.service

sudo systemctl daemon-reload
sudo systemctl enable supervision      # 开机自启
```

此时先不 start —— jar 将在步骤 8 由 `deploy.sh` 就位并启动。若希望先行验证配置，可手动将 jar 拷入后启动：

```bash
sudo cp /tmp/supervision-site-management-1.0.0.jar /opt/supervision/backend/
sudo chown supervision:supervision /opt/supervision/backend/supervision-site-management-1.0.0.jar
sudo systemctl start supervision
sudo systemctl status supervision
```

### 步骤 6：部署前端静态文件

```bash
sudo cp /tmp/static/*.html /opt/supervision/static/
sudo mkdir -p /opt/supervision/static/assets
sudo cp /tmp/static/assets/app.js /opt/supervision/static/assets/
sudo chown -R supervision:supervision /opt/supervision/static
sudo chmod -R 755 /opt/supervision/static
ls /opt/supervision/static   # 确认 login.html 存在（Nginx index 首选项）
```

### 步骤 7：启用 Nginx 站点 —— `nginx-supervision.conf`

```bash
# 1. 替换域名占位符（一条 sed 完成全文替换）
sed -i 's/your-domain.com/实际域名/g' /tmp/deploy/nginx-supervision.conf

# 2. 安装站点配置
sudo cp /tmp/deploy/nginx-supervision.conf /etc/nginx/conf.d/supervision.conf
# Ubuntu 亦可： sudo cp ... /etc/nginx/sites-available/supervision.conf
#              sudo ln -s /etc/nginx/sites-available/supervision.conf /etc/nginx/sites-enabled/
#              sudo rm -f /etc/nginx/sites-enabled/default

# 3. 校验并重载
sudo nginx -t && sudo systemctl reload nginx
```

配置要点（与后端强一致，改动需同步）：

| 配置项 | 值 | 对应后端约定 |
| --- | --- | --- |
| `client_max_body_size` | `100m` | `application.yml` 的 `max-request-size: 100MB` |
| `location ^~ /api/uploads/` 与 `/uploads/` | `return 403`（封禁匿名直出） | 附件原路径仅作存库标识，预览走 `/api/files/access` 受控签名链接，后端再验项目权限 |
| `location ^~ /api/` → `proxy_pass` | `http://127.0.0.1:8080/api/` | `server.port: 8080` + `context-path: /api` |
| `root` | `/opt/supervision/static` | 前端静态目录 |
| `ssl_certificate` | `/opt/supervision/certs/fullchain.pem` | 步骤 3.2 放置的证书 |

### 步骤 8：部署后端 jar —— `deploy.sh`

```bash
sudo bash /tmp/deploy/deploy.sh /tmp/supervision-site-management-1.0.0.jar
```

脚本会：备份旧 jar 到 `/opt/supervision/backups/supervision-site-management-1.0.0.jar.<时间戳>.bak` → 替换新 jar 并 `chown supervision:supervision` → `systemctl restart supervision` → 轮询探活最多 60 秒（`POST http://127.0.0.1:8080/api/auth/login` 返回任意非 000 状态码即视为就绪）。失败时脚本会直接打印 `journalctl` 排查命令与三行回滚命令。

### 步骤 9：服务器安全加固 —— `enable-hardening.sh`

将此前需手工逐条执行的系统级加固（详见第 7 节）一键完成，降低运维遗漏：

```bash
cd /tmp/deploy
sudo bash enable-hardening.sh
```

脚本行为：安装并启用 `fail2ban`（jail.local：sshd + nginx-http-auth，maxretry=5）→ 启用 `unattended-upgrades` 自动安全更新（这两步幂等，可重复执行）→ **交互式**处理 SSH 禁密码。为避免把自己锁在门外，SSH 部分会先校验存在可用 `authorized_keys`、写入独立片段后以 `sshd -t` 语法自检通过才重启，失败自动回滚。

> ⚠️ 执行 SSH 加固前，务必**先用密钥成功登录一次**并保持当前终端不关，另开新终端验证密钥登录无误后再关闭旧会话。若已有带外访问、希望跳过交互：`sudo ENABLE_SSH_HARDENING=1 bash enable-hardening.sh`。

### 步骤 10：按检查清单验收

部署完成后打开 [./PRODUCTION_CHECKLIST.md](./PRODUCTION_CHECKLIST.md)，逐项确认六大类：**一、安全 / 二、配置 / 三、数据库 / 四、日志与监控 / 五、Nginx / 六、验证（冒烟测试）**。

快速自测：

```bash
# HTTP 应 301 跳转到 HTTPS
curl -I http://your-domain.com

# HTTPS 首页应返回 200
curl -I https://your-domain.com/login.html

# 登录接口应返回 JSON（用户名密码错误也算连通成功）
curl -s -X POST https://your-domain.com/api/auth/login \
  -H "Content-Type: application/json" -d '{"username":"admin","password":"wrong"}'

# 未带 token 访问受保护接口，应返回 401 与统一 JSON 响应体
curl -i https://your-domain.com/api/projects
```

---

## 5. MySQL 生产参数建议

编辑 `/etc/mysql/mysql.conf.d/mysqld.cnf`（CentOS 为 `/etc/my.cnf`），在 `[mysqld]` 段追加：

```ini
[mysqld]
# ---------- 字符集：必须 utf8mb4，与 init-database.sh 建库一致 ----------
character-set-server = utf8mb4
collation-server     = utf8mb4_unicode_ci

# ---------- InnoDB 缓冲池：建议为物理内存的 25%（本机同时跑 JVM 与 Nginx）----------
# 4G 内存 → 1G ； 8G 内存 → 2G
innodb_buffer_pool_size = 1G
innodb_log_file_size    = 256M
innodb_flush_log_at_trx_commit = 1

# ---------- 连接数：需大于 application-prod.yml 中 druid.max-active(50) ----------
max_connections = 200

# ---------- 慢查询日志：定位性能问题的第一手材料 ----------
slow_query_log      = 1
slow_query_log_file = /var/log/mysql/slow.log
long_query_time     = 1
log_queries_not_using_indexes = 0

# ---------- 只监听本机，杜绝公网暴露 ----------
bind-address = 127.0.0.1

# ---------- 时区与连接编码 ----------
default-time-zone = '+08:00'

[client]
default-character-set = utf8mb4
```

生效与验证：

```bash
sudo systemctl restart mysql
mysql -u root -p -e "SHOW VARIABLES LIKE 'character_set_server'; \
SHOW VARIABLES LIKE 'innodb_buffer_pool_size'; \
SHOW VARIABLES LIKE 'slow_query%'; \
SHOW VARIABLES LIKE 'bind_address';"

# 查看最近的慢查询
sudo tail -f /var/log/mysql/slow.log
```

> `innodb_buffer_pool_size` 不要超过内存的 50%：本机还需为 JVM（`-Xmx2G`）、Nginx 与操作系统留出空间。2 核 4G 机型建议 1G，4 核 8G 建议 2G。

---

## 6. 可选方案：使用阿里云 RDS 替代自建 MySQL

若数据量增长或需要高可用/自动备份，可改用 RDS MySQL 8.0。与自建方案的差异**仅有三点**：

| 环节 | 自建 MySQL | 使用 RDS |
| --- | --- | --- |
| `aliyun-init.sh` | 安装并启动本机 mysql-server | **跳过本机 MySQL**：可在脚本中注释 `apt-get install ... mysql-server` 与 `systemctl enable/start mysql` 两处，仅保留 JDK 与 Nginx 安装 |
| `supervision.service` | `After=mysql.service` / `Wants=mysql.service` | 建议改为仅 `After=network-online.target`（本机已无 mysql.service） |
| 数据库连接 | 使用默认 URL（`localhost:3306`） | **必须**在 `supervision.env` 中显式配置 `SPRING_DATASOURCE_URL` |
| 账号与建库 | `init-database.sh` 交互创建 | 在 RDS 控制台创建账号与 `supervision_db`（字符集选 utf8mb4），再用 `init-database.sh` 的 SQL 逻辑手工执行 `schema.sql` / `schema-indexes.sql` |
| 网络访问 | 仅 localhost | RDS **白名单**中加入 ECS 的**内网 IP**；使用内网地址连接（不走公网、免流量费、更低延迟） |
| 参数调优 | 改 `mysqld.cnf` | 在 RDS「参数设置」中调整（`character_set_server`、`slow_query_log`、`long_query_time`） |
| 备份 | `deploy/backup.sh` + crontab | RDS 自动备份（仍建议保留 `backup.sh` 做逻辑备份异地留存） |

RDS 场景下 `supervision.env` 的连接串示例（注意用内网地址、保留 `useSSL=true`）：

```bash
SPRING_DATASOURCE_URL="jdbc:mysql://rm-xxxxxx.mysql.rds.aliyuncs.com:3306/supervision_db?useUnicode=true&characterEncoding=utf-8&useSSL=true&serverTimezone=Asia/Shanghai"
SPRING_DATASOURCE_USERNAME=supervision_app
SPRING_DATASOURCE_PASSWORD=<RDS 账号密码>
```

连通性验证：

```bash
mysql -h rm-xxxxxx.mysql.rds.aliyuncs.com -u supervision_app -p -e "SELECT 1;"
```

---

## 7. 安全防护

> **✅ 一键执行（推荐）**：以下 7.1–7.2、7.5 的加固项已封装为脚本 `deploy/enable-hardening.sh`，执行 `sudo bash enable-hardening.sh` 即可自动完成 fail2ban 与自动安全更新；SSH 禁密码因有「把自己锁在门外」风险，脚本采用**交互式 + 前置 authorized_keys 校验 + `sshd -t` 语法自检**，确认可用密钥后再落地。下方手工命令保留作参考/排障用。

### 7.1 SSH 密钥登录并禁用密码

```bash
# 本地生成密钥对（若已有可跳过）
ssh-keygen -t ed25519 -C "ops@supervision"

# 上传公钥到服务器
ssh-copy-id -i ~/.ssh/id_ed25519.pub root@<ECS 公网 IP>

# 服务器端：确认密钥登录可用后再禁用密码登录
sudo vi /etc/ssh/sshd_config
#   PubkeyAuthentication yes
#   PasswordAuthentication no
#   PermitRootLogin prohibit-password
sudo systemctl restart sshd
```

> **务必在新开一个终端验证密钥登录成功后**，再关闭当前会话，避免把自己锁在门外。

### 7.2 fail2ban 防暴力破解

```bash
sudo apt-get install -y fail2ban
sudo tee /etc/fail2ban/jail.local >/dev/null <<'EOF'
[DEFAULT]
bantime  = 3600
findtime = 600
maxretry = 5
backend  = systemd

[sshd]
enabled = true
port    = 22

[nginx-http-auth]
enabled = true
EOF

sudo systemctl enable --now fail2ban
sudo fail2ban-client status sshd     # 查看当前封禁情况
```

### 7.3 云监控告警

在阿里云「云监控」中为该 ECS 创建报警规则，建议至少配置 5 条（通知方式：短信 + 邮件 + 钉钉机器人）：

| 监控项 | 阈值 | 说明 |
| --- | --- | --- |
| CPU 使用率 | 连续 5 分钟 > 80% | 可能存在慢查询或死循环 |
| 内存使用率 | 连续 5 分钟 > 85% | 考虑调整 `-Xmx` 或升配 |
| 磁盘使用率 | > 80% | 排查 `logs` / `uploads` / `backups` |
| 公网出流量 | 持续跑满带宽 | 大量附件下载或异常爬取 |
| 实例状态 | 停机/重启 | 立即介入 |

另建议启用「站点监控」拨测 `https://your-domain.com/login.html`，5 分钟一次，异常即告警。

### 7.4 最小权限原则清单

| 对象 | 权限约定 | 落地位置 |
| --- | --- | --- |
| 应用进程 | 以 `supervision` 系统用户运行，`nologin` 不可交互登录 | `aliyun-init.sh` 步骤 4、`supervision.service` 的 `User=/Group=` |
| 数据库账号 | `supervision_app@localhost` 仅 `SELECT/INSERT/UPDATE/DELETE` 且仅限 `supervision_db` | `init-database.sh` 的 `GRANT` 语句 |
| 环境变量文件 | `chmod 600` + 属主 `supervision` | 步骤 4 |
| 证书 / 备份目录 | `chmod 700` | `aliyun-init.sh` 步骤 5 |
| 静态 / 上传目录 | `chmod 755`（Nginx 需读） | `aliyun-init.sh` 步骤 5 |
| MySQL 监听 | `bind-address = 127.0.0.1` | 第 5 节 |
| 后端端口 | 仅 `127.0.0.1:8080`，不进安全组 | 2.1 节 |
| 密钥管理 | 禁止写入代码库、禁止出现在命令行历史 | `supervision.env.example` 头部说明 |

### 7.5 系统安全更新

```bash
sudo apt-get update && sudo apt-get upgrade -y
sudo apt-get install -y unattended-upgrades
sudo dpkg-reconfigure -plow unattended-upgrades   # 启用自动安全更新
```

---

## 8. 后续维护

日常启停、排障决策树、备份恢复、巡检清单与升级发版流程，全部见 [./OPS_HANDOVER.md](./OPS_HANDOVER.md)。
微信小程序对接（合法域名配置、示例代码、上传限制对照）见 [./WECHAT_MINIPROGRAM_GUIDE.md](./WECHAT_MINIPROGRAM_GUIDE.md)。
回滚预案与常见问题排查见 [./DEPLOYMENT.md](./DEPLOYMENT.md) 第 11、12 节。
