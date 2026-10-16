# ASCII-only confirmation that project.buildUnit persists and returns (avoids console codepage false-negative)
$ErrorActionPreference = 'Continue'
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

$tok = (Invoke-Api POST '/auth/login' @{ username='admin'; password='123456' }).json.data.token
$marker = 'RB-BUILDUNIT-ASCII-0001'
$p = Invoke-Api POST '/projects' @{ name='RB-Proj-Ascii'; code='RB-PRJ-9001'; buildUnit=$marker; type='test'; status='active'; startDate='2026-01-01'; endDate='2026-12-31' } $tok
$prjId = $p.json.data.id
Write-Host "create http=$($p.status) code=$($p.json.code) id=$prjId"
$gp = Invoke-Api GET "/projects/$prjId" $null $tok
$ret = $gp.json.data.buildUnit
if ($ret -eq $marker) { Write-Host "[PASS] B03-02 buildUnit round-trips intact (ascii) value=$ret" -ForegroundColor Green }
else { Write-Host "[FAIL] B03-02 expected=$ret actual=$ret" -ForegroundColor Red }
Invoke-Api DELETE "/projects/$prjId" $null $tok | Out-Null
