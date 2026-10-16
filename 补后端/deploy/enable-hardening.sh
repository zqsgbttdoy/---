#!/bin/bash
# ==============================================================================
# enable-hardening.sh - 服务器安全加固一键脚本（配合 ALIYUN_DEPLOYMENT.md 第 7 节）
#
# 作用：把此前"仅文档化、需手工逐条执行"的安全加固项自动化，减少运维遗漏：
#   1. 安装并配置 fail2ban（sshd + nginx-http-auth，maxretry=5）—— 防暴力破解
#   2. 启用 unattended-upgrades —— 系统自动安全更新
#   3. （可选、交互式且带防锁死保护）SSH 改用密钥登录并禁用密码
#
# 使用方法（root 或 sudo 执行）：
#   sudo bash enable-hardening.sh
#
# 说明：
#   - 第 1、2 步为幂等操作，可安全重复执行；
#   - 第 3 步（SSH 禁密码）默认不自动执行，需现场交互确认，并会先校验
#     已存在可用的 authorized_keys、且修改后 sshd 配置语法自检通过才重启，
#     避免误操作把自己锁在服务器门外。若确需跳过交互（例如已有带外访问），
#     可执行：sudo ENABLE_SSH_HARDENING=1 bash enable-hardening.sh
#
# 注：CentOS / Alibaba Cloud Linux 的等价命令以注释形式附在相关步骤旁。
# ==============================================================================
set -e

if [ "$(id -u)" -ne 0 ]; then
    echo "错误：本脚本需要 root 权限，请使用 sudo bash enable-hardening.sh 执行"
    exit 1
fi

# 非交互执行判定：设置了 ENABLE_SSH_HARDENING=1 则跳过 SSH 交互确认
AUTO_SSH="${ENABLE_SSH_HARDENING:-0}"

echo "=============================================="
echo "  监理现场管理系统 - 服务器安全加固开始"
echo "=============================================="

# ------------------------------------------------------------------------------
# 步骤 1/3：fail2ban 防暴力破解
# CentOS 等价命令： yum install -y fail2ban ；服务名 fail2ban 相同
# ------------------------------------------------------------------------------
echo "[1/3] 正在安装并配置 fail2ban ..."
apt-get update -y
apt-get install -y fail2ban

# jail.local 覆盖默认配置（fail2ban 官方推荐改 .local 而非直接编辑 .conf，升级不丢失）
tee /etc/fail2ban/jail.local >/dev/null <<'EOF'
# 本文件由 enable-hardening.sh 生成，请勿直接编辑 jail.conf
[DEFAULT]
bantime  = 3600      # 命中后封禁 1 小时
findtime = 600       # 统计窗口 10 分钟
maxretry = 5         # 窗口内最多 5 次失败即封禁
backend  = systemd   # Ubuntu 22.04 用 systemd 日志后端

[sshd]
enabled = true
port    = 22

[nginx-http-auth]
enabled = true
EOF

systemctl enable fail2ban
systemctl restart fail2ban
echo "fail2ban 已启用。当前封禁情况： fail2ban-client status sshd"

# ------------------------------------------------------------------------------
# 步骤 2/3：系统自动安全更新（unattended-upgrades）
# CentOS 等价命令： yum install -y yum-cron && systemctl enable --now yum-cron
# ------------------------------------------------------------------------------
echo "[2/3] 正在启用 unattended-upgrades 自动安全更新 ..."
apt-get install -y unattended-upgrades
# 非交互生成配置（--plow 在脚本里会阻塞，这里直接写默认启用文件）
tee /etc/apt/apt.conf.d/20auto-upgrades >/dev/null <<'EOF'
APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";
EOF
echo "自动安全更新已启用（每日检查并安装安全补丁）"

# ------------------------------------------------------------------------------
# 步骤 3/3：SSH 密钥登录并禁用密码登录（交互式 + 防锁死保护）
# ------------------------------------------------------------------------------
echo "[3/3] SSH 登录加固（禁用密码登录）"

if [ "$AUTO_SSH" != "1" ]; then
    read -r -p "是否现在禁用 SSH 密码登录？请确认你已能用密钥登录再输入 yes（其他任意输入跳过）: " ANS
    if [ "$ANS" != "yes" ]; then
        echo "已跳过 SSH 加固。可稍后手动执行： sudo ENABLE_SSH_HARDENING=1 bash enable-hardening.sh"
        echo "==============================================";
        echo "  安全加固完成（fail2ban + 自动更新 已生效，SSH 未改动）"
        echo "=============================================="
        exit 0
    fi
fi

# 防锁死前置校验：确认至少存在一个非空的 authorized_keys，否则拒绝禁用密码
KEY_COUNT=$(find /root/.ssh /home/*/.ssh -maxdepth 1 -name authorized_keys -size +10c 2>/dev/null | wc -l)
if [ "$KEY_COUNT" -lt 1 ]; then
    echo "错误：未检测到任何可用的 ~/.ssh/authorized_keys（含公钥）。"
    echo "      为避免禁用密码后无法登录，已中止 SSH 加固。"
    echo "      请先用 ssh-copy-id 上传公钥，确认密钥登录成功后再重试。"
    exit 1
fi

echo "检测到 ${KEY_COUNT} 个 authorized_keys，继续加固 ..."
# Ubuntu 22.04 sshd_config 默认 Include /etc/ssh/sshd_config.d/*.conf，用独立片段覆盖，不动主配置
# 片段按文件名字典序加载且 sshd 取首次出现的值，云镜像自带的 50-cloud-init.conf 常含
# PasswordAuthentication yes，故必须用 01- 前缀抢先生效
rm -f /etc/ssh/sshd_config.d/90-supervision-hardening.conf
tee /etc/ssh/sshd_config.d/01-supervision-hardening.conf >/dev/null <<'EOF'
# 由 enable-hardening.sh 生成：仅允许密钥登录
PubkeyAuthentication yes
PasswordAuthentication no
PermitRootLogin prohibit-password
EOF

# 语法自检通过才重启，避免把 sshd 改坏导致连不上
if sshd -t; then
    systemctl restart sshd
    echo "SSH 已改为密钥登录并禁用密码。"
    echo "⚠️ 请【保持当前终端不关】，另开一个新终端用密钥登录验证成功后，再关闭本会话。"
else
    echo "错误：sshd 配置语法自检失败，已回滚（删除加固片段），未重启 sshd。"
    rm -f /etc/ssh/sshd_config.d/01-supervision-hardening.conf
    exit 1
fi

echo "=============================================="
echo "  安全加固全部完成！"
echo "  - fail2ban:      systemctl status fail2ban"
echo "  - 自动安全更新:  cat /etc/apt/apt.conf.d/20auto-upgrades"
echo "  - SSH 禁密码:    已通过 /etc/ssh/sshd_config.d/01-supervision-hardening.conf 生效（sshd -T 可核验）"
echo "=============================================="
