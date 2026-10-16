// 不可见字符 / 编码 / 行尾 / 表达式引号配对 深度扫描
const fs = require('fs'), path = require('path');
const root = path.join(__dirname, '..', '补后端', 'miniprogram');
function walk(d) {
  for (const f of fs.readdirSync(d)) {
    const p = path.join(d, f);
    if (fs.statSync(p).isDirectory()) { if (f !== 'node_modules') walk(p); }
    else if (/\.(wxml|js|wxss|json)$/.test(f)) chk(p, f);
  }
}
const inv = { '\u200B': '零宽空格', '\u200C': '零宽不连字', '\u200D': '零宽连字', '\u00A0': '不换行空格', '\u2028': '行分隔符', '\u2029': '段分隔符', '\u3000': '全角空格' };
function chk(p, f) {
  const b = fs.readFileSync(p);
  const rel = path.relative(root, p);
  const t = b.toString('utf8');
  if (b[0] === 0xEF && b[1] === 0xBB && b[2] === 0xBF) console.log('[文件头 BOM] ' + rel);
  t.split('\n').forEach((ln, i) => {
    for (const k in inv) if (ln.indexOf(k) >= 0) console.log('[不可见字符 ' + inv[k] + '] ' + rel + ' 行' + (i + 1) + ' 列' + (ln.indexOf(k) + 1));
  });
  if (/\r\n/.test(t) && /\.wxml$/.test(f)) console.log('[CRLF 行尾] ' + rel);
  if (/\.wxml$/.test(f)) {
    [...t.matchAll(/\{\{([\s\S]*?)\}\}/g)].forEach((m, idx) => {
      const s = m[1];
      const sq = (s.match(/'/g) || []).length, dq = (s.match(/"/g) || []).length;
      if (sq % 2 || dq % 2) console.log('[表达式引号不配对] ' + rel + ' #' + idx + ': {{' + s + '}}');
      if (/[\u201C\u201D\u2018\u2019]/.test(s)) console.log('[中文引号进表达式] ' + rel + ' #' + idx + ': {{' + s + '}}');
    });
    // 属性双引号未闭合粗查：逐行统计 <tag 内的引号数
    t.split('\n').forEach((ln, i) => {
      const tags = ln.match(/<[a-zA-Z][^>]*\/?>/g) || [];
      tags.forEach(tg => {
        const q = (tg.match(/"/g) || []).length;
        if (q % 2) console.log('[属性引号可能未闭合] ' + rel + ' 行' + (i + 1) + ': ' + tg.slice(0, 60));
      });
    });
  }
}
walk(root);
console.log('扫描完成');
