#!/bin/bash
# ==============================================================================
# init-database.sh - 数据库初始化脚本
#
# 功能：
#   1. 交互式读取 MySQL root 密码与新专用账号密码（不在命令行明文出现）
#   2. 创建 supervision_db 数据库（utf8mb4）
#   3. 创建 supervision_app@localhost 专用账号，仅授权 supervision_db 库
#   4. 依次执行 schema.sql 与 schema-indexes.sql（索引脚本必须在建表后执行且只执行一次）
#   5. SHOW TABLES 计数校验（预期 21 张表）
#
# 使用方法：
#   bash init-database.sh <schema.sql路径> <schema-indexes.sql路径>
# 示例：
#   bash init-database.sh ./schema.sql ./schema-indexes.sql
# ==============================================================================
set -e

DB_NAME="supervision_db"
DB_USER="supervision_app"
EXPECTED_TABLES=24

# ---------------- 参数校验 ----------------
if [ $# -ne 2 ]; then
    echo "用法：bash init-database.sh <schema.sql路径> <schema-indexes.sql路径>"
    exit 1
fi

SCHEMA_SQL="$1"
INDEXES_SQL="$2"

if [ ! -f "$SCHEMA_SQL" ]; then
    echo "错误：找不到建表脚本 $SCHEMA_SQL"
    exit 1
fi
if [ ! -f "$INDEXES_SQL" ]; then
    echo "错误：找不到索引脚本 $INDEXES_SQL"
    exit 1
fi

# ---------------- 交互式读取密码（-s 不回显） ----------------
read -r -s -p "请输入 MySQL root 密码: " ROOT_PWD
echo ""
read -r -s -p "请输入新建专用账号 ${DB_USER} 的密码: " APP_PWD
echo ""
read -r -s -p "请再次输入专用账号密码确认: " APP_PWD2
echo ""

if [ "$APP_PWD" != "$APP_PWD2" ]; then
    echo "错误：两次输入的专用账号密码不一致，已退出"
    exit 1
fi
if [ -z "$APP_PWD" ]; then
    echo "错误：专用账号密码不能为空"
    exit 1
fi

# 通过环境变量传递 root 密码，避免出现在进程命令行中
export MYSQL_PWD="$ROOT_PWD"

# 对专用账号密码中的单引号做 SQL 转义（' → ''），防止打断 IDENTIFIED BY 语句
SAFE_APP_PWD=${APP_PWD//\'/\'\'}

# ---------------- 步骤 1：创建数据库与专用账号 ----------------
echo "[1/4] 正在创建数据库 ${DB_NAME}（utf8mb4）与专用账号 ${DB_USER}@localhost ..."
mysql -u root <<SQL
CREATE DATABASE IF NOT EXISTS ${DB_NAME} DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '${DB_USER}'@'localhost' IDENTIFIED BY '${SAFE_APP_PWD}';
GRANT SELECT, INSERT, UPDATE, DELETE ON ${DB_NAME}.* TO '${DB_USER}'@'localhost';
FLUSH PRIVILEGES;
SQL
echo "数据库与专用账号创建完成（仅授权 ${DB_NAME} 库的增删改查）"

# ---------------- 步骤 2：执行建表脚本 ----------------
echo "[2/4] 正在执行建表脚本 ${SCHEMA_SQL} ..."
mysql -u root --default-character-set=utf8mb4 "$DB_NAME" < "$SCHEMA_SQL"
echo "建表脚本执行完成"

# ---------------- 步骤 3：执行索引脚本（必须在建表后，且只执行一次） ----------------
echo "[3/4] 正在执行索引脚本 ${INDEXES_SQL} ..."
echo "注意：本脚本只应执行一次，重复执行会因索引已存在而报错"
mysql -u root --default-character-set=utf8mb4 "$DB_NAME" < "$INDEXES_SQL"
echo "索引脚本执行完成"

# ---------------- 步骤 4：SHOW TABLES 计数校验 ----------------
echo "[4/4] 正在校验表数量..."
TABLE_COUNT=$(mysql -u root -N -e "SHOW TABLES" "$DB_NAME" | wc -l)
echo "当前 ${DB_NAME} 库中共有 ${TABLE_COUNT} 张表（预期 ${EXPECTED_TABLES} 张）"
mysql -u root -e "SHOW TABLES" "$DB_NAME"

# 清理环境变量中的密码
unset MYSQL_PWD

if [ "$TABLE_COUNT" -eq "$EXPECTED_TABLES" ]; then
    echo "=============================================="
    echo "  数据库初始化成功！"
    echo "  请将 ${DB_USER} 的密码填入 supervision.env 的 SPRING_DATASOURCE_PASSWORD"
    echo "=============================================="
else
    echo "=============================================="
    echo "  警告：表数量与预期不符（实际 ${TABLE_COUNT} / 预期 ${EXPECTED_TABLES}），请人工核查"
    echo "=============================================="
    exit 1
fi
