# API functional tests: departments / users / projects (supervision system)
# Usage: powershell -File api-functional-tests.ps1
$ErrorActionPreference = 'Continue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$base = 'http://localhost:8081/api'   # go through static server proxy (same path as frontend)
$results = @()
$script:pass = 0; $script:fail = 0; $script:blocked = 0

function Invoke-Api {
    param([string]$Method, [string]$Path, $Body = $null, [string]$Token = $null)
    $headers = @{}
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    try {
        $params = @{ Uri = "$base$Path"; Method = $Method; Headers = $headers; ContentType = 'application/json; charset=utf-8'; UseBasicParsing = $true; TimeoutSec = 20 }
        if ($null -ne $Body) { $params['Body'] = ($Body | ConvertTo-Json -Depth 6) }
        $resp = Invoke-WebRequest @params
        $code = [int]$resp.StatusCode
        $json = $null; try { $json = $resp.Content | ConvertFrom-Json } catch {}
        return @{ status = $code; json = $json; raw = $resp.Content }
    } catch {
        $ex = $_.Exception
        $code = 0; $raw = ''
        if ($ex.Response) {
            try { $code = [int]$ex.Response.StatusCode } catch {}
            try { $sr = New-Object IO.StreamReader($ex.Response.GetResponseStream()); $raw = $sr.ReadToEnd(); } catch {}
        }
        $json = $null; try { $json = $raw | ConvertFrom-Json } catch {}
        return @{ status = $code; json = $json; raw = $raw; err = $ex.Message }
    }
}

function Check {
    param([string]$Id, [string]$Desc, [bool]$Ok, [string]$Actual)
    if ($Ok) { $script:pass++; $s = 'PASS' } else { $script:fail++; $s = 'FAIL' }
    $script:results += [pscustomobject]@{ Id = $Id; Case = $Desc; Result = $s; Actual = $Actual }
    Write-Host ("[{0}] {1} - {2} => {3} | {4}" -f $s, $Id, $Desc, $Actual, '')
}

# ---------- AUTH ----------
$r = Invoke-Api POST '/auth/login' @{ username = 'admin'; password = 'wrong-pass' }
Check 'AUTH-01' 'login with wrong password rejected' ($r.status -ne 200 -or ($r.json.code -ne 200 -and $r.json.code -ne 0)) "status=$($r.status) body=$($r.raw.Substring(0,[Math]::Min(80,$r.raw.Length)))"

$r = Invoke-Api POST '/auth/login' @{ username = 'admin'; password = '123456' }
$token = $r.json.data.token; if (-not $token) { $token = $r.json.data.accessToken }
Check 'AUTH-02' 'admin/123456 login returns token' ([bool]$token) "status=$($r.status)"
if (-not $token) { Write-Host 'FATAL: cannot obtain admin token, aborting'; $results | Export-Csv "$PSScriptRoot\api-test-results.csv" -Encoding UTF8; exit 1 }

$r = Invoke-Api GET '/departments' $null 'invalid-token-here'
Check 'AUTH-03' 'request with invalid token -> 401' ($r.status -eq 401) "status=$($r.status)"

# ---------- DEPARTMENTS ----------
$r = Invoke-Api GET '/departments'
Check 'DEPT-01' 'list departments without token -> 401' ($r.status -eq 401) "status=$($r.status)"

$r = Invoke-Api POST '/departments' @{ name = 'QA-Dept-01'; description = 'auto test dept'; sortOrder = 99; status = 'active' } $token
$deptId = $r.json.data.id
Check 'DEPT-02' 'create department (valid)' ($r.status -eq 200 -and $deptId) "status=$($r.status) id=$deptId"

$r = Invoke-Api POST '/departments' @{ name = ''; status = 'active' } $token
Check 'DEPT-03' 'create with empty name -> validation error' ($r.status -ne 200 -or $r.json.code -ne 200) "status=$($r.status)"

$longName = 'X' * 101
$r = Invoke-Api POST '/departments' @{ name = $longName } $token
Check 'DEPT-04' 'create with 101-char name -> rejected (max 100)' ($r.status -ne 200 -or $r.json.code -ne 200) "status=$($r.status)"

$r = Invoke-Api GET '/departments?name=QA-Dept&pageNum=1&pageSize=10' $null $token
$found = $r.json.data.list | Where-Object { $_.id -eq $deptId }
Check 'DEPT-05' 'search by name filter works' ([bool]$found) "total=$($r.json.data.total)"

$r = Invoke-Api GET "/departments/$deptId" $null $token
Check 'DEPT-06' 'get department by id' ($r.status -eq 200 -and $r.json.data.name -eq 'QA-Dept-01') "status=$($r.status)"

$r = Invoke-Api PUT "/departments/$deptId" @{ name = 'QA-Dept-01-UPD'; status = 'active' } $token
Check 'DEPT-07' 'update department' ($r.status -eq 200 -and $r.json.data.name -eq 'QA-Dept-01-UPD') "status=$($r.status)"

$r = Invoke-Api GET '/departments?pageNum=1&pageSize=9999' $null $token
Check 'DEPT-08' 'pageSize clamped to <=200' ($r.status -eq 200 -and $r.json.data.pageSize -le 200) "pageSize=$($r.json.data.pageSize)"

# ---------- USERS ----------
$r = Invoke-Api POST '/users' @{ name = 'QA-User-01'; phone = '13900001234'; deptId = $deptId; position = 'QA test role'; password = 'abc123'; status = 'active' } $token
$userId = $r.json.data.id
Check 'USER-01' 'create user (valid)' ($r.status -eq 200 -and $userId) "status=$($r.status) id=$userId"

$r = Invoke-Api POST '/users' @{ name = 'QA-Bad'; phone = '123'; deptId = $deptId } $token
Check 'USER-02' 'create user with invalid phone -> rejected' ($r.status -ne 200 -or $r.json.code -ne 200) "status=$($r.status)"

$r = Invoke-Api POST '/users' @{ name = 'QA-Bad2'; phone = '13900001235'; password = '12345' } $token
Check 'USER-03' 'create user with 5-char password -> rejected (min 6)' ($r.status -ne 200 -or $r.json.code -ne 200) "status=$($r.status)"

$r = Invoke-Api GET '/users?name=QA-User&pageNum=1&pageSize=10' $null $token
$found = $r.json.data.list | Where-Object { $_.id -eq $userId }
Check 'USER-04' 'user list name filter works' ([bool]$found) "total=$($r.json.data.total)"

$r = Invoke-Api GET "/users?deptId=$deptId" $null $token
Check 'USER-05' 'user list deptId filter works' ($r.status -eq 200 -and @($r.json.data.list).Count -ge 1) "count=$(@($r.json.data.list).Count)"

$r = Invoke-Api PUT "/users/$userId/status?status=inactive" $null $token
Check 'USER-06' 'update user status' ($r.status -eq 200) "status=$($r.status)"

$r = Invoke-Api PUT "/users/$userId/reset-password" $null $token
Check 'USER-07' 'admin resets user password, returns new one' ($r.status -eq 200 -and $r.json.data) "new=$($r.json.data)"

# dept with bound users must not be deletable
$r = Invoke-Api DELETE "/departments/$deptId" $null $token
Check 'DEPT-09' 'delete dept that has users -> business error' ($r.status -ne 200 -or $r.json.code -ne 200) "status=$($r.status) msg=$($r.json.message)"

# non-admin permission check (seeded user 2 has no admin role)
$r2 = Invoke-Api POST '/auth/login' @{ username = '13800000001'; password = '123456' }
$t2 = $r2.json.data.token; if (-not $t2) { $t2 = $r2.json.data.accessToken }
if (-not $t2) { $r2 = Invoke-Api POST '/auth/login' @{ username = '张总监'; password = '123456' }; $t2 = $r2.json.data.token }
if ($t2) {
    $r = Invoke-Api POST '/users' @{ name = 'QA-Forbidden'; phone = '13900009999' } $t2
    Check 'USER-08' 'non-admin create user -> 403' ($r.status -eq 403 -or ($r.json -and $r.json.code -eq 403)) "status=$($r.status)"
} else {
    Check 'USER-08' 'non-admin create user -> 403' $false "BLOCKED: cannot login as non-admin (attempted phone & name)"
}

$r = Invoke-Api DELETE "/users/$userId" $null $token
Check 'USER-09' 'delete user' ($r.status -eq 200) "status=$($r.status)"

$r = Invoke-Api DELETE "/departments/$deptId" $null $token
Check 'DEPT-10' 'delete department (after users removed)' ($r.status -eq 200) "status=$($r.status)"

$r = Invoke-Api GET "/departments/$deptId" $null $token
Check 'DEPT-11' 'get deleted department -> error' ($r.status -ne 200 -or $r.json.code -ne 200) "status=$($r.status)"

# ---------- PROJECTS ----------
$r = Invoke-Api POST '/projects' @{ name = 'QA-Project-01'; code = 'QA-PRJ-001'; type = '市政'; status = 'active'; address = 'test address'; startDate = '2026-01-01'; endDate = '2026-12-31' } $token
$prjId = $r.json.data.id
Check 'PRJ-01' 'create project (valid)' ($r.status -eq 200 -and $prjId) "status=$($r.status) id=$prjId"

$r = Invoke-Api POST '/projects' @{ name = 'QA-NoCode' } $token
Check 'PRJ-02' 'create without code -> validation error' ($r.status -ne 200 -or $r.json.code -ne 200) "status=$($r.status)"

$r = Invoke-Api POST '/projects' @{ name = 'QA-Dup'; code = 'QA-PRJ-001' } $token
$dupRejected = ($r.status -ne 200 -or $r.json.code -ne 200)
Check 'PRJ-03' 'create with duplicate code -> rejected' $dupRejected "status=$($r.status) code=$($r.json.code) (observation if PASS=false: backend allows duplicate codes)"
if (-not $dupRejected -and $r.json.data.id) { Invoke-Api DELETE "/projects/$($r.json.data.id)" $null $token | Out-Null }

$r = Invoke-Api GET '/projects?code=QA-PRJ-001' $null $token
Check 'PRJ-04' 'project list code filter works' (@($r.json.data.list).Count -eq 1) "count=$(@($r.json.data.list).Count)"

$r = Invoke-Api GET '/projects?name=QA-Project&status=active' $null $token
Check 'PRJ-05' 'project list name+status filter works' ($r.status -eq 200 -and @($r.json.data.list).Count -ge 1) "count=$(@($r.json.data.list).Count)"

$r = Invoke-Api PUT "/projects/$prjId" @{ name = 'QA-Project-01-UPD'; code = 'QA-PRJ-001'; status = 'active' } $token
Check 'PRJ-06' 'update project' ($r.status -eq 200 -and $r.json.data.name -eq 'QA-Project-01-UPD') "status=$($r.status)"

$r = Invoke-Api PUT "/projects/$prjId/status?status=completed" $null $token
Check 'PRJ-07' 'update project status' ($r.status -eq 200) "status=$($r.status)"

$r = Invoke-Api GET "/projects/$prjId" $null $token
Check 'PRJ-08' 'get project detail' ($r.status -eq 200 -and $r.json.data.code -eq 'QA-PRJ-001') "status=$($r.status)"

$r = Invoke-Api DELETE '/projects/99999999' $null $token
Check 'PRJ-09' 'delete non-existent project -> error' ($r.status -ne 200 -or $r.json.code -ne 200) "status=$($r.status)"

$r = Invoke-Api DELETE "/projects/$prjId" $null $token
Check 'PRJ-10' 'delete project' ($r.status -eq 200) "status=$($r.status)"

# ---------- SUMMARY ----------
Write-Host ""
Write-Host ("TOTAL: {0}  PASS: {1}  FAIL: {2}" -f ($pass + $fail), $pass, $fail)
$results | Export-Csv "$PSScriptRoot\api-test-results.csv" -NoTypeInformation -Encoding UTF8
Write-Host "Detail saved to $PSScriptRoot\api-test-results.csv"
