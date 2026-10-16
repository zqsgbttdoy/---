'use strict';
// 非浏览器验收：执行页面原始内联脚本，使用可控DOM桩验证统计逻辑；不改动页面。
const fs = require('node:fs'), path = require('node:path'), vm = require('node:vm');
const root = path.join(__dirname, '..', '补后端');
const out = path.resolve(__dirname, process.argv[2] || 'QA22-page-checks');
if (!out.startsWith(__dirname + path.sep)) throw new Error('输出必须在测试工作区');
if (fs.existsSync(path.join(out, '页面脚本验证.json'))) throw new Error('输出目录已有测试证据，请使用新目录');
fs.mkdirSync(out, { recursive: true });
const results = [];
function check(page, title, valid, actual) { const r = { id: 'P' + String(results.length + 1).padStart(3, '0'), page, title, result: valid ? '通过' : '失败', actual }; results.push(r); console.log(r.id, r.result, page, title); }
const pages = ['index', 'welcome', 'project-ledger', 'daily-inspection', 'sideline-inspection', 'supervision-notice', 'work-contact', 'supervision-diary', 'supervision-log', 'safety-log', 'meeting-minutes', 'supervision-monthly-report', 'sub-item-database', 'project-data-upload', 'diary-statistics', 'log-statistics', 'safety-log-statistics', 'diary-report', 'message-center', 'department-management', 'personnel-management', 'permission-settings', 'process-management', 'operation-log', 'work-standard-config', 'login'];
function inline(page) { return Array.from(fs.readFileSync(path.join(root, page + '.html'), 'utf8').matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/gi)).filter(m => !/\bsrc=/.test(m[1])).map(m => m[2]); }
for (const page of pages) { try { const scripts = inline(page); scripts.forEach(s => new vm.Script(s)); check(page, '内联JavaScript语法检查', true, { scripts: scripts.length }); } catch (e) { check(page, '内联JavaScript语法检查', false, e.message); } }
for (const f of ['assets/app.js', 'assets/field-records.js', 'miniprogram/utils/schema.js']) { try { new vm.Script(fs.readFileSync(path.join(root, f), 'utf8')); check(f, '共享脚本语法检查', true); } catch (e) { check(f, '共享脚本语法检查', false, e.message); } }
function escape(v) { return String(v ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;'); }
function environment(page) {
  const nodes = new Map(), calls = [], errors = [], downloads = [];
  function node(id) {
    if (!nodes.has(id)) { let html = '', text = ''; const classes = new Set(); nodes.set(id, { id, value: '', style: {}, dataset: {}, nextElementSibling: { textContent: '' }, classList: { add(c) { classes.add(c); }, remove(c) { classes.delete(c); }, contains(c) { return classes.has(c); }, toggle(c) { if (classes.has(c)) classes.delete(c); else classes.add(c); } }, appendChild() {}, click() {}, addEventListener() {}, querySelectorAll: () => [], querySelector: s => node(id + ' ' + s),
      get innerHTML() { return html; }, set innerHTML(v) { html = String(v); }, get textContent() { return text; }, set textContent(v) { text = String(v); html = escape(v); } }); }
    return nodes.get(id);
  }
  const document = { getElementById: node, createElement: tag => node('created-' + tag + '-' + nodes.size), querySelector: node, querySelectorAll: s => [0, 1, 2, 3].map(i => node(s + i)), addEventListener() {} };
  const context = { window: { location: { search: '' } }, document, App: { escape, toast() {} }, console: { log() {}, error: (...a) => errors.push(a.join(' ')) }, Date, JSON, String, Number, Math, Promise, URLSearchParams, Blob, alert: m => errors.push(m),
    URL: { createObjectURL(blob) { downloads.push(blob); return 'blob:mock'; }, revokeObjectURL() {} },
    apiGetPage: async (route, params) => { calls.push({ route, params }); return { list: [], total: 0 }; } };
  vm.createContext(context); inline(page).forEach(s => vm.runInContext(s, context));
  return { context, node, calls, errors, downloads };
}
async function workStandardChecks() {
  const page = 'work-standard-config', s = environment(page), c = s.context;
  c.window.App = c.App; c.App.toast = message => s.errors.push(message);
  const defaults = { diaryDeadline: '18:00', missingTime: 'next_day_morning', remindBefore: '60', noticeReplyDays: '3', urgeFrequency: 'daily', minInspection: '2', restDayRule: 'not_required' };
  Object.entries(defaults).forEach(([id, value]) => { s.node(id).value = value; });
  const sample = { id: 987, name: 'diaryDeadline', module: 'work-standard', standard: '17:30', updateTime: '2026-09-24T10:00:00' };
  c.apiGetPage = c.window.apiGetPage = async () => ({ list: [sample], total: 1 });
  await c.loadConfigs();
  check(page, '已存截止时间同步到控件和预览', s.node('diaryDeadline').value === '17:30' && s.node('previewDeadline').textContent === '17:30');
  check(page, '提前提醒按真实配置计算', s.node('previewReminder').textContent === '16:30' && s.node('previewLead').textContent === '60分钟');
  check(page, '缺失预览与选项一致', s.node('previewMissing').textContent === '次日 09:00');
  check(page, '更新时间来自接口', s.node('configUpdateInfo').textContent.includes('2026-09-24 10:00:00'));
  s.node('diaryDeadline').value = '19:00'; s.node('missingTime').value = 'next_day_noon'; c.updatePreview();
  check(page, '编辑配置后预览联动', s.node('previewDeadline').textContent === '19:00' && s.node('previewReminder').textContent === '18:00' && s.node('previewMissing').textContent === '次日 12:00');
  let writes = 0;
  c.apiFetch = c.window.apiFetch = async (route, options) => { writes++; return { ...options.body, id: options.body.name === 'diaryDeadline' ? 987 : 1000 + writes, updateTime: '2026-09-24T11:00:00' }; };
  await Promise.all([c.saveConfig(), c.saveConfig()]);
  check(page, '保存成功更新元数据且重复点击不重复发起', writes === 11 && s.node('saveModal').classList.contains('show') && s.node('configUpdateInfo').textContent.includes('11:00:00'), { writes });
  c.closeModal('saveModal');
  c.apiFetch = c.window.apiFetch = async () => { throw new Error('权限不足'); };
  await c.saveConfig();
  check(page, '权限失败不显示保存成功', !s.node('saveModal').classList.contains('show') && s.errors.some(e => e.includes('权限不足')));
  c.window.apiFetch = null; await c.saveConfig();
  check(page, '接口缺失不伪报保存成功', !s.node('saveModal').classList.contains('show'));
  c.window.apiFetch = c.apiFetch;
  c.apiGetPage = c.window.apiGetPage = async () => { throw new Error('连接失败'); };
  await c.loadConfigs();
  check(page, '读取失败禁止保存而非当作默认值', c.configLoaded === false && s.node('configUpdateInfo').textContent.includes('禁止保存'));
  c.apiGetPage = c.window.apiGetPage = async () => ({ list: [sample], total: 201 }); await c.loadConfigs();
  check(page, '未完整加载时禁止保存', c.configLoaded === false);
  c.apiGetPage = c.window.apiGetPage = async () => ({ list: [sample, { ...sample, id: 988 }], total: 2 }); await c.loadConfigs();
  check(page, '同名配置冲突显式拒绝保存', c.configLoaded === false && s.errors.some(e => e.includes('同名配置')));
  c.confirmReset();
  check(page, '重置同步预览且不自动保存', s.node('previewDeadline').textContent === '18:00' && s.node('previewReminder').textContent === '17:00' && s.errors.includes('表单已重置为默认值，尚未保存'));
  const html = fs.readFileSync(path.join(root, page + '.html'), 'utf8');
  check(page, '未实现调度明确告知且无立即生效承诺', html.includes('自动调度尚未实现') && html.includes('尚未接入保存，已禁用') && !html.includes('立即生效'));
  check(page, '预览和元数据拥有真实DOM锚点', ['previewDeadline', 'previewReminder', 'previewLead', 'previewMissing', 'configUpdateInfo'].every(id => html.includes('id="' + id + '"')));
}
function securityChecks() {
  const runtime = { window: {}, document: { readyState: 'loading', addEventListener() {} } };
  vm.createContext(runtime);
  vm.runInContext(fs.readFileSync(path.join(root, 'assets/app.js'), 'utf8'), runtime);
  const actualEscape = runtime.window.App.escape;
  check('assets/app.js', '实际App.escape编码HTML特殊字符', actualEscape('<>&"\'') === '&lt;&gt;&amp;&quot;&#39;');
  const safety = environment('safety-log'), sc = safety.context;
  sc.App.escape = actualEscape;
  const marker = '<img src=x onerror="window.__qaXss=1">';
  sc.filteredData = [sc.mapLog({ id: 7, code: marker, projectId: 1, logDate: '2026-09-23', status: 'draft' })];
  sc.renderAllActions = () => {};
  sc.renderTable();
  const rendered = safety.node('tbody').innerHTML;
  check('safety-log', '日志编号以文本输出不得进入可执行HTML', rendered.includes(actualEscape(marker)) && !rendered.includes(marker),
    { rawMarkupObserved: rendered.includes(marker), note: '原渲染函数输出检查，未打开浏览器或发送攻击请求' });
  const project = environment('project-ledger'), pc = project.context;
  pc.App.escape = actualEscape;
  pc.filteredData = [{ id: 7, code: 'QA', name: "');window.__qaXss=1;//", status: 'active' }];
  pc.renderTable();
  const html = project.node('tableBody').innerHTML;
  const handler = Array.from(html.matchAll(/onclick="([^"]*)"/g)).map(m => m[1]).find(s => s.includes('confirmDelete'));
  const probe = {};
  if (handler) {
    const decoded = handler.replace(/&#(?:0*39|x0*27);|&apos;/gi, "'").replace(/&quot;/g, '"').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&amp;/g, '&');
    vm.runInNewContext(decoded, { window: probe, confirmDelete() {} }, { timeout: 1000 });
  }
  check('project-ledger', '项目名不得突破onclick的JavaScript字符串上下文', probe.__qaXss !== 1,
    { markerExecutedInSandbox: probe.__qaXss === 1, note: '模拟HTML实体解码后，仅在隔离VM执行标记赋值；不访问真实页面或令牌' });
}
function finish() {
  const counts = {}; results.forEach(r => counts[r.result] = (counts[r.result] || 0) + 1);
  fs.writeFileSync(path.join(out, '页面脚本验证.json'), JSON.stringify({ note: '原页面内联JS+DOM桩/接口桩，不是浏览器渲染/点击测试，也不是真实大量数据负载测试。', counts, results }, null, 2));
  console.log(JSON.stringify(counts));
  if (counts['失败']) process.exitCode = 1;
}
async function main() {
  if (process.argv.includes('--work-standard-only')) { await workStandardChecks(); finish(); return; }
  for (const page of ['log-statistics', 'safety-log-statistics']) {
    const s = environment(page), c = s.context, today = c.getTodayStr();
    const drafts = c.aggregate([{ projectId: 1, logDate: today, status: 'draft' }]);
    check(page, '当日只有草稿不得显示已提交', drafts[0].todaySubmitted === false, drafts);
    const duplicates = c.aggregate([{ projectId: 1, logDate: today, status: 'approved' }, { projectId: 1, logDate: today, status: 'approved' }]);
    check(page, '同一天两条记录不能当作两个应写工作日', duplicates[0].totalDays === 1, duplicates);
    c.projectData = drafts; c.renderTable();
    check(page, '渲染后的草稿行应显示未提交', s.node('tableBody').innerHTML.includes('○ 未提交'), s.node('tableBody').innerHTML);
    c.projectsMap = { 1: '模拟A项目', 2: '模拟B项目（无记录）' };
    check(page, '无记录项目应纳入未提交统计', c.aggregate([]).length === 2, c.aggregate([]));
    s.context.apiGetPage = async (route, params) => { s.calls.push({ route, params }); return { list: [{ projectId: 1, logDate: today, status: 'approved' }], total: 2001, pageNum: 1, pageSize: 2000 }; };
    await c.loadData(); check(page, '总数超过第一页时不能静默少计', s.calls.length > 1 || s.errors.length > 0, { requests: s.calls, displayedRows: c.rawLogs.length, totalFromApi: 2001, errors: s.errors });
  }
  const diary = environment('diary-statistics');
  diary.context.usersMap = { 1: '模拟监理甲' }; diary.context.projectsMap = { 1: '模拟项目A' };
  diary.context.renderStats([{ projectId: 1, authorId: 1, status: 'approved', diaryDate: '2026-09-23' }, { projectId: 1, authorId: 1, status: 'draft', diaryDate: '2026-09-23' }]);
  check('diary-statistics', '已通过和草稿数量分别展示', diary.node('statApproved').textContent === '1' && diary.node('statDraft').textContent === '1', { approved: diary.node('statApproved').textContent, draft: diary.node('statDraft').textContent });
  check('diary-statistics', '同人同日多记录不能直接当作应写天数', !diary.node('statBody').innerHTML.includes('<td>2</td>'), diary.node('statBody').innerHTML);
  diary.context.apiGetPage = async (route, params) => { diary.calls.push({ route, params }); return { list: [{ projectId: 1, authorId: 1, status: 'approved', diaryDate: '2026-09-23' }], total: 1001 }; };
  diary.context.loadData(); await Promise.resolve(); await Promise.resolve();
  check('diary-statistics', '超过1000条应继续取数或显式报错', diary.calls.length > 1 || diary.errors.length > 0, { calls: diary.calls, shown: diary.node('statTotal').textContent, actualTotal: 1001 });
  const report = environment('diary-report');
  report.context.apiGetPage = async (route, params) => { report.calls.push({ route, params }); return { list: [{ id: 123, authorId: 1, projectId: 1, status: 'approved', diaryDate: '2026-09-23', weather: '晴', photos: '["/api/uploads/test.png"]', attachments: '[]' }], total: 1001 }; };
  await report.context.loadData();
  check('diary-report', '状态与附件数量正确渲染', report.node('reportTableBody').innerHTML.includes('通过') && report.context.filteredData[0].relatedCount === 1, report.node('reportTableBody').innerHTML);
  check('diary-report', '查看日记链接应携带该条记录ID', /(?:id|recordId)=123/.test(report.node('reportTableBody').innerHTML), report.node('reportTableBody').innerHTML);
  check('diary-report', '报表不能忽略超过1000条的后续记录', report.calls.length > 1 || report.errors.length > 0, { calls: report.calls, displayed: report.context.filteredData.length, actualTotal: 1001 });
  report.context.exportExcel(); const csv = await report.downloads[0].text();
  check('diary-report', '可生成包含真实渲染记录的CSV文本', csv.includes('2026-09-23') && csv.includes('通过'), csv);
  fs.writeFileSync(path.join(out, '日记报表-受控脚本模拟导出.csv'), '\uFEFF' + csv);
  report.context.filteredData[0].weather = '晴,间多云'; report.context.exportExcel();
  const commaCsv = await report.downloads[1].text(); check('diary-report', 'CSV逗号字段应加引号以防列错位', commaCsv.includes('"晴,间多云"'), commaCsv);
  await workStandardChecks();
  if (process.argv.includes('--security')) securityChecks();
  finish();
}
main().catch(e => { results.push({ result: '受阻', title: '页面测试器中断', actual: e.message }); finish(); console.error(e); process.exitCode = 1; });
