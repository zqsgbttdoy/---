'use strict';
// 仅补充本地隔离测试证据；不覆盖原批次结果、不修改业务源码、不操作浏览器会话。
const fs = require('node:fs'), path = require('node:path');
const { randomUUID, createHash } = require('node:crypto');
const { spawnSync } = require('node:child_process');
const acceptance = process.argv.includes('--acceptance');
const regression = acceptance || process.argv.includes('--regression');
const batch = process.argv[2] || 'QA22-20260923052710';
if (!/^QA22-\d{14}$/.test(batch)) throw new Error('无效批次');
const stamp = new Date().toISOString().replace(/[-:TZ.]/g, '').slice(0, 17) + '-' + randomUUID().slice(0, 8);
const dir = path.join(__dirname, batch), out = path.join(dir, '补充复核-' + stamp);
const base = 'http://127.0.0.1:8091/api';
const results = [], requests = [], created = [];
let token;
function write(name, data) { fs.writeFileSync(path.join(out, name), JSON.stringify(data, null, 2), { encoding: 'utf8', flag: 'wx' }); }
function read(name) { return JSON.parse(fs.readFileSync(path.join(dir, name), 'utf8')); }
function check(title, valid, actual) { results.push({ id: 'S' + String(results.length + 1).padStart(3, '0'), title, result: valid ? '通过' : '失败', actual }); }
const ok = r => r.http === 200 && r.code === 200;
function brief(r) { return { http: r.http, code: r.code, message: r.message, id: r.data?.id, status: r.data?.status }; }
async function api(method, route, body, extra = {}) {
  const headers = { ...extra }; if (token) headers.Authorization = 'Bearer ' + token;
  if (body != null && !(body instanceof FormData)) headers['Content-Type'] = 'application/json';
  const response = await fetch(base + route, { method, headers, body: body == null ? undefined : body instanceof FormData ? body : JSON.stringify(body), signal: AbortSignal.timeout(20000), redirect: 'manual' });
  const json = await response.json();
  const result = { http: response.status, code: json.code, message: json.message, data: json.data };
  requests.push({ method, route, ...brief(result) });
  return result;
}
function manifest(directory, extensions, excluded) {
  const result = {};
  function visit(dirPath) {
    for (const entry of fs.readdirSync(dirPath, { withFileTypes: true })) {
      const file = path.join(dirPath, entry.name);
      if (file === excluded || entry.isSymbolicLink()) continue;
      if (entry.isDirectory()) visit(file);
      else if (!extensions || extensions.has(path.extname(file)))
        result[path.relative(directory, file)] = createHash('sha256').update(fs.readFileSync(file)).digest('hex');
    }
  }
  visit(directory);
  return result;
}
function aggregate() {
  const apiResults = read('接口测试结果.json'), pages = read('页面脚本验证.json');
  const modules = {};
  for (const r of apiResults.results) { const m = modules[r.module] ||= {}; m[r.result] = (m[r.result] || 0) + 1; }
  const reportDir = regression ? path.join(out, 'surefire') : path.join(__dirname, '..', '补后端', 'backend', 'target', 'surefire-reports');
  const suites = [];
  for (const file of (fs.existsSync(reportDir) ? fs.readdirSync(reportDir) : []).filter(f => f.endsWith('.txt'))) {
    const text = fs.readFileSync(path.join(reportDir, file), 'utf8');
    const match = text.match(/Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)/);
    if (match) suites.push({ file, tests: +match[1], failures: +match[2], errors: +match[3], skipped: +match[4], updatedAt: fs.statSync(path.join(reportDir, file)).mtime.toISOString() });
  }
  const totals = suites.reduce((a, s) => { for (const key of ['tests', 'failures', 'errors', 'skipped']) a[key] += s[key]; return a; }, { tests: 0, failures: 0, errors: 0, skipped: 0 });
  const summary = { batch, recordedAt: new Date().toISOString(), api: { counts: apiResults.counts, assertions: apiResults.results.length, requests: read('请求记录.json').length, modules }, pages: pages.counts, resources: read('页面资源可用性.json').length, fixtureEntries: read('模拟数据清单.json').fixtures.length, regression: { totals, suites } };
  summary.regression.execution = regression ? '本次独立进程全量回归，不连接旧HTTP服务' : '已有Surefire文件快照，不表示本次重跑';
  summary.regression.reportDirectory = reportDir;
  summary.api.note = '历史主批次统计，保持原结果，不与本次修复回归混算';
  if (regression) {
    const pageFile = path.join(out, 'pages', '页面脚本验证.json');
    summary.currentPages = fs.existsSync(pageFile) ? JSON.parse(fs.readFileSync(pageFile, 'utf8')).counts : null;
    const detailFile = path.join(out, '后端用例明细.json');
    if (fs.existsSync(detailFile)) {
      const cases = JSON.parse(fs.readFileSync(detailFile, 'utf8')).cases;
      summary.currentBackendGroups = {};
      for (const item of cases) {
        const counts = summary.currentBackendGroups[item.group] ||= {};
        counts[item.result] = (counts[item.result] || 0) + 1;
      }
    }
  }
  write('证据汇总.json', summary);
  write('异常断言索引.json', { api: apiResults.results.filter(r => r.result !== '通过'), pages: pages.results.filter(r => r.result !== '通过') });
  console.log(JSON.stringify(summary, null, 2));
}
async function live() {
  if (process.env.QA22_ISOLATED !== '1' || !process.env.QA_ADMIN_PASSWORD) throw new Error('补充写入必须确认本地e2e/H2并提供环境凭据');
  const auth = await api('POST', '/auth/login', { username: 'admin', password: process.env.QA_ADMIN_PASSWORD });
  if (!ok(auth) || !auth.data?.token) throw new Error('独立API认证失败，未改变浏览器会话');
  token = auth.data.token;
  const found = await api('GET', '/projects?code=' + encodeURIComponent(batch + '-A'));
  const project = found.data?.list?.find(p => p.code === batch + '-A');
  if (!ok(found) || !project) throw new Error('指定批次项目不存在，停止写入，不重建或重启环境');
  const form = new FormData();
  form.append('file', new Blob([Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aS9sAAAAASUVORK5CYII=', 'base64')], { type: 'image/png' }), 'QA22并发复核模拟图.png');
  const upload = await api('POST', '/files/upload', form);
  if (!ok(upload) || !upload.data?.url) throw new Error('专项照片上传失败');
  created.push({ type: '模拟照片', url: upload.data.url, note: '1×1有效PNG，仅传输测试，未删除上传文件' });
  for (let round = 1; round <= 4; round++) {
    const explicit = round < 4;
    const body = { projectId: project.id, diaryDate: '2026-09-22', weather: '晴', construction: 'QA22并发专项模拟，不是真实工程记录', photos: JSON.stringify([{ url: upload.data.url, description: '并发重放测试图' }]) };
    if (explicit) body.code = 'QA22C-' + stamp + '-' + round;
    const headers = { 'Idempotency-Key': randomUUID() };
    const replies = await Promise.all(Array.from({ length: 4 }, () => api('POST', '/supervision-diaries', body, headers)));
    const label = `第${round}组（${explicit ? '显式编号' : '自动编号'}）`;
    const ids = [...new Set(replies.filter(ok).map(r => r.data.id))];
    check(label + '并发同键请求均返回同一业务结果', replies.every(ok) && ids.length === 1, replies.map(brief));
    const replay = await api('POST', '/supervision-diaries', body, headers);
    check(label + '随后顺序重放返回原记录', ok(replay) && ids.length === 1 && replay.data.id === ids[0], brief(replay));
    const successful = replies.find(ok)?.data;
    if (!successful) continue;
    const query = await api('GET', '/supervision-diaries?projectId=' + project.id + '&code=' + encodeURIComponent(successful.code));
    check(label + '列表仅存在一条相同编号记录', ok(query) && query.data.total === 1 && query.data.list[0].id === successful.id, { total: query.data?.total, ids: query.data?.list?.map(r => r.id) });
    if (explicit) {
      const other = await api('POST', '/supervision-diaries', body, { 'Idempotency-Key': randomUUID() });
      check(label + '不同请求使用相同编号仍拒绝', other.http === 409, brief(other));
    }
    for (const id of ids) {
      const row = replies.find(r => r.data?.id === id).data;
      const removal = await api('DELETE', '/supervision-diaries/' + id, null, { 'X-Record-Version': String(row.lockVersion) });
      check(label + '仅清理本专项本人草稿', ok(removal), brief(removal));
      created.push({ type: '专项草稿', id, code: row.code, projectId: project.id, softDeleted: ok(removal) });
    }
  }
  // 与页面真实配置模型一致的样本；只在不存在同名配置时新增，绝不覆盖既有值。
  const configs = await api('GET', '/work-standard-configs?module=work-standard&pageSize=200');
  if (!ok(configs) || configs.data.total > configs.data.list.length) throw new Error('无法完整核对已有工作标准，停止配置写入');
  const existing = configs.data.list.find(r => r.name === 'diaryDeadline');
  if (existing) {
    const detail = await api('GET', '/work-standard-configs/' + existing.id);
    check('已有diaryDeadline仅只读核对，不覆盖既有值', ok(detail) && detail.data.standard === existing.standard,
      { id: existing.id, standard: detail.data?.standard, note: '只验证存取一致，不代表调度生效' });
  }
  else {
    const added = await api('POST', '/work-standard-configs', { name: 'diaryDeadline', module: 'work-standard', standard: '17:30', status: 'enabled', description: batch + '页面回显专项样本；仅H2，不代表定时任务生效' });
    check('新增符合页面模型的日记截止时间样本', ok(added), brief(added));
    if (ok(added)) {
      created.push({ type: '页面模型标准', id: added.data.id, name: 'diaryDeadline', module: 'work-standard', standard: '17:30' });
      const reloaded = await api('GET', '/work-standard-configs?module=work-standard&pageSize=200');
      check('页面同款查询可读回17:30', ok(reloaded) && reloaded.data.list.some(r => r.id === added.data.id && r.standard === '17:30'), { id: added.data.id, matching: reloaded.data?.list?.filter(r => r.id === added.data.id).map(r => ({ name: r.name, standard: r.standard })) });
    }
  }
}
function runRegression() {
  const root = path.resolve(__dirname, '..'), backend = path.join(root, '补后端', 'backend');
  const reportDir = path.join(out, 'surefire');
  const maven = process.env.QA_MAVEN || path.join(backend, '.mvn', 'maven', 'apache-maven-3.9.6', 'bin', 'mvn.cmd');
  if (!process.env.JAVA_HOME || !fs.existsSync(maven)) throw new Error('请提供可用Java 17+的JAVA_HOME及Maven路径QA_MAVEN');
  const env = { ...process.env, QA_MAVEN: maven, QA_POM: path.join(backend, 'pom.xml'), QA_REPO: path.join(root, '.m2-cache'), QA_REPORTS: reportDir };
  // PowerShell只执行固定命令；路径经环境变量传入，不拼入可执行脚本。
  const build = spawnSync('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command',
    '[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new(); & $env:QA_MAVEN -o -f $env:QA_POM "-Dmaven.repo.local=$env:QA_REPO" "-Dqa.reportsDirectory=$env:QA_REPORTS" test; exit $LASTEXITCODE'],
    { cwd: root, env, encoding: 'utf8', timeout: 600000, maxBuffer: 32 * 1024 * 1024 });
  fs.writeFileSync(path.join(out, '后端回归日志.txt'), (build.stdout || '') + (build.stderr || ''), 'utf8');
  check('当前源码后端全量回归', build.status === 0, { exitCode: build.status, error: build.error?.message, reports: reportDir });
  const details = [];
  for (const file of fs.existsSync(reportDir) ? fs.readdirSync(reportDir).filter(f => /^TEST-.*\.xml$/.test(f)) : []) {
    const suite = path.join(reportDir, file), xml = fs.readFileSync(suite, 'utf8');
    for (const m of xml.matchAll(/<testcase\b([^>]*?)(?:\/>|>([\s\S]*?)<\/testcase>)/g)) {
      const name = m[1].match(/\bname="([^"]+)"/)?.[1];
      const result = /<failure\b/.test(m[2] || '') ? '失败' : /<error\b/.test(m[2] || '') ? '错误' : /<skipped\b/.test(m[2] || '') ? '跳过' : '通过';
      details.push({ suite: file, name, result, group: name?.startsWith('qaAcceptance') ? '新增上线契约' : '既有回归' });
      if (name?.startsWith('qa22') || name?.startsWith('qaAcceptance'))
        check('后端专项：' + name, result === '通过', { test: name, result, evidence: suite });
    }
  }
  write('后端用例明细.json', { note: '逐项来自本次Surefire XML，不与摘要重复相加；新增契约失败保留为上线阻塞证据。', cases: details });
  const pageArgs = [path.join(__dirname, 'qa22-page-checks.cjs'), path.relative(__dirname, path.join(out, 'pages'))];
  pageArgs.push(acceptance ? '--security' : '--work-standard-only');
  const pages = spawnSync(process.execPath, pageArgs,
    { cwd: root, encoding: 'utf8', timeout: 60000, maxBuffer: 4 * 1024 * 1024 });
  fs.writeFileSync(path.join(out, '页面回归日志.txt'), (pages.stdout || '') + (pages.stderr || ''), 'utf8');
  check(acceptance ? '全部页面逻辑、工作标准、XSS专项与全页语法' : '工作标准原页面脚本专项与全页语法', pages.status === 0, { exitCode: pages.status, error: pages.error?.message });
  if (acceptance) {
    const mini = spawnSync(process.execPath, ['--test', path.join(root, '补后端', 'miniprogram', 'tests', 'drafts.test.cjs')],
      { cwd: root, encoding: 'utf8', timeout: 60000, maxBuffer: 4 * 1024 * 1024 });
    fs.writeFileSync(path.join(out, '小程序模拟测试日志.txt'), (mini.stdout || '') + (mini.stderr || ''), { encoding: 'utf8', flag: 'wx' });
    check('小程序草稿及接口模拟测试（非真机）', mini.status === 0, { exitCode: mini.status, error: mini.error?.message });
    for (const file of ['qa22-followup.cjs', 'qa22-page-checks.cjs', 'qa22-system-tests.cjs']) {
      const syntax = spawnSync(process.execPath, ['--check', path.join(__dirname, file)], { encoding: 'utf8' });
      check('测试器语法：' + file, syntax.status === 0, { exitCode: syntax.status, error: syntax.stderr });
    }
  }
  if (build.status !== 0 || pages.status !== 0) process.exitCode = 1;
}
async function main() {
  fs.mkdirSync(out, { recursive: false });
  if (['--acceptance', '--regression', '--live', '--archive-red'].filter(flag => process.argv.includes(flag)).length > 1)
    throw new Error('独立回归、旧服务写入与证据归档模式不能同时启用');
  if (process.argv.includes('--archive-red')) {
    const source = path.join(__dirname, '..', '补后端', 'backend', 'target', 'surefire-reports');
    const summary = fs.readFileSync(path.join(source, 'com.supervision.FieldWorkflowIntegrationTest.txt'), 'utf8');
    if (!summary.includes('Tests run: 3, Failures: 2, Errors: 1, Skipped: 0')) throw new Error('当前报告不是修复前三项失败证据，停止归档');
    for (const file of ['TEST-com.supervision.FieldWorkflowIntegrationTest.xml', 'com.supervision.FieldWorkflowIntegrationTest.txt'])
      fs.copyFileSync(path.join(source, file), path.join(out, file), fs.constants.COPYFILE_EXCL);
    results.push({ result: '证据归档', title: '修复前三项定向失败证据', actual: '仅复制已有报告；未运行测试，不计本次通过项' });
    return;
  }
  const previous = acceptance ? manifest(dir, null, out) : null;
  if (acceptance) {
    const source = path.join(__dirname, '..', '补后端');
    write('源码指纹.json', { time: new Date().toISOString(), algorithm: 'SHA-256',
      main: manifest(path.join(source, 'backend', 'src', 'main')),
      tests: manifest(path.join(source, 'backend', 'src', 'test')),
      deploy: manifest(path.join(source, 'deploy'), new Set(['.sh', '.conf', '.service', '.example'])),
      pages: Object.fromEntries(fs.readdirSync(source).filter(f => f.endsWith('.html')).map(f => [f, createHash('sha256').update(fs.readFileSync(path.join(source, f))).digest('hex')])),
      scripts: manifest(path.join(source, 'assets')),
      testRunners: Object.fromEntries(['qa22-followup.cjs', 'qa22-page-checks.cjs', 'qa22-system-tests.cjs'].map(f =>
        [f, createHash('sha256').update(fs.readFileSync(path.join(__dirname, f))).digest('hex')])) });
    const tables = Array.from(fs.readFileSync(path.join(source, 'backend', 'src', 'main', 'resources', 'schema.sql'), 'utf8')
      .matchAll(/^CREATE TABLE IF NOT EXISTS\s+(\w+)/gm), m => m[1]);
    const expected = Number(fs.readFileSync(path.join(source, 'deploy', 'init-database.sh'), 'utf8').match(/^EXPECTED_TABLES=(\d+)/m)?.[1]);
    write('部署静态核对.json', { note: '只比较模板与DDL，未运行初始化脚本或连接MySQL', tables, actual: tables.length, expected, consistent: tables.length === expected });
    write('执行环境.json', { node: process.version, javaHome: process.env.JAVA_HOME, maven: process.env.QA_MAVEN,
      mode: '隔离MVC/H2＋prod配置上下文（无数据源自动配置）＋页面DOM/API桩；不使用生产凭据、不连接旧HTTP服务' });
  }
  if (regression) runRegression();
  aggregate();
  if (acceptance) {
    const after = manifest(dir, null, out);
    const unchanged = JSON.stringify(previous) === JSON.stringify(after);
    write('历史证据完整性.json', { algorithm: 'SHA-256', unchanged, files: Object.keys(previous).length, before: previous, after });
    check('原批次及既有补充证据SHA-256保持不变', unchanged, { files: Object.keys(previous).length });
  }
  if (process.argv.includes('--live')) await live();
}
main().catch(e => { results.push({ result: '受阻', title: '专项中断', actual: e.message }); process.exitCode = 1; }).finally(() => {
  const counts = {}; results.forEach(r => counts[r.result] = (counts[r.result] || 0) + 1);
  if (results.some(r => r.result === '失败' || r.result === '受阻')) process.exitCode = 1;
  write('补充复核结果.json', { batch, time: new Date().toISOString(), mode: acceptance ? '生产前隔离验收' : regression ? '源码隔离回归' : '历史汇总或HTTP专项', note: '与原258项分开统计；MVC+H2不等于真实网络或MySQL验收；未改动旧批次JSON和旧服务数据。', counts, results, created, requests });
  console.log(JSON.stringify({ out, counts, requests: requests.length }, null, 2));
});

