# 以独立进程运行根目录下的 .bat，stdout/stderr 重定向到文件（避免子进程继承管道导致挂起），pause 由输入文件自动应答
# 用法：powershell -NoProfile -File 测试工作区\运行批处理到文件.ps1 -Bat 一键启动.bat [-NoBrowser] [-TimeoutSec 200]
param([Parameter(Mandatory = $true)][string]$Bat, [switch]$NoBrowser, [int]$TimeoutSec = 200)
$root = Split-Path -Parent $PSScriptRoot
$tmp = Join-Path $env:TEMP 'supv-bat-test'
New-Item -ItemType Directory -Force -Path $tmp | Out-Null
$in = Join-Path $tmp 'in.txt'; $out = Join-Path $tmp 'out.txt'; $err = Join-Path $tmp 'err.txt'
[IO.File]::WriteAllText($in, "`r`n`r`n`r`n")
if ($NoBrowser) { $env:SUP_NO_BROWSER = '1' } else { Remove-Item Env:SUP_NO_BROWSER -ErrorAction SilentlyContinue }
$cmd = Join-Path $env:SystemRoot 'System32\cmd.exe'
$p = Start-Process -FilePath $cmd -ArgumentList @('/d', '/c', ('"' + (Join-Path $root $Bat) + '"')) -WorkingDirectory $root `
    -RedirectStandardInput $in -RedirectStandardOutput $out -RedirectStandardError $err -NoNewWindow -PassThru
$null = $p.Handle   # 缓存句柄，否则进程结束后 ExitCode 为空
if (-not $p.WaitForExit($TimeoutSec * 1000)) { Write-Output "[TIMEOUT after $TimeoutSec s]" }
$bytes = [IO.File]::ReadAllBytes($out)
try { $text = (New-Object Text.UTF8Encoding($false, $true)).GetString($bytes) } catch { $text = [Text.Encoding]::GetEncoding(936).GetString($bytes) }
Write-Output $text
$e = [IO.File]::ReadAllText($err)
if ($e.Trim()) { Write-Output "[stderr]"; Write-Output $e }
Write-Output "[exit=$($p.ExitCode)]"
