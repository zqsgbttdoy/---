// 从微信开发者工具本机日志中提取最近的报错（白屏时 Console 里的错误也会写进这里）。
// 用法：node 测试工作区\开发者工具日志提取.cjs [起始时间 HH:MM，默认最近 30 分钟]
const fs = require('fs');
const path = require('path');

const base = path.join(process.env.LOCALAPPDATA, '微信开发者工具', 'User Data');
function walk(dir, out = []) {
  let list = [];
  try { list = fs.readdirSync(dir, { withFileTypes: true }); } catch { return out; }
  for (const d of list) {
    const p = path.join(dir, d.name);
    if (d.isDirectory()) { if (!/WeappCompileCache|Cache|GPUCache|Code Cache|IndexedDB/.test(d.name)) walk(p, out); }
    else if (/WeappLog[\\/]logs[\\/].*\.log$|miniprogramLog[\\/]log\d+$/.test(p)) out.push(p);
  }
  return out;
}
const since = process.argv[2]
  ? (() => { const [h, m] = process.argv[2].split(':').map(Number); const t = new Date(); t.setHours(h, m, 0, 0); return t; })()
  : new Date(Date.now() - 30 * 60000);
const files = walk(base).filter(f => fs.statSync(f).mtime >= since);
if (!files.length) { console.log('未找到最近的开发者工具日志（请先在开发者工具点一次「编译」再运行）'); process.exit(0); }

const pattern = /error|exception|fail|未找到|not defined|is not a function|Cannot read|Unexpected|白屏|not found|invalid|无权限|wxml|appid/i;
const noise = /DevTools listening|sourcemap|\.map\b|favicon|reportRealtime|wxacrash|getSystemInfo|Request timed out.*report|statistic/i;
for (const f of files) {
  const lines = fs.readFileSync(f, 'utf8').split(/\r?\n/);
  const hits = [];
  const seen = new Set();
  lines.forEach((line, i) => {
    const ts = line.match(/(\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}:\d{2})/);
    if (ts && new Date(ts[1].replace(' ', 'T')) < since) return;
    if (!pattern.test(line) || noise.test(line)) return;
    const key = line.replace(/^\S+\s+\S+\s*/, '').replace(/\d+/g, '#').slice(0, 160);
    if (seen.has(key)) return;
    seen.add(key);
    hits.push(`${String(i + 1).padStart(6)}: ${line.slice(0, 600)}`);
  });
  console.log(`\n===== ${path.relative(base, f)}（${hits.length} 条去重后） =====`);
  hits.slice(-80).forEach(h => console.log(h));
}
