// 按微信小程序同款链路自检：config.js 地址 → 登录 → 项目列表 → 今日摘要 → 我的日常巡检
// 用法：node 小程序连通性自检.cjs [手机号] [密码]，默认王强 13800000004 / 123456
const path = require('path');
const config = require(path.join(__dirname, '补后端', 'miniprogram', 'config.js'));
const phone = process.argv[2] || '13800000004';
const password = process.argv[3] || '123456';
const base = config.baseUrl + '/api';
const today = new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 10);
let failed = 0;

function ok(msg) { console.log('  [✅] ' + msg); }
function bad(msg, fix) { failed++; console.log('  [❌] ' + msg); if (fix) console.log('       → 怎么办：' + fix); }

async function call(p, options = {}) {
  const res = await fetch(base + p, { ...options, signal: AbortSignal.timeout(20000) });
  const body = await res.json().catch(() => null);
  if (!body || body.code !== 200) throw new Error((body && body.message) || ('HTTP ' + res.status));
  return body.data;
}

(async () => {
  console.log('小程序连通性自检（模拟微信开发者工具的请求）');
  console.log('config.js 中的 baseUrl：' + config.baseUrl + '   今天：' + today + '\n');

  if (!/:8081$/.test(config.baseUrl)) bad('baseUrl 不是 8081 端口', '把 补后端\\miniprogram\\config.js 的 baseUrl 改为 http://127.0.0.1:8081');
  else ok('第1步 配置：baseUrl 指向 8081');

  let token, userId;
  try {
    const s = await call('/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username: phone, password }) });
    token = s.token; userId = s.userId;
    ok('第2步 登录：' + s.userName + '（userId=' + userId + '）');
  } catch (e) {
    bad('登录失败：' + e.message, /fetch failed|ECONNREFUSED|aborted|timeout/i.test(e.message)
      ? '服务没启动——先双击《一键启动.bat》，等浏览器出现登录页后再运行本自检'
      : '账号密码不对，或演示数据没灌——双击《生成演示数据.bat》后重试');
    return finish();
  }
  const auth = { Authorization: 'Bearer ' + token };

  let project;
  try {
    const page = await call('/projects?pageNum=1&pageSize=100', { headers: auth });
    if (!page.list.length) { bad('项目列表为空', '演示数据没灌入——双击《生成演示数据.bat》'); return finish(); }
    project = page.list[0];
    ok('第3步 项目列表：共 ' + page.total + ' 个，首个「' + project.name + '」(id=' + project.id + ')');
  } catch (e) { bad('项目列表失败：' + e.message); return finish(); }

  try {
    const sum = await call('/field/summary?projectId=' + project.id + '&date=' + today, { headers: auth });
    const c = sum['daily-inspections'];
    const n = c.draft + c.submitted + c.approved + c.rejected;
    if (n > 0) ok('第4步 今日摘要-日常巡检：草稿' + c.draft + ' 待审核' + c.submitted + ' 通过' + c.approved + ' 退回' + c.rejected);
    else bad('今日摘要-日常巡检为 0', '演示数据是旧日期或已清空——双击《生成演示数据.bat》重新灌入（若提示“已存在”先运行《停止服务.bat》+《一键启动.bat》）');
  } catch (e) { bad('今日摘要失败：' + e.message); }

  try {
    const page = await call('/daily-inspections?projectId=' + project.id + '&authorId=' + userId + '&pageNum=1&pageSize=20', { headers: auth });
    if (page.total > 0) {
      ok('第5步 我的记录-日常巡检：' + page.total + ' 条');
      page.list.forEach(r => console.log('         ' + r.code + '  ' + r.inspectionDate + '  ' + r.location + '  [' + r.status + ']'));
    } else bad('我的记录-日常巡检为空', '当前账号不是巡检填报人。请用王强 13800000004 登录（admin 登录时“我的记录”为空属正常）');
  } catch (e) { bad('巡检列表失败：' + e.message); }
  finish();
})();

function finish() {
  console.log('\n' + (failed ? '共 ' + failed + ' 项未通过，请按上面“怎么办”处理后再运行本自检。'
    : '全部通过 ✅ 后端与数据正常。若小程序仍报错，请在微信开发者工具里：清缓存 → 清除全部缓存 → 编译，然后重新登录。'));
}
