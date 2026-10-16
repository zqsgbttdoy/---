#!/bin/bash
# ==============================================================================
# restore.sh - 数据库恢复脚本
#
# 功能：
#   将指定备份文件（支持 .sql 与 .sql.gz）恢复到 supervision_db 数据库。
#   恢复涉及 DROP/CREATE TABLE，需使用 root 账号（交互式输入密码）。
#
# 使用方法：
#   bash restore.sh <备份文件路径>
# 示例：
#   bash restore.sh /opt/supervision/backups/supervision_db_20260730_020000.sql.gz
#
# 警告：恢复操作会覆盖 supervision_db 中的现有数据，不可撤销！
#       建议恢复前先停止应用： sudo systemctl stop supervision
# ==============================================================================
set -e

DB_NAME="supervision_db"

# ---------------- 参数校验 ----------------
if [ $# -ne 1 ]; then
    echo "用法：bash restore.sh <备份文件路径（.sql 或 .sql.gz）>"
    exit 1
fi

BACKUP_FILE="$1"
if [ ! -f "$BACKUP_FILE" ]; then
    echo "错误：找不到备份文件 $BACKUP_FILE"
    exit 1
fi

case "$BACKUP_FILE" in
    *.sql.gz|*.sql) ;;
    *)
        echo "错误：仅支持 .sql 或 .sql.gz 格式的备份文件"
        exit 1
        ;;
esac

# ---------------- 二次确认 ----------------
echo "=============================================="
echo "  警告：即将把以下备份恢复到数据库 ${DB_NAME}："
echo "    ${BACKUP_FILE}"
echo "  该操作会覆盖现有数据，且不可撤销！"
echo "  建议已提前执行： sudo systemctl stop supervision"
echo "=============================================="
read -r -p "确认恢复请输入大写 YES（其他任意输入取消）: " CONFIRM
if [ "$CONFIRM" != "YES" ]; then
    echo "已取消恢复操作"
    exit 0
fi

# ---------------- 交互式读取 root 密码 ----------------
read -r -s -p "请输入 MySQL root 密码: " ROOT_PWD
echo ""
export MYSQL_PWD="$ROOT_PWD"

# ---------------- 执行恢复 ----------------
echo "正在恢复数据库 ${DB_NAME}，请勿中断..."
case "$BACKUP_FILE" in
    *.sql.gz)
        gunzip -c "$BACKUP_FILE" | mysql -u root --default-character-set=utf8mb4 "$DB_NAME"
        ;;
    *.sql)
        mysql -u root --default-character-set=utf8mb4 "$DB_NAME" < "$BACKUP_FILE"
        ;;
esac

# ---------------- 校验 ----------------
TABLE_COUNT=$(mysql -u root -N -e "SHOW TABLES" "$DB_NAME" | wc -l)
unset MYSQL_PWD

echo "=============================================="
echo "  恢复完成！当前 ${DB_NAME} 库中共有 ${TABLE_COUNT} 张表"
echo "  若此前停止了应用，请重新启动： sudo systemctl start supervision"
echo "=============================================="
