# ============================================================================
#  监理现场管理系统 · 一键启动（本地演示模式，H2 内存数据库，无需 MySQL）
#    后端 Spring Boot ：http://127.0.0.1:8080/api
#    页面 + API 代理  ：http://localhost:8081/login.html（/api 转发到 8080）
#  由根目录"一键启动.bat"调用：bat 只含 ASCII 字符并执行 chcp 65001，
#  本文件以 UTF-8 显式读取后执行，不受 cmd 的 UTF-8 解析缺陷、执行策略和 BOM 影响。
#  可选环境变量：SUP_NO_BROWSER=1 时不自动打开浏览器（自动化测试用）
# ============================================================================
$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch {}
try { $Host.UI.RawUI.WindowTitle = '监理现场管理系统 - 一键启动（本地演示）' } catch {}

if ($env:SUP_ROOT) { $Root = $env:SUP_ROOT.TrimEnd('\') }
elseif ($PSScriptRoot) { $Root = Split-Path -Parent $PSScriptRoot }
else { $Root = (Get-Location).Path }

$JarName     = 'supervision-site-management-1.0.0.jar'
$BackendSig  = 'supervision-site-management'
$StaticSig   = 'e2e-static-server.js'
$BackendPort = 8080
$StaticPort  = 8081
$LoginUrl    = "http://localhost:$StaticPort/login.html"
$MaxWait     = 120
$LogDir      = Join-Path $Root 'logs'
$BackendLog  = Join-Path $LogDir 'e2e-backend.log'
$StaticLog   = Join-Path $LogDir 'e2e-static.log'
$CmdExe      = Join-Path $env:SystemRoot 'System32\cmd.exe'

function Fail([string[]]$Lines) {
    Write-Host ''
    foreach ($l in $Lines) { Write-Host $l -ForegroundColor Red }
    Write-Host ''
    Write-Host '启动未完成。处理完上述问题后，可再次双击"一键启动.bat"重试。'
    exit 1
}

function Show-LogTail([string]$Path) {
    if (Test-Path -LiteralPath $Path) {
        try { Get-Content -LiteralPath $Path -Tail 30 -Encoding UTF8 | ForEach-Object { Write-Host "    $_" } } catch {}
    }
}

# 返回 java.exe 的主版本号（1.8.0_x → 8，17.0.20 → 17），无法识别返回 0
function Get-JavaMajor([string]$Exe) {
    try {
        $psi = New-Object System.Diagnostics.ProcessStartInfo
        $psi.FileName = $Exe
        $psi.Arguments = '-version'
        $psi.UseShellExecute = $false
        $psi.RedirectStandardError = $true
        $psi.RedirectStandardOutput = $true
        $psi.CreateNoWindow = $true
        $p = [System.Diagnostics.Process]::Start($psi)
        $text = $p.StandardError.ReadToEnd() + $p.StandardOutput.ReadToEnd()
        [void]$p.WaitForExit(15000)
    } catch { return 0 }
    if ($text -notmatch 'version "([^"]+)"') { return 0 }
    $parts = $Matches[1] -split '[.\-+_]'
    $major = 0
    if ($parts[0] -eq '1' -and $parts.Count -gt 1) { [void][int]::TryParse($parts[1], [ref]$major) }
    else { [void][int]::TryParse($parts[0], [ref]$major) }
    return $major
}

# 返回监听指定端口的进程 PID，未监听返回 0
function Get-PortPid([int]$Port) {
    try {
        $c = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction Stop | Select-Object -First 1
        if ($c) { return [int]$c.OwningProcess }
    } catch {}
    $m = netstat -ano | Select-String -Pattern ("^\s*TCP\s+\S+:{0}\s+\S+\s+LISTENING\s+(\d+)" -f $Port) | Select-Object -First 1
    if ($m) { return [int]$m.Matches[0].Groups[1].Value }
    return 0
}

# 端口占用者判定：free / ours（本系统进程，可复用）/ other（其他程序）
function Get-PortState([int]$Port, [string]$Sig) {
    $procId = Get-PortPid $Port
    if ($procId -eq 0) { return @{ State = 'free'; Pid = 0 } }
    $cmdLine = ''
    try { $cmdLine = (Get-CimInstance Win32_Process -Filter "ProcessId=$procId").CommandLine } catch {}
    if ($cmdLine -and $cmdLine.Contains($Sig)) { return @{ State = 'ours'; Pid = $procId } }
    return @{ State = 'other'; Pid = $procId }
}

# 直连本机 HTTP（不走系统代理）；有任意 HTTP 响应即返回 @{Code;Body}，连接失败返回 $null
function Invoke-Local([string]$Url, [string]$Method = 'GET', [string]$Body = '', [int]$TimeoutMs = 3000, [string]$Token = '') {
    $resp = $null
    try {
        $req = [System.Net.HttpWebRequest]::Create($Url)
        $req.Method = $Method
        $req.Timeout = $TimeoutMs
        $req.ReadWriteTimeout = $TimeoutMs
        $req.Proxy = $null
        if ($Token) { $req.Headers['Authorization'] = "Bearer $Token" }
        if ($Body) {
            $bytes = [Text.Encoding]::UTF8.GetBytes($Body)
            $req.ContentType = 'application/json'
            $req.ContentLength = $bytes.Length
            $s = $req.GetRequestStream(); $s.Write($bytes, 0, $bytes.Length); $s.Close()
        }
        $resp = $req.GetResponse()
    } catch {
        $ex = $_.Exception
        while ($ex -and -not ($ex -is [System.Net.WebException])) { $ex = $ex.InnerException }
        if ($ex -and $ex.Response) { $resp = $ex.Response } else { return $null }
    }
    $text = ''
    try { $sr = New-Object IO.StreamReader($resp.GetResponseStream(), [Text.Encoding]::UTF8); $text = $sr.ReadToEnd(); $sr.Close() } catch {}
    $code = [int]$resp.StatusCode
    $resp.Close()
    return @{ Code = $code; Body = $text }
}

Write-Host '=============================================='
Write-Host '   监理现场管理系统 - 一键启动（本地演示）'
Write-Host '=============================================='
Write-Host ''

# ---------- 0. 定位后端工程目录（查找 子目录\backend\mvnw.cmd，不硬编码中文目录名） ----------
$BackendDir = $null
foreach ($d in (Get-ChildItem -LiteralPath $Root -Directory -ErrorAction SilentlyContinue)) {
    $probe = Join-Path $d.FullName 'backend\mvnw.cmd'
    if (Test-Path -LiteralPath $probe) { $BackendDir = Join-Path $d.FullName 'backend'; break }
}
if (-not $BackendDir) { Fail @('[错误] 未找到后端工程：项目根目录下应有 子目录\backend\mvnw.cmd', '       请确认"一键启动.bat"放在项目根目录，且后端文件夹未被移动或删除。') }
$StaticJs = Join-Path $Root $StaticSig
if (-not (Test-Path -LiteralPath $StaticJs)) { Fail @("[错误] 未找到页面服务脚本：$StaticJs") }
$Jar = Join-Path $BackendDir "target\$JarName"
if (-not (Test-Path -LiteralPath $LogDir)) { New-Item -ItemType Directory -Path $LogDir | Out-Null }

# ---------- 1. 检测 JDK：主版本号 >= 17 才选用；JAVA_HOME 合格则优先，否则多版本扫描取最优 ----------
Write-Host '[1/6] 正在检测 Java 环境（要求 JDK 17 及以上）...'
$JavaExe = $null; $JavaMajor = 0
if ($env:JAVA_HOME) {
    $exe = Join-Path $env:JAVA_HOME 'bin\java.exe'
    if (Test-Path -LiteralPath $exe) {
        $maj = Get-JavaMajor $exe
        if ($maj -ge 17) { $JavaExe = $exe; $JavaMajor = $maj }
        elseif ($maj -eq 0) { Write-Host "      JAVA_HOME 中的 java.exe 无法识别版本，改为自动扫描本机其他 JDK" -ForegroundColor Yellow }
        else { Write-Host "      JAVA_HOME 指向 Java $maj（低于 17），改为自动扫描本机其他 JDK" -ForegroundColor Yellow }
    }
}
# 从候选 java.exe 中选出主版本 >= 17 的最优者：完整 JDK（同目录有 javac.exe）优先，其次取版本高者
function Select-BestJava($Candidates, $Seen) {
    $best = $null; $bestScore = 0
    foreach ($c in $Candidates) {
        $key = $c.ToLowerInvariant()
        if ($Seen.ContainsKey($key)) { continue }
        $Seen[$key] = $true
        $maj = Get-JavaMajor $c
        if ($maj -lt 17) { continue }
        $score = $maj
        if (Test-Path -LiteralPath (Join-Path (Split-Path -Parent $c) 'javac.exe')) { $score += 1000 }
        if ($score -gt $bestScore) { $bestScore = $score; $best = @{ Exe = $c; Major = $maj } }
    }
    return $best
}
if (-not $JavaExe) {
    $seen = @{}; $cands = New-Object System.Collections.Generic.List[string]
    $vendorRoots = @("$env:USERPROFILE\.jdks", "$env:ProgramFiles\Java", "$env:ProgramFiles\Eclipse Adoptium",
                     "$env:ProgramFiles\Microsoft", "$env:ProgramFiles\Zulu", "$env:ProgramFiles\Amazon Corretto",
                     "$env:ProgramFiles\BellSoft", "$env:ProgramFiles\Semeru", "${env:ProgramFiles(x86)}\Java")
    foreach ($r in $vendorRoots) {
        if ($r -and (Test-Path -LiteralPath $r)) {
            Get-ChildItem -LiteralPath $r -Filter java.exe -File -Recurse -Depth 3 -ErrorAction SilentlyContinue | ForEach-Object { $cands.Add($_.FullName) }
        }
    }
    Get-Command java.exe -All -CommandType Application -ErrorAction SilentlyContinue | ForEach-Object { $cands.Add($_.Source) }
    $best = Select-BestJava $cands $seen

    # 系统中没有合格 JDK 时，才回退到编辑器插件自带的 JDK（插件升级后路径会变化，仅作兜底）
    if (-not $best) {
        $cands = New-Object System.Collections.Generic.List[string]
        foreach ($r in @("$env:USERPROFILE\.qoder\extensions", "$env:USERPROFILE\.vscode\extensions")) {
            if (Test-Path -LiteralPath $r) {
                Get-ChildItem -LiteralPath $r -Filter java.exe -File -Recurse -Depth 6 -ErrorAction SilentlyContinue | ForEach-Object { $cands.Add($_.FullName) }
            }
        }
        $best = Select-BestJava $cands $seen
    }
    if ($best) { $JavaExe = $best.Exe; $JavaMajor = $best.Major }
}
if (-not $JavaExe) {
    Fail @('[错误] 本机未找到可用的 JDK 17 或更高版本（已扫描 JAVA_HOME、常见安装目录与 PATH）。',
           '       解决：到 https://adoptium.net 下载安装"Temurin 17 或 21 (JDK)"，安装时勾选"设置 JAVA_HOME"，',
           '             装好后重新双击"一键启动.bat"。')
}
$JavaHome = Split-Path -Parent (Split-Path -Parent $JavaExe)
$env:JAVA_HOME = $JavaHome
$env:Path = (Join-Path $JavaHome 'bin') + ';' + $env:Path
Write-Host "      已选用 Java ${JavaMajor}：$JavaHome"

# ---------- 2. 检测 Node.js ----------
Write-Host '[2/6] 正在检测 Node.js...'
$NodeExe = $null
$cmd = Get-Command node.exe -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
if ($cmd) { $NodeExe = $cmd.Source }
elseif (Test-Path -LiteralPath "$env:ProgramFiles\nodejs\node.exe") { $NodeExe = "$env:ProgramFiles\nodejs\node.exe" }
if (-not $NodeExe) { Fail @('[错误] 未找到 Node.js。解决：到 https://nodejs.org 下载安装 LTS 版本后重新双击"一键启动.bat"。') }
Write-Host "      已找到 Node.js：$NodeExe"

# ---------- 3. 检查后端程序包，缺失时自动构建 ----------
Write-Host '[3/6] 正在检查后端程序包...'
$needsBuild = -not (Test-Path -LiteralPath $Jar)
if (-not $needsBuild) {
    $jarTime = (Get-Item -LiteralPath $Jar).LastWriteTimeUtc
    $inputs = @(Get-Item -LiteralPath (Join-Path $BackendDir 'pom.xml')) + @(Get-ChildItem -LiteralPath (Join-Path $BackendDir 'src\main') -Recurse -File)
    $needsBuild = @($inputs | Where-Object { $_.LastWriteTimeUtc -gt $jarTime }).Count -gt 0
}
if ($needsBuild) {
    $running = Get-PortState $BackendPort $BackendSig
    if ($running.State -eq 'ours') {
        Fail @('[提示] 后端代码已更新，当前仍运行旧版本。', '       请先双击“停止服务.bat”，再双击“一键启动.bat”；演示数据会重置。')
    }
    Write-Host '      程序包缺失或代码已更新，开始自动构建（首次约 3-5 分钟，请耐心等待）...'
    $mvnArgs = @('clean', 'package', '-DskipTests')
    $m2 = Join-Path $Root '.m2-cache'
    if (Test-Path -LiteralPath $m2) { $mvnArgs += "-Dmaven.repo.local=$m2" }
    Push-Location -LiteralPath $BackendDir
    $buildExit = 1
    try { & (Join-Path $BackendDir 'mvnw.cmd') @mvnArgs; $buildExit = $LASTEXITCODE } catch {} finally { Pop-Location }
    if ($buildExit -ne 0 -or -not (Test-Path -LiteralPath $Jar)) {
        Fail @("[错误] 自动构建失败，未生成 $JarName。",
               '       请向上翻看 Maven 报错；常见原因：网络无法下载依赖、磁盘空间不足、JDK 不完整。')
    }
}
Write-Host "      程序包就绪：$JarName"

# ---------- 4. 启动后端（8080）与页面服务（8081），端口已被本系统占用则复用 ----------
Write-Host '[4/6] 正在启动服务...'
$backendProc = $null
$ps = Get-PortState $BackendPort $BackendSig
if ($ps.State -eq 'other') {
    Fail @("[错误] 端口 $BackendPort 已被其他程序占用（PID=$($ps.Pid)），不是本系统后端。",
           "       解决：在任务管理器""详细信息""页按 PID 结束该进程，或执行： taskkill /pid $($ps.Pid) /f")
}
if ($ps.State -eq 'ours') {
    Write-Host "      后端已在运行（PID=$($ps.Pid)），直接复用"
} else {
    Write-Host "      启动后端服务：端口 $BackendPort，H2 内存数据库"
    Remove-Item -LiteralPath $BackendLog -Force -ErrorAction SilentlyContinue
    $a = '/c title 监理后端-{0}（关闭此窗口即停止后端） & "{1}" -Dfile.encoding=UTF-8 -jar "{2}" --spring.profiles.active=e2e --server.port={0} > "{3}" 2>&1' -f $BackendPort, $JavaExe, $Jar, $BackendLog
    $backendProc = Start-Process -FilePath $CmdExe -ArgumentList $a -WorkingDirectory $BackendDir -WindowStyle Minimized -PassThru
}
$ps = Get-PortState $StaticPort $StaticSig
if ($ps.State -eq 'other') {
    Fail @("[错误] 端口 $StaticPort 已被其他程序占用（PID=$($ps.Pid)），不是本系统页面服务。",
           "       解决：在任务管理器""详细信息""页按 PID 结束该进程，或执行： taskkill /pid $($ps.Pid) /f")
}
if ($ps.State -eq 'ours') {
    Write-Host "      页面服务已在运行（PID=$($ps.Pid)），直接复用"
} else {
    Write-Host "      启动页面服务：端口 $StaticPort"
    $a = '/c title 监理页面-{0}（关闭此窗口即停止页面服务） & "{1}" "{2}" > "{3}" 2>&1' -f $StaticPort, $NodeExe, $StaticJs, $StaticLog
    [void](Start-Process -FilePath $CmdExe -ArgumentList $a -WorkingDirectory $Root -WindowStyle Minimized -PassThru)
}

# ---------- 5. 等待就绪：后端探活 + 页面探活 + admin 登录自检 ----------
Write-Host "[5/6] 正在等待服务就绪（最长 $MaxWait 秒，首次启动约 20-40 秒）" -NoNewline
$sw = [Diagnostics.Stopwatch]::StartNew()
while ($true) {
    # /api/auth/info 未登录返回 401，有任何 HTTP 响应即说明后端已就绪
    if (Invoke-Local "http://127.0.0.1:$BackendPort/api/auth/info" -TimeoutMs 2000) { break }
    if ($backendProc) {
        $failed = $false
        try { $failed = Select-String -LiteralPath $BackendLog -SimpleMatch 'APPLICATION FAILED TO START' -Quiet -ErrorAction Stop } catch {}
        if ($failed -or $backendProc.HasExited) {
            Write-Host ''
            Write-Host "[错误] 后端启动失败，日志最后 30 行如下（完整日志：$BackendLog）：" -ForegroundColor Red
            Show-LogTail $BackendLog
            Fail @('[错误] 后端未能启动，请根据上方日志处理（常见：端口冲突、JDK 不完整、程序包损坏）。')
        }
    }
    if ($sw.Elapsed.TotalSeconds -ge $MaxWait) {
        Write-Host ''
        Write-Host "[错误] 等待 $MaxWait 秒后端仍未就绪，日志最后 30 行如下（完整日志：$BackendLog）：" -ForegroundColor Red
        Show-LogTail $BackendLog
        Fail @('[错误] 后端启动超时。可关闭其他占用内存的程序后重试。')
    }
    Write-Host '.' -NoNewline
    Start-Sleep -Seconds 2
}
Write-Host ''
Write-Host "      [OK] 后端已就绪：http://127.0.0.1:$BackendPort/api" -ForegroundColor Green

$staticOk = $false
for ($i = 0; $i -lt 20; $i++) {
    $r = Invoke-Local "http://127.0.0.1:$StaticPort/login.html" -TimeoutMs 2000
    if ($r -and $r.Code -eq 200) { $staticOk = $true; break }
    Start-Sleep -Seconds 1
}
if (-not $staticOk) {
    Write-Host "[错误] 页面服务未能启动，日志如下（$StaticLog）：" -ForegroundColor Red
    Show-LogTail $StaticLog
    Fail @('[错误] 页面服务启动失败。')
}
Write-Host "      [OK] 页面服务已就绪：$LoginUrl" -ForegroundColor Green

$r = Invoke-Local "http://127.0.0.1:$StaticPort/api/auth/login" -Method 'POST' -Body '{"username":"admin","password":"123456"}' -TimeoutMs 10000
try {
    $login = $r.Body | ConvertFrom-Json
    if ($login.code -ne 200 -or -not $login.data.token -or $login.data.roles -notcontains 'admin') { throw 'admin 登录或角色异常' }
    $token = $login.data.token
    $roleResult = Invoke-Local "http://127.0.0.1:$StaticPort/api/roles?pageSize=200" -Token $token
    $rolePage = $roleResult.Body | ConvertFrom-Json
    $admins = @($rolePage.data.list | Where-Object { $_.code -eq 'admin' })
    $permResult = Invoke-Local "http://127.0.0.1:$StaticPort/api/permissions?pageSize=1" -Token $token
    $perms = $permResult.Body | ConvertFrom-Json
    if ($admins.Count -eq 0 -or $perms.code -ne 200 -or $perms.data.total -lt 31) { throw '管理员或权限目录缺失' }
    foreach ($admin in $admins) {
        $boundResult = Invoke-Local "http://127.0.0.1:$StaticPort/api/roles/$($admin.id)/permissions" -Token $token
        $bound = $boundResult.Body | ConvertFrom-Json
        if ($bound.code -ne 200 -or @($bound.data | Sort-Object -Unique).Count -ne $perms.data.total) { throw 'admin 权限绑定不完整' }
    }
    Write-Host "      [OK] admin 登录与全权限自检通过：$($perms.data.total)/$($perms.data.total)" -ForegroundColor Green
} catch {
    Fail @("[错误] admin 登录/全权限自检失败：$($_.Exception.Message)", "       请先停止服务再启动，并查看日志：$BackendLog")
} finally {
    if ($token) { Invoke-Local "http://127.0.0.1:$StaticPort/api/auth/logout" -Method 'POST' -Token $token | Out-Null }
}

# ---------- 6. 自动打开浏览器 ----------
if ($env:SUP_NO_BROWSER -eq '1') {
    Write-Host '[6/6] 已按 SUP_NO_BROWSER=1 跳过打开浏览器'
} else {
    Write-Host "[6/6] 正在打开浏览器：$LoginUrl"
    try { Start-Process $LoginUrl } catch { Write-Host '      [提示] 浏览器未能自动打开，请手动访问上述地址' -ForegroundColor Yellow }
}

Write-Host ''
Write-Host '=============================================='
Write-Host ' 启动完成！如浏览器未自动打开，请手动访问：'
Write-Host "   $LoginUrl"
Write-Host ' 登录账号：admin    密码：123456'
Write-Host ''
Write-Host ' 说明：本地演示模式使用 H2 内存数据库，'
Write-Host '       停止服务或重启后数据会重置为初始演示数据。'
Write-Host ' 用完后双击"停止服务.bat"关闭全部服务。'
Write-Host " 日志目录：$LogDir"
Write-Host '=============================================='
exit 0
