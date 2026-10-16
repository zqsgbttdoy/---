@echo off
chcp 65001 >nul
setlocal
title Supervision Site Management - Start (local demo)
rem ===========================================================================
rem  One-click start for local demo (backend :8080, pages :8081, H2 in-memory DB).
rem  KEEP THIS FILE PURE ASCII. cmd.exe mis-parses multi-byte UTF-8 text inside
rem  .bat files under "chcp 65001" (lines get split / skipped after call/goto),
rem  so all Chinese messages and logic live in scripts\start-local.ps1 (UTF-8),
rem  which is read explicitly as UTF-8 below (independent of BOM / policy).
rem ===========================================================================
set "SUP_ROOT=%~dp0"
set "SUP_PS1=%~dp0scripts\start-local.ps1"
set "PS_EXE=%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe"
if not exist "%PS_EXE%" set "PS_EXE=powershell.exe"
if not exist "%SUP_PS1%" goto NO_PS1

"%PS_EXE%" -NoProfile -ExecutionPolicy Bypass -Command "$c=[IO.File]::ReadAllText($env:SUP_PS1,[Text.Encoding]::UTF8); & ([ScriptBlock]::Create($c))"
set "RC=%ERRORLEVEL%"
echo.
pause
exit /b %RC%

:NO_PS1
echo [ERROR] Missing file: scripts\start-local.ps1
echo         Please keep the "scripts" folder next to this .bat file.
echo.
pause
exit /b 1
