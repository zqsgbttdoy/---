// 小程序文件体检：JSON 合法性、BOM/NUL/编码损坏、WXML 标签闭合、页面注册、模板变量与 js data 对应
const fs = require('fs'), path = require('path');
const root = path.join(__dirname, '..', '补后端', 'miniprogram');
const out = [];

function walk(d) {
  for (const f of fs.readdirSync(d)) {
    const p = path.join(d, f);
    if (fs.statSync(p).isDirectory()) { if (f !== 'node_modules') walk(p); }
    else if (/\.(wxml|json|js|wxss)$/.test(f)) check(p, f);
  }
}
function check(p, f) {
  const buf = fs.readFileSync(p);
  const rel = path.relative(root, p);
  if (buf[0] === 0xEF && buf[1] === 0xBB && buf[2] === 0xBF) out.push('[BOM] ' + rel + '（带 BOM 可能导致开发者工具编译报错）');
  if (buf.includes(0)) out.push('[NUL] ' + rel + ' 含二进制空字符，文件已损坏');
  const text = buf.toString('utf8');
  if (/\uFFFD/.test(text)) out.push('[编码] ' + rel + ' 存在非 UTF-8 内容');
  if (/\.json$/.test(f)) { try { JSON.parse(text.replace(/^\uFEFF/, '')); } catch (e) { out.push('[JSON解析失败] ' + rel + ' -> ' + e.message); } }
  if (/\.wxml$/.test(f)) {
    const stack = [];
    const re = /<(\/?)([a-zA-Z][a-zA-Z0-9-]*)\b([^>]*)>/g;
    const selfClose = new Set(['input', 'image', 'textarea', 'import', 'include', 'wxs', 'switch', 'slider', 'progress', 'audio', 'video', 'canvas']);
    let m;
    while ((m = re.exec(text))) {
      const line = text.slice(0, m.index).split('\n').length;
      const close = m[1], tag = m[2], attrs = m[3];
      if (attrs.trim().endsWith('/')) continue;
      if (close) {
        const top = stack.pop();
        if (top && top.tag !== tag) out.push('[标签不匹配] ' + rel + ' 行' + line + '：期望 </' + top.tag + '>（开于行' + top.line + '），实际 </' + tag + '>');
      } else if (!selfClose.has(tag)) stack.push({ tag, line });
    }
    for (const s of stack) out.push('[未闭合标签] ' + rel + '：<' + s.tag + '> 开始于行' + s.line);
    const cn = /[\u2018\u2019\u201c\u201d\uFF08\uFF09]/;
    text.split('\n').forEach((ln, i) => {
      const exprs = (ln.match(/\{\{[^}]*\}\}/g) || []).join('');
      if (cn.test(exprs)) out.push('[中文标点混入表达式] ' + rel + ' 行' + (i + 1) + '：' + ln.trim().slice(0, 60));
    });
  }
}
walk(root);

const app = JSON.parse(fs.readFileSync(path.join(root, 'app.json'), 'utf8').replace(/^\uFEFF/, ''));
for (const pg of app.pages) {
  for (const ext of ['.js', '.wxml']) {
    if (!fs.existsSync(path.join(root, pg + ext))) out.push('[缺文件] 页面 ' + pg + ' 缺少 ' + ext);
  }
}

// wxml 模板 {{}} 内顶层标识符 vs 同名 js（剔除字符串字面量与 HTML 实体）
const clean = t => t.replace(/'[^']*'|"[^"]*"/g, '');
for (const pg of app.pages) {
  const wf = path.join(root, pg + '.wxml'), jf = path.join(root, pg + '.js');
  if (!fs.existsSync(wf) || !fs.existsSync(jf)) continue;
  const w = fs.readFileSync(wf, 'utf8'), j = fs.readFileSync(jf, 'utf8');
  const ids = new Set();
  for (const mm of w.matchAll(/\{\{([^}]*)\}\}/g)) {
    for (const t of clean(mm[1]).matchAll(/(^|[^.\w$])([A-Za-z_$][\w$]*)/g)) ids.add(t[2]);
  }
  const known = new Set(['true', 'false', 'null', 'undefined', 'item', 'index']);
  for (const id of ids) {
    if (known.has(id)) continue;
    if (!new RegExp('\\b' + id + '\\b').test(j)) out.push('[变量未定义?] ' + pg + '.wxml 表达式用到 ' + id + '，但 ' + pg + '.js 全文未出现');
  }
}
console.log(out.length ? out.join('\n') : '（静态检查全部通过，未发现问题）');
