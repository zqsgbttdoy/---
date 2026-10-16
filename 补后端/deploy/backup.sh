#!/bin/bash
# ==============================================================================
# backup.sh - 数据库每日备份脚本
#
# 功能：
#   1. mysqldump 导出 supervision_db（--single-transaction 不锁表）
#   2. gzip 压缩，文件名带日期时间戳
#   3. 自动清理 30 天前的旧备份
#
# 密码安全：
#   不在命令行传递密码，而是通过 --defaults-extra-file 读取
#   /opt/supervision/backend/.backup.cnf（my.cnf 格式，务必 chmod 600），模板如下：
#   ----------------------------------------------
#   [client]
#   user=supervision_app
#   password=请替换为数据库专用账号密码
#   host=localhost
#   ----------------------------------------------
#   创建后执行：
#     chmod 600 /opt/supervision/backend/.backup.cnf
#     chown supervision:supervision /opt/supervision/backend/.backup.cnf
#
# 定时任务（crontab -e 添加以下行，每日凌晨 2:00 执行）：
#   0 2 * * * /bin/bash /opt/supervision/backend/backup.sh >> /opt/supervision/logs/backup.log 2>&1
# ==============================================================================
set -e

DB_NAME="supervision_db"
BACKUP_DIR="/opt/supervision/backups"
CNF_FILE="/opt/supervision/backend/.backup.cnf"
KEEP_DAYS=30
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="${BACKUP_DIR}/${DB_NAME}_${TIMESTAMP}.sql"

# ---------------- 前置检查 ----------------
if [ ! -f "$CNF_FILE" ]; then
    echo "错误：找不到数据库凭据文件 ${CNF_FILE}"
    echo "请按脚本头部注释中的模板创建该文件，并 chmod 600"
    exit 1
fi

mkdir -p "$BACKUP_DIR"

# ---------------- 步骤 1：导出数据库 ----------------
echo "[1/3] 正在备份数据库 ${DB_NAME} 到 ${BACKUP_FILE} ..."
mysqldump --defaults-extra-file="$CNF_FILE" \
    --single-transaction \
    --skip-lock-tables \
    --no-tablespaces \
    --default-character-set=utf8mb4 \
    "$DB_NAME" > "$BACKUP_FILE"
echo "数据库导出完成"

# ---------------- 步骤 2：gzip 压缩 ----------------
echo "[2/3] 正在压缩备份文件 ..."
gzip -f "$BACKUP_FILE"
echo "压缩完成：${BACKUP_FILE}.gz （$(du -h "${BACKUP_FILE}.gz" | cut -f1)）"

# ---------------- 步骤 3：清理 30 天前旧备份 ----------------
echo "[3/3] 正在清理 ${KEEP_DAYS} 天前的旧备份 ..."
find "$BACKUP_DIR" -name "${DB_NAME}_*.sql.gz" -type f -mtime +"$KEEP_DAYS" -print -delete
echo "清理完成"

echo "=============================================="
echo "  备份成功：${BACKUP_FILE}.gz"
echo "  当前备份目录占用：$(du -sh "$BACKUP_DIR" | cut -f1)"
echo "=============================================="
