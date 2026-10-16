@echo off
chcp 65001 >nul
title 监理系统 - 生成演示数据
echo ==============================================
echo   监理现场管理系统 - 一键生成演示数据
echo   前提：先运行《一键启动.bat》并等登录页打开
echo ==============================================
where node >nul 2>&1
if errorlevel 1 (
    echo [错误] 未找到 Node.js，请先安装 Node 18 及以上版本后重试。
    echo        下载地址：https://nodejs.org/zh-cn （选 LTS 版，一路下一步安装）
    pause
    exit /b 1
)
node "%~dp0演示数据生成.cjs"
echo.
pause
