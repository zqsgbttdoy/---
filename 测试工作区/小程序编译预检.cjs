// 小程序编译预检：模拟微信开发者工具编译阶段的四类检查，任何一项报错都会让模拟器白屏。
// 用法：node 测试工作区\小程序编译预检.cjs
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const root = path.resolve(__dirname, '..', '补后端', 'miniprogram');
const errors = [];
const err = (file, line, msg) => errors.push(`${path.relative(root, file)}:${line}  ${msg}`);
const lineOf = (text, idx) => text.slice(0, idx).split('\n').length;

function walk(dir, out = []) {
  for (const name of fs.readdirSync(dir)) {
    if (name === 'node_modules' || name.startsWith('.')) continue;
    const p = path.join(dir, name);
    fs.statSync(p).isDirectory() ? walk(p, out) : out.push(p);
  }
  return out;
}
const files = walk(root);
const count = {};
files.forEach(f => { const e = path.extname(f); count[e] = (count[e] || 0) + 1; });

// 1. WXML：{{ }} 表达式必须是合法 JS 表达式（微信不会把 &amp; 还原为 &）
const voidTags = new Set(['input', 'image', 'icon', 'progress', 'import', 'include', 'wxs', 'textarea', 'slider', 'switch', 'radio', 'checkbox']);
for (const f of files.filter(f => f.endsWith('.wxml'))) {
  const text = fs.readFileSync(f, 'utf8');
  if (text.charCodeAt(0) === 0xfeff) err(f, 1, '文件含 BOM 头');
  const re = /\{\{([\s\S]*?)\}\}/g;
  let m;
  while ((m = re.exec(text))) {
    const expr = m[1];
    if (/&(amp|lt|gt|quot|apos);/.test(expr)) { err(f, lineOf(text, m.index), `表达式含 HTML 实体（微信不解码）：{{${expr}}}`); continue; }
    try { new Function(`return (${expr});`); }
    catch (e) {
      // 对象字面量简写（template data="{{a, b}}"、{{...item}}）是 WXML 特有合法写法
      try { new Function(`return ({${expr}});`); } catch { err(f, lineOf(text, m.index), `表达式语法错误 {{${expr}}}：${e.message}`); }
    }
  }
  // 标签配对
  const stripped = text.replace(/<!--[\s\S]*?-->/g, s => s.replace(/[^\n]/g, ' '))
    .replace(/\{\{[\s\S]*?\}\}/g, s => s.replace(/[^\n]/g, ' '))
    .replace(/<wxs[\s\S]*?<\/wxs>/g, s => s.replace(/[^\n]/g, ' '));
  const stack = [];
  const tagRe = /<(\/?)([a-zA-Z][\w-]*)((?:\s+[^\s=>\/]+(?:\s*=\s*(?:"[^"]*"|'[^']*'))?)*)\s*(\/?)>/g;
  while ((m = tagRe.exec(stripped))) {
    const [, close, tag, , self] = m;
    const ln = lineOf(stripped, m.index);
    if (close) {
      const top = stack.pop();
      if (!top) err(f, ln, `多余的闭合标签 </${tag}>`);
      else if (top.tag !== tag) { err(f, ln, `</${tag}> 与第 ${top.ln} 行 <${top.tag}> 不匹配`); stack.length = 0; break; }
    } else if (!self && !voidTags.has(tag)) stack.push({ tag, ln });
  }
  stack.forEach(t => err(f, t.ln, `<${t.tag}> 未闭合`));
  const leftover = stripped.replace(tagRe, '').match(/<[a-zA-Z\/][^>]*$/m);
  if (leftover) err(f, lineOf(stripped, stripped.indexOf(leftover[0])), `疑似残缺标签：${leftover[0].slice(0, 40)}`);
}

// 2. JSON 合法性 + app.json 页面注册
for (const f of files.filter(f => f.endsWith('.json'))) {
  try { JSON.parse(fs.readFileSync(f, 'utf8').replace(/^\uFEFF/, '')); } catch (e) { err(f, 1, `JSON 格式错误：${e.message}`); }
}
const app = JSON.parse(fs.readFileSync(path.join(root, 'app.json'), 'utf8'));
for (const p of app.pages || []) {
  for (const ext of ['.js', '.wxml']) if (!fs.existsSync(path.join(root, p + ext))) err(path.join(root, 'app.json'), 1, `注册页面 ${p} 缺少 ${ext}`);
}
for (const f of files.filter(f => f.endsWith('.wxml') && f.includes(`${path.sep}pages${path.sep}`))) {
  const rel = path.relative(root, f).replace(/\\/g, '/').replace(/\.wxml$/, '');
  if (!(app.pages || []).includes(rel)) err(f, 1, `页面未在 app.json 注册：${rel}`);
}
for (const t of (app.tabBar && app.tabBar.list) || []) {
  if (!(app.pages || []).includes(t.pagePath)) err(path.join(root, 'app.json'), 1, `tabBar 页面未注册：${t.pagePath}`);
}

// 3. JS 语法 + require 路径
for (const f of files.filter(f => f.endsWith('.js'))) {
  try { execFileSync(process.execPath, ['--check', f], { stdio: 'pipe' }); }
  catch (e) { err(f, 1, `JS 语法错误：${String(e.stderr).split('\n').slice(0, 5).join(' | ')}`); }
  const text = fs.readFileSync(f, 'utf8');
  const rq = /require\(\s*['"](\.[^'"]+)['"]\s*\)/g;
  let m;
  while ((m = rq.exec(text))) {
    const target = path.resolve(path.dirname(f), m[1]);
    if (![target, target + '.js', path.join(target, 'index.js')].some(fs.existsSync)) err(f, lineOf(text, m.index), `require 路径不存在：${m[1]}`);
  }
}

// 4. WXSS：大括号配对
for (const f of files.filter(f => f.endsWith('.wxss'))) {
  const text = fs.readFileSync(f, 'utf8').replace(/\/\*[\s\S]*?\*\//g, '');
  let depth = 0;
  for (let i = 0; i < text.length; i++) {
    if (text[i] === '{') depth++;
    if (text[i] === '}' && --depth < 0) { err(f, lineOf(text, i), '多余的 }'); depth = 0; }
  }
  if (depth) err(f, 1, `缺少 ${depth} 个 }`);
}

console.log(`已检查：${Object.entries(count).map(([k, v]) => `${k || '无扩展名'} ${v} 个`).join('，')}`);
if (errors.length) { console.log(`\n❌ 发现 ${errors.length} 处问题：`); errors.forEach(e => console.log('  ' + e)); process.exit(1); }
console.log('\n✅ 全部通过：WXML 表达式、标签配对、JSON、页面注册、JS 语法、WXSS 均无错误');
