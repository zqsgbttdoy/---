$root = 'e:\希地工作\trae 文件保存\完整版-监理 - qoder\补后端'
Get-ChildItem (Join-Path $root '*.html') | ForEach-Object {
  $t = Get-Content $_.FullName -Raw
  foreach ($m in [regex]::Matches($t, '\?\.')) {
    $s = [Math]::Max(0, $m.Index - 30); $len = [Math]::Min(60, $t.Length - $s)
    Write-Output ($_.Name + ' :: ' + ($t.Substring($s, $len) -replace '\s+', ' '))
  }
} | Select-Object -First 15
