@echo off
chcp 65001 >nul
setlocal
title Supervision Site Management - Stop
rem ===========================================================================
rem  Stop local demo services (backend :8080, pages :8081).
rem  KEEP THIS FILE PURE ASCII (see note in the start script). Logic and
rem  Chinese messages live in scripts\stop-local.ps1, read explicitly as UTF-8.
rem ===========================================================================
set "SUP_ROOT=%~dp0"
set "SUP_PS1=%~dp0scripts\stop-local.ps1"
set "PS_EXE=%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe"
if not exist "%PS_EXE%" set "PS_EXE=powershell.exe"
if not exist "%SUP_PS1%" goto NO_PS1

"%PS_EXE%" -NoProfile -ExecutionPolicy Bypass -Command "$c=[IO.File]::ReadAllText($env:SUP_PS1,[Text.Encoding]::UTF8); & ([ScriptBlock]::Create($c))"
set "RC=%ERRORLEVEL%"
echo.
pause
exit /b %RC%

:NO_PS1
echo [ERROR] Missing file: scripts\stop-local.ps1
echo         Please keep the "scripts" folder next to this .bat file.
echo.
pause
exit /b 1
