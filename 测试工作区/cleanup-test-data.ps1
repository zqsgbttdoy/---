$base = 'http://localhost:8081/api'
$t = (Invoke-RestMethod -Uri "$base/auth/login" -Method Post -ContentType 'application/json' -Body '{"username":"admin","password":"123456"}').data.token
$h = @{ Authorization = "Bearer $t" }
$all = Invoke-RestMethod -Uri ("$base/users?pageNum=1&pageSize=200") -Headers $h
$all.data.list | Where-Object { $_.name -like 'QA-*' -or $_.name -like 'UI*' } | ForEach-Object {
    Invoke-RestMethod -Uri "$base/users/$($_.id)" -Method Delete -Headers $h | Out-Null
    Write-Host "deleted user id=$($_.id) name=$($_.name)"
}
$d = Invoke-RestMethod -Uri ("$base/departments?pageNum=1&pageSize=200") -Headers $h
$d.data.list | Where-Object { $_.name -like 'QA-*' -or $_.name -like 'UI*' } | ForEach-Object {
    Invoke-RestMethod -Uri "$base/departments/$($_.id)" -Method Delete -Headers $h | Out-Null
    Write-Host "deleted dept id=$($_.id)"
}
Write-Host "cleanup done"
