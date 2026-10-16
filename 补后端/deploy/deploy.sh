#!/bin/bash
# ==============================================================================
# deploy.sh - 服务器端部署/升级脚本
#
# 功能：
#   1. 备份当前运行中的旧 jar 到 /opt/supervision/backups/（带时间戳）
#   2. 用新 jar 替换 /opt/supervision/backend/ 下的运行 jar
#   3. systemctl restart supervision
#   4. 轮询探活（最多 60 秒），失败时输出回滚指引
#
# 使用方法（root 或 sudo 执行）：
#   sudo bash deploy.sh <新jar路径>
# 示例：
#   sudo bash deploy.sh /tmp/supervision-site-management-1.0.0.jar
# ==============================================================================
set -e

APP_DIR="/opt/supervision/backend"
BACKUP_DIR="/opt/supervision/backups"
JAR_NAME="supervision-site-management-1.0.0.jar"
TARGET_JAR="${APP_DIR}/${JAR_NAME}"
# 探活地址：POST 空 body 到登录接口，只要有 HTTP 响应（400/401/200 均可）即认为进程存活
HEALTH_URL="http://127.0.0.1:8080/api/auth/login"
MAX_WAIT=60

# ---------------- 参数校验 ----------------
if [ $# -ne 1 ]; then
    echo "用法：sudo bash deploy.sh <新jar路径>"
    exit 1
fi

NEW_JAR="$1"
if [ ! -f "$NEW_JAR" ]; then
    echo "错误：找不到新 jar 文件 $NEW_JAR"
    exit 1
fi

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_JAR="${BACKUP_DIR}/${JAR_NAME}.${TIMESTAMP}.bak"

# ---------------- 步骤 1：备份旧 jar ----------------
if [ -f "$TARGET_JAR" ]; then
    echo "[1/4] 正在备份旧 jar 到 ${BACKUP_JAR} ..."
    mkdir -p "$BACKUP_DIR"
    cp "$TARGET_JAR" "$BACKUP_JAR"
    echo "旧 jar 备份完成"
else
    echo "[1/4] 未发现旧 jar（首次部署），跳过备份"
fi

# ---------------- 步骤 2：替换 jar ----------------
echo "[2/4] 正在部署新 jar ..."
cp "$NEW_JAR" "$TARGET_JAR"
chown supervision:supervision "$TARGET_JAR"
echo "新 jar 已就位：${TARGET_JAR}"

# ---------------- 步骤 3：重启服务 ----------------
echo "[3/4] 正在重启 supervision 服务 ..."
systemctl restart supervision

# ---------------- 步骤 4：探活等待（最多 60 秒） ----------------
echo "[4/4] 等待服务就绪（最多 ${MAX_WAIT} 秒）..."
ELAPSED=0
READY=0
while [ "$ELAPSED" -lt "$MAX_WAIT" ]; do
    # POST 空 body，只要拿到任意 HTTP 状态码（非 000）即认为端口已就绪
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST -H "Content-Type: application/json" -d '{}' --max-time 3 "$HEALTH_URL" || true)
    if [ -n "$HTTP_CODE" ] && [ "$HTTP_CODE" != "000" ]; then
        READY=1
        break
    fi
    sleep 2
    ELAPSED=$((ELAPSED + 2))
    echo "  已等待 ${ELAPSED} 秒，继续探测..."
done

if [ "$READY" -eq 1 ]; then
    echo "=============================================="
    echo "  部署成功！服务已就绪（探活返回 HTTP ${HTTP_CODE}）"
    echo "  查看状态： systemctl status supervision"
    echo "  查看日志： tail -f ${APP_DIR}/logs/app.log"
    echo "=============================================="
else
    echo "=============================================="
    echo "  部署失败：服务在 ${MAX_WAIT} 秒内未就绪！"
    echo ""
    echo "  排查建议："
    echo "    journalctl -u supervision -n 100 --no-pager"
    echo "    tail -n 200 ${APP_DIR}/logs/app.log"
    echo ""
    echo "  回滚步骤（恢复到本次备份的旧版本）："
    echo "    sudo cp ${BACKUP_JAR} ${TARGET_JAR}"
    echo "    sudo chown supervision:supervision ${TARGET_JAR}"
    echo "    sudo systemctl restart supervision"
    echo "=============================================="
    exit 1
fi
