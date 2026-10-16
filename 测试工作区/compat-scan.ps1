$root = 'e:\希地工作\trae 文件保存\完整版-监理 - qoder\补后端'
$c = Get-Content (Join-Path $root 'assets\app.js') -Raw
function M($s, $p) { return ([regex]::Matches($s, $p)).Count }
$all = (Get-ChildItem (Join-Path $root '*.html') | ForEach-Object { Get-Content $_.FullName -Raw }) -join ' '

$patterns = [ordered]@{
  'OC(?.)'      = '\?\.'
  'NC(??)'      = '\?\?'
  'async'        = '\basync\b'
  'arrow(=>)'    = '=>'
  'fetch('       = 'fetch\('
  'let/const'    = '(?<![\w])(let|const) '
  'css-var'      = 'var\(--'
  'grid'         = 'display:\s*grid'
  'sticky'       = 'position:\s*sticky'
  'flex'         = 'display:\s*flex'
  'keyframes'    = '@keyframes'
  'gradient'     = 'linear-gradient'
  'transform'    = 'transform:'
  'placeholder'  = '::placeholder'
  'input-date'   = 'type="date"'
  'input-file'   = 'type="file"'
  'template-bt'  = ([regex]::Escape([string][char]96))
}

$out = New-Object System.Text.StringBuilder
foreach ($k in $patterns.Keys) {
  $a = M $c $patterns[$k]
  $b = M $all $patterns[$k]
  [void]$out.AppendLine(('{0,-14} appjs={1,-4} html={2}' -f $k, $a, $b))
}
$out.ToString()
