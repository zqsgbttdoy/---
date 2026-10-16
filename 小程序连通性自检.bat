@echo off
chcp 65001 >nul
title 监理系统 - 小程序连通性自检
echo ==============================================
echo   微信小程序连通性自检（登录/项目/今日巡检）
echo   前提：先运行《一键启动.bat》和《生成演示数据.bat》
echo ==============================================
where node >nul 2>&1
if errorlevel 1 (
    echo [错误] 未找到 Node.js，请先安装 Node 18 及以上版本后重试。
    echo        下载地址：https://nodejs.org/zh-cn （选 LTS 版，一路下一步安装）
    pause
    exit /b 1
)
echo.
echo 【一】代码编译预检（wxml/json/js/wxss）
node "%~dp0测试工作区\小程序编译预检.cjs"
echo.
echo 【二】网络与演示数据
node "%~dp0小程序连通性自检.cjs" %*
echo.
pause
