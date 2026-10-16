param([string]$BaseUrl = 'http://localhost:8081/api')
# 回归：会话隔离、管理员全权限，以及原部门/项目/软删除保护。
$ErrorActionPreference = 'Continue'
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch {}
$base = $BaseUrl.TrimEnd('/')
$runId = [Guid]::NewGuid().ToString('N').Substring(0, 10)
$phone = '13' + (Get-Random -Minimum 100000000 -Maximum 999999999)
$userName = "RB-User-$runId"
$pass = 0; $fail = 0

function Invoke-Api {
    param([string]$Method, [string]$Path, $Body = $null, [string]$Token = $null)
    $headers = @{}
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    try {
        $params = @{ Uri = "$base$Path"; Method = $Method; Headers = $headers; ContentType = 'application/json; charset=utf-8'; UseBasicParsing = $true; TimeoutSec = 20 }
        if ($null -ne $Body) { $params['Body'] = [Text.Encoding]::UTF8.GetBytes((ConvertTo-Json -InputObject $Body -Depth 6)) }
        $resp = Invoke-WebRequest @params
        $raw = [Text.Encoding]::UTF8.GetString($resp.RawContentStream.ToArray())
        return @{ status = [int]$resp.StatusCode; json = ($raw | ConvertFrom-Json) }
    } catch {
        $code = 0; $raw = ''
        if ($_.Exception.Response) { try { $code = [int]$_.Exception.Response.StatusCode } catch {}; try { $sr = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream()); $raw = $sr.ReadToEnd() } catch {} }
        $json = $null; try { $json = $raw | ConvertFrom-Json } catch {}
        return @{ status = $code; json = $json; raw = $raw }
    }
}
function Check($id, $desc, $cond, $info) {
    if ($cond) { Write-Host "[PASS] $id - $desc => $info" -ForegroundColor Green; $script:pass++ }
    else { Write-Host "[FAIL] $id - $desc => $info" -ForegroundColor Red; $script:fail++ }
}

$login = Invoke-Api POST '/auth/login' @{ username='admin'; password='123456' }
$tok = $login.json.data.token
Check 'AUTH-01' 'admin 登录且持有管理员角色' ($login.status -eq 200 -and $tok -and $login.json.data.roles -contains 'admin') "http=$($login.status)"
if (-not $tok) { Write-Host '请先运行一键启动，登录失败不能视为负向用例通过。'; exit 1 }
$second = (Invoke-Api POST '/auth/login' @{ username='admin'; password='123456' }).json.data.token
Check 'AUTH-02' '同账号并发登录签发独立令牌' ($second -and $second -ne $tok) '独立会话'

# 对比真实权限 ID，而不是仅检查页面勾选数量。
$roleList = Invoke-Api GET '/roles?pageSize=200' $null $tok
$adminRoles = @($roleList.json.data.list | Where-Object { $_.code -eq 'admin' })
$permissionPage = Invoke-Api GET '/permissions?pageSize=200' $null $tok
$permissionIds = @($permissionPage.json.data.list | ForEach-Object { $_.id })
Check 'ADMIN-01' '管理员及权限目录完整' ($adminRoles.Count -gt 0 -and $permissionIds.Count -ge 31 -and $permissionIds.Count -eq $permissionPage.json.data.total) "total=$($permissionIds.Count)"
foreach ($role in $adminRoles) {
    $bound = Invoke-Api GET "/roles/$($role.id)/permissions" $null $tok
    $missing = @($permissionIds | Where-Object { $bound.json.data -notcontains $_ })
    Check 'ADMIN-02' 'admin 实际绑定全部权限' ($bound.json.code -eq 200 -and $missing.Count -eq 0) "missing=$($missing.Count)"
}
$normal = Invoke-Api POST '/roles' @{ name="RB-Role-$runId"; code="rb-$runId" } $tok
$normalId = $normal.json.data.id
Invoke-Api POST "/roles/$normalId/permissions" @($permissionIds[0]) $tok | Out-Null
$createdPermission = Invoke-Api POST '/permissions' @{ name="RB-Perm-$runId"; code="rb:$runId"; module='regression' } $tok
$newPermissionId = $createdPermission.json.data.id
Check 'ADMIN-03' '新增权限成功' ($createdPermission.json.code -eq 200 -and $newPermissionId) "id=$newPermissionId"
foreach ($role in $adminRoles) {
    $bound = Invoke-Api GET "/roles/$($role.id)/permissions" $null $tok
    Check 'ADMIN-04' '新增权限自动授予 admin' ($newPermissionId -and $bound.json.data -contains $newPermissionId) "role=$($role.id)"
    Invoke-Api POST "/roles/$($role.id)/permissions" @() $tok | Out-Null
    $bound = Invoke-Api GET "/roles/$($role.id)/permissions" $null $tok
    Check 'ADMIN-05' '清空勾选不能削弱 admin' (@($bound.json.data).Count -eq $permissionIds.Count + 1) "count=$(@($bound.json.data).Count)"
}
$normalBound = Invoke-Api GET "/roles/$normalId/permissions" $null $tok
Check 'ROLE-01' '普通角色没有自动提权' ($normalBound.json.code -eq 200 -and @($normalBound.json.data).Count -eq 1 -and $normalBound.json.data -notcontains $newPermissionId) '保留原授权'
if ($newPermissionId) { Invoke-Api DELETE "/permissions/$newPermissionId" $null $tok | Out-Null }

foreach ($route in @('/dashboard/stats','/projects','/daily-inspections','/sideline-inspections','/supervision-notices','/work-contacts','/supervision-diaries','/supervision-logs','/safety-logs','/meeting-minutes','/supervision-monthly-reports','/sub-item-databases','/project-documents','/departments','/users','/roles','/permissions/tree','/operation-logs','/messages','/work-standard-configs')) {
    $access = Invoke-Api GET $route $null $tok
    Check 'ADMIN-MODULE' "admin 访问 $route" ($access.status -eq 200 -and $access.json.code -eq 200) "http=$($access.status)"
}

# 流程管理是静态流程说明页，没有 /processes 接口；页面接入由前端回归验证。

# ================= BUG-01: dept with ACTIVE member must NOT be deletable =================
$d = Invoke-Api POST '/departments' @{ name='RB-Dept-A'; status='active' } $tok
$deptA = $d.json.data.id
Check 'B01-01' 'create parent dept' ($d.json.code -eq 200 -and $deptA) "id=$deptA"

$u = Invoke-Api POST '/users' @{ name=$userName; phone=$phone; deptId=$deptA; password='abc123'; status='active' } $tok
$uidA = $u.json.data.id
Check 'B01-02' 'create active user in dept' ($u.json.code -eq 200 -and $uidA) "uid=$uidA"
Invoke-Api POST "/roles/$normalId/users" @($uidA) $tok | Out-Null
$worker = (Invoke-Api POST '/auth/login' @{ username=$phone; password='abc123' }).json.data.token
$workerInfo = Invoke-Api GET '/auth/info' $null $worker
$adminInfo = Invoke-Api GET '/auth/info' $null $tok
Check 'AUTH-03' '不同账号请求身份隔离' ($worker -and $workerInfo.json.data.userId -eq $uidA -and $adminInfo.json.data.userName -eq 'admin') '身份一致'
$forbidden = Invoke-Api GET '/roles' $null $worker
Check 'ROLE-02' '普通角色仍被管理员接口拒绝' ($worker -and $forbidden.status -eq 403) "http=$($forbidden.status)"
Invoke-Api POST '/auth/logout' $null $second | Out-Null
$old = Invoke-Api GET '/auth/info' $null $second
$alive = Invoke-Api GET '/auth/info' $null $tok
$workerAlive = Invoke-Api GET '/auth/info' $null $worker
Check 'AUTH-04' '退出令牌不可复用' ($old.status -eq 401) "http=$($old.status)"
Check 'AUTH-05' '退出不影响独立登录的 admin 和普通账号' ($alive.json.code -eq 200 -and $workerAlive.json.code -eq 200) '其他会话有效'

$del = Invoke-Api DELETE "/departments/$deptA" $null $tok
Check 'B01-03' 'delete dept WITH active member -> blocked (expect code!=200)' ($del.json.code -ne 200) "http=$($del.status) body=$($del.json | ConvertTo-Json -Compress)"

# ================= BUG-01b: dept with CHILD dept must NOT be deletable =================
$d2 = Invoke-Api POST '/departments' @{ name='RB-Dept-Root'; status='active' } $tok
$rootId = $d2.json.data.id
$d3 = Invoke-Api POST '/departments' @{ name='RB-Dept-Child'; parentId=$rootId; status='active' } $tok
$childId = $d3.json.data.id
$delChild = Invoke-Api DELETE "/departments/$rootId" $null $tok
Check 'B01-04' 'delete dept WITH child -> blocked' ($delChild.json.code -ne 200) "http=$($delChild.status) msg=$($delChild.json.message)"

# cleanup child then root
Invoke-Api DELETE "/departments/$childId" $null $tok | Out-Null
$delRoot = Invoke-Api DELETE "/departments/$rootId" $null $tok
Check 'B01-05' 'delete dept after child removed -> ok' ($delRoot.json.code -eq 200) "http=$($delRoot.status)"

# ================= BUG-01/02 getById guard: deleted (inactive) not viewable =================
$delA = Invoke-Api DELETE "/departments/$deptA" $null $tok   # move user to inactive first
# inactivate member so dept becomes deletable
Invoke-Api PUT "/users/$uidA" @{ name=$userName; phone=$phone; deptId=$deptA; password='abc123'; status='inactive' } $tok | Out-Null
Invoke-Api DELETE "/users/$uidA" $null $tok | Out-Null
$delA2 = Invoke-Api DELETE "/departments/$deptA" $null $tok
Check 'B01-06' 'delete dept after members inactive/removed -> ok' ($delA2.json.code -eq 200) "http=$($delA2.status) msg=$($delA2.json.message)"
$getDel = Invoke-Api GET "/departments/$deptA" $null $tok
Check 'B01-07' 'GET deleted dept -> code!=200 (getById guard)' ($getDel.json.code -ne 200) "http=$($getDel.status)"

# ================= BUG-04: soft-deleted user not in default list =================
$listAfter = Invoke-Api GET "/users?name=$userName&pageNum=1&pageSize=200" $null $tok
$stillThere = @($listAfter.json.data.list | Where-Object { $_.id -eq $uidA })
Check 'B04-01' 'soft-deleted user hidden from default list' ($stillThere.Count -eq 0) "count=$($stillThere.Count)"
# but visible when explicitly filtering status=inactive
$listInact = Invoke-Api GET "/users?name=$userName&status=inactive&pageNum=1&pageSize=200" $null $tok
$foundInact = @($listInact.json.data.list | Where-Object { $_.id -eq $uidA })
Check 'B04-02' 'inactive user retrievable via status=inactive filter' ($foundInact.Count -ge 1) "count=$($foundInact.Count)"

# ================= BUG-03: project buildUnit persisted & returned =================
$p = Invoke-Api POST '/projects' @{ name='RB-Proj-1'; code="RB-$runId"; buildUnit='RB-建设单位-测试'; type='房建'; status='active'; startDate='2026-01-01'; endDate='2026-12-31' } $tok
$prjId = $p.json.data.id
Check 'B03-01' 'create project WITH buildUnit -> ok' ($p.json.code -eq 200 -and $prjId) "http=$($p.status) id=$prjId"
$gp = Invoke-Api GET "/projects/$prjId" $null $tok
Check 'B03-02' 'project detail returns buildUnit' ($gp.json.data.buildUnit -eq 'RB-建设单位-测试') "buildUnit=$($gp.json.data.buildUnit)"
# create WITHOUT buildUnit -> should be rejected (required field)
$pnb = Invoke-Api POST '/projects' @{ name='RB-Proj-NoBU'; code='RB-PRJ-0002'; type='房建' } $tok
Check 'B03-03' 'create project WITHOUT buildUnit -> rejected' ($pnb.json.code -ne 200) "http=$($pnb.status)"

# ================= BUG-02: deleted project detail guarded =================
$dp = Invoke-Api DELETE "/projects/$prjId" $null $tok
Check 'B02-01' 'delete project -> ok' ($dp.json.code -eq 200) "http=$($dp.status)"
$gdp = Invoke-Api GET "/projects/$prjId" $null $tok
Check 'B02-02' 'GET deleted project -> code!=200 (getById guard)' ($gdp.json.code -ne 200) "http=$($gdp.status)"
$listP = Invoke-Api GET '/projects?name=RB-Proj-1&pageNum=1&pageSize=200' $null $tok
$stillP = @($listP.json.data.list | Where-Object { $_.id -eq $prjId })
Check 'B02-03' 'deleted project hidden from list' ($stillP.Count -eq 0) "count=$($stillP.Count)"

if ($normalId) { Invoke-Api DELETE "/roles/$normalId" $null $tok | Out-Null }
Invoke-Api POST '/auth/logout' $null $tok | Out-Null
& node (Join-Path $PSScriptRoot 'session-regression.cjs')
Check 'FRONTEND' '标签页隔离、退出及旧响应竞态回归' ($LASTEXITCODE -eq 0) "exit=$LASTEXITCODE"
Write-Host ""
Write-Host ("TOTAL: {0}  PASS: {1}  FAIL: {2}" -f ($pass+$fail), $pass, $fail) -ForegroundColor Cyan
if ($fail -gt 0) { exit 1 }
exit 0
