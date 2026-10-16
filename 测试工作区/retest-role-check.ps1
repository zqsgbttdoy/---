# Follow-up: verify @RequireRole blocks a user WITHOUT any role
$base = 'http://localhost:8081/api'
function Invoke-Api {
    param([string]$Method, [string]$Path, $Body = $null, [string]$Token = $null)
    $headers = @{}
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    try {
        $params = @{ Uri = "$base$Path"; Method = $Method; Headers = $headers; ContentType = 'application/json; charset=utf-8'; UseBasicParsing = $true; TimeoutSec = 20 }
        if ($null -ne $Body) { $params['Body'] = ($Body | ConvertTo-Json -Depth 6) }
        $resp = Invoke-WebRequest @params
        return @{ status = [int]$resp.StatusCode; json = ($resp.Content | ConvertFrom-Json) }
    } catch {
        $code = 0; $raw = ''
        if ($_.Exception.Response) { try { $code = [int]$_.Exception.Response.StatusCode } catch {}; try { $sr = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream()); $raw = $sr.ReadToEnd() } catch {} }
        $json = $null; try { $json = $raw | ConvertFrom-Json } catch {}
        return @{ status = $code; json = $json; raw = $raw }
    }
}
$admin = (Invoke-Api POST '/auth/login' @{ username = 'admin'; password = '123456' }).json.data.token

# create a plain user (no role assigned)
Invoke-Api POST '/users' @{ name = 'QA-Guest'; phone = '13900007777'; password = 'abc123'; status = 'active' } $admin | Out-Null
$g = Invoke-Api POST '/auth/login' @{ username = '13900007777'; password = 'abc123' }
$gtoken = $g.json.data.token
Write-Host "guest login status=$($g.status) roles=$($g.json.data.roles)"

$r = Invoke-Api GET '/departments' $null $gtoken
Write-Host "RETEST-01 guest GET /departments (admin-only) => status=$($r.status) code=$($r.json.code) expect 403"
$r = Invoke-Api POST '/users' @{ name = 'QA-X'; phone = '13900008888' } $gtoken
Write-Host "RETEST-02 guest POST /users => status=$($r.status) code=$($r.json.code) expect 403"
$r = Invoke-Api GET '/users' $null $gtoken
Write-Host "RETEST-03 guest GET /users (login-only) => status=$($r.status) code=$($r.json.code) expect 200"

# cleanup
$lst = Invoke-Api GET '/users?name=QA-Guest' $null $admin
foreach ($u in $lst.json.data.list) { Invoke-Api DELETE "/users/$($u.id)" $null $admin | Out-Null; Write-Host "cleaned user id=$($u.id)" }
