#!/bin/bash
# ==============================================================================
# aliyun-init.sh - 阿里云 ECS（Ubuntu 22.04）服务器初始化脚本
#
# 功能：
#   1. 安装 OpenJDK 17、Nginx、MySQL Server
#   2. 创建 supervision 系统用户（无登录 shell）
#   3. 创建 /opt/supervision 目录结构并授权
#
# 使用方法（root 或 sudo 执行）：
#   sudo bash aliyun-init.sh
#
# 注：CentOS / Alibaba Cloud Linux 的等价命令以注释形式附在每个步骤旁。
# ==============================================================================
set -e

if [ "$(id -u)" -ne 0 ]; then
    echo "错误：本脚本需要 root 权限，请使用 sudo bash aliyun-init.sh 执行"
    exit 1
fi

echo "=============================================="
echo "  监理现场管理系统 - 服务器初始化开始"
echo "=============================================="

# ------------------------------------------------------------------------------
# 步骤 1/5：更新软件包索引
# CentOS 等价命令： yum makecache
# ------------------------------------------------------------------------------
echo "[1/5] 正在更新软件包索引..."
apt-get update -y

# ------------------------------------------------------------------------------
# 步骤 2/5：安装 OpenJDK 17（仅运行时，无需 JDK 编译组件）
# CentOS 等价命令： yum install -y java-17-openjdk-headless
# ------------------------------------------------------------------------------
echo "[2/5] 正在安装 OpenJDK 17..."
apt-get install -y openjdk-17-jre-headless
java -version
echo "OpenJDK 17 安装完成"

# ------------------------------------------------------------------------------
# 步骤 3/5：安装 Nginx 与 MySQL Server
# CentOS 等价命令：
#   yum install -y nginx
#   yum install -y mysql-server   （或使用 MySQL 官方 yum 源安装 8.0）
#   systemctl enable --now nginx mysqld
# ------------------------------------------------------------------------------
echo "[3/5] 正在安装 Nginx 与 MySQL Server..."
apt-get install -y nginx mysql-server
systemctl enable nginx
systemctl enable mysql
systemctl start nginx
systemctl start mysql
echo "Nginx 与 MySQL 安装并启动完成"
echo "提示：首次安装 MySQL 后建议执行 mysql_secure_installation 加固（设置 root 密码、删除匿名用户等）"

# ------------------------------------------------------------------------------
# 步骤 4/5：创建 supervision 系统用户（无家目录登录需求，禁止交互登录）
# CentOS 等价命令： useradd --system --shell /usr/sbin/nologin supervision
# ------------------------------------------------------------------------------
echo "[4/5] 正在创建 supervision 系统用户..."
if id supervision >/dev/null 2>&1; then
    echo "用户 supervision 已存在，跳过创建"
else
    useradd --system --no-create-home --shell /usr/sbin/nologin supervision
    echo "用户 supervision 创建完成"
fi

# ------------------------------------------------------------------------------
# 步骤 5/5：创建目录结构并授权
#   /opt/supervision/backend  后端 jar + supervision.env
#   /opt/supervision/uploads  上传文件根目录（APP_UPLOAD_DIR）
#   /opt/supervision/logs     应用日志（logs/app.log）
#   /opt/supervision/backups  数据库备份与旧 jar 备份
#   /opt/supervision/static   前端静态 HTML（由 Nginx 托管）
#   /opt/supervision/certs    SSL 证书
# CentOS 等价命令与 Ubuntu 相同（mkdir/chown/chmod 为通用命令）
# ------------------------------------------------------------------------------
echo "[5/5] 正在创建 /opt/supervision 目录结构..."
mkdir -p /opt/supervision/backend
mkdir -p /opt/supervision/uploads
mkdir -p /opt/supervision/logs
mkdir -p /opt/supervision/backups
mkdir -p /opt/supervision/static
mkdir -p /opt/supervision/certs

chown -R supervision:supervision /opt/supervision
# 证书目录收紧权限，仅属主可读写进入
chmod 700 /opt/supervision/certs
# 备份目录收紧权限（内含数据库导出）
chmod 700 /opt/supervision/backups
# 静态目录需允许 Nginx（www-data）读取
chmod 755 /opt/supervision /opt/supervision/static
# 上传目录需允许 Nginx 读取（location /api/uploads/ 直接服务物理文件）
chmod 755 /opt/supervision/uploads

echo "目录结构创建并授权完成："
ls -l /opt/supervision

echo "=============================================="
echo "  初始化完成！后续步骤："
echo "  1. 执行 init-database.sh 初始化数据库"
echo "  2. 复制 supervision.env.example 为 /opt/supervision/backend/supervision.env 并填写"
echo "  3. 安装 supervision.service 与 nginx-supervision.conf"
echo "  4. 执行 deploy.sh 部署后端 jar"
echo "=============================================="
