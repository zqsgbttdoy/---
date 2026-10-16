// 小程序适配层 · 本地真实接口冒烟（手动运行，不属于 node --test 套件）
// 前置：e2e 后端(8080) 与静态代理(8081) 已启动，参见 ../README.md 第二节。
// 运行：node 补后端/miniprogram/tests/live-smoke.cjs
// 用 fetch 桥接模拟 wx 环境，直接加载真实 utils/api.js 与 utils/reports.js，
// 按小程序页面实际调用顺序验证契约；包含部门与项目两次“建-改/查-删”闭环，不残留测试数据。
'use strict';
const fs = require('node:fs'), vm = require('node:vm'), path = require('node:path');
const base = 'http://127.0.0.1:8081';
const results = [];
function check(title, ok, actual) { results.push({ title, result: ok ? '通过' : '失败', actual }); console.log((ok ? '✔' : '✘') + ' ' + title + (ok ? '' : ' → ' + JSON.stringify(actual))); }
const store = new Map();
const wx = {
  getStorageSync: k => store.get(k),
  setStorageSync: (k, v) => store.set(k, v),
  removeStorageSync: k => store.delete(k),
  request(options) {
    // 与真实 wx.request 一致：GET 参数不做过滤，undefined/null 会变成字符串；剔除职责在 utils/api.js。
    const url = options.url + (options.method === 'GET' && options.data
      ? '?' + Object.entries(options.data).map(([k, v]) => encodeURIComponent(k) + '=' + encodeURIComponent(v)).join('&') : '');
    fetch(url, { method: options.method || 'GET', headers: options.header, body: options.method === 'GET' ? undefined : JSON.stringify(options.data || {}), signal: AbortSignal.timeout(20000) })
      .then(async res => options.success({ statusCode: res.status, data: await res.json() }))
      .catch(() => options.fail(new Error('network')));
  },
  uploadFile(options) { options.fail(new Error('冒烟不上传文件')); }
};
function load(rel, configOverride) {
  const context = { module: { exports: {} }, require: name => name.includes('config') ? (configOverride || cfg) : require(path.join(__dirname, '..', name)), wx, console, setTimeout, Promise };
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '..', rel), 'utf8'), context, { filename: rel });
  return context.module.exports;
}
const cfg = { baseUrl: base, voiceEnabled: false };
const api = load('utils/api.js', cfg);
const reports = require(path.join(__dirname, '..', 'utils/reports.js'));
const { schemas, today } = require(path.join(__dirname, '..', 'utils/schema.js'));
(async () => {
  const session = await api.request('/auth/login', 'POST', { username: 'admin', password: '123456' });
  api.saveSession(session);
  check('登录并存储会话（pages/login 同款链路）', api.userId() === String(session.userId) && api.isAdmin(), { userId: api.userId(), roles: session.roles });
  const users = await api.request('/users', 'GET', { pageNum: 1, pageSize: 20, status: 'active' });
  check('人员列表契约 list/total（pages/personnel）', Array.isArray(users.list) && typeof users.total === 'number', { total: users.total });
  const depts = await api.request('/departments', 'GET', { pageNum: 1, pageSize: 200 });
  check('部门列表可读（pages/departments，admin 令牌）', Array.isArray(depts.list), { total: depts.total });
  const stamp = Date.now();
  const created = await api.request('/departments', 'POST', { name: '小程序冒烟-' + stamp, parentId: null, leader: '测试', description: '自动创建，稍后删除', sortOrder: 999, status: 'active' });
  check('新增部门成功', created.id != null, { id: created.id });
  const updated = await api.request('/departments/' + created.id, 'PUT', { name: '小程序冒烟改-' + stamp, parentId: null, leader: '测试2', description: '', sortOrder: 998, status: 'active' });
  check('编辑部门成功', updated.name === '小程序冒烟改-' + stamp, { name: updated.name });
  await api.request('/departments/' + created.id, 'DELETE');
  const after = await api.request('/departments', 'GET', { pageNum: 1, pageSize: 200 });
  check('删除部门并确认无残留（冒烟自清理）', !after.list.some(d => d.id === created.id), { count: after.list.length });
  const projects = await api.allProjects();
  check('项目分页接口契约 list/total（工作台/报表数据源）', Array.isArray(projects), { count: projects.length });
  let smokeProject = null;
  let projectId = projects.length ? projects[0].id : null;
  if (!projectId) {
    smokeProject = await api.request('/projects', 'POST', { name: '小程序冒烟项目-' + stamp, code: 'MP-SMOKE-' + stamp, buildUnit: '冒烟测试单位（自动创建，用后即删）', status: 'active' });
    projectId = smokeProject.id;
    const refetch = await api.allProjects();
    check('新建冒烟项目后可被聚合取回（项目库写入链路）', refetch.some(p => p.id === projectId), { id: projectId, count: refetch.length });
  }
  if (projectId) {
    const dateTo = today(), dateFrom = reports.shiftDate(dateTo, -6);
    check('shiftDate 与后端 ISO 日期兼容', /^\d{4}-\d{2}-\d{2}$/.test(dateFrom), { dateFrom, dateTo });
    for (const kind of Object.keys(schemas)) {
      const rows = await reports.fetchAll(api.request, '/' + kind, { projectId, dateFrom, dateTo });
      const summary = reports.aggregate(rows);
      check('报表取数+聚合：' + schemas[kind].title, summary.total === rows.length && summary.authors.every(a => a.workDays <= a.total),
        { total: summary.total, authors: summary.authors.length });
    }
    const summary = await api.request('/field/summary', 'GET', { projectId, date: dateTo });
    check('今日摘要接口可用（pages/home）', summary && typeof summary === 'object', { keys: Object.keys(summary || {}).slice(0, 6) });
  }
  if (smokeProject) {
    await api.request('/projects/' + projectId, 'DELETE');
    const cleaned = await api.allProjects();
    check('删除冒烟项目并确认无残留（冒烟自清理）', !cleaned.some(p => p.id === projectId), { id: projectId });
  }
  // 401 行为：坏令牌必须走 api.js 的清会话分支
  api.saveSession({ token: 'bad', userId: session.userId, userName: 'x', roles: [] });
  await api.request('/users', 'GET', {}).then(() => check('无效令牌返回 401 并清除会话', false, '居然成功'), e => check('无效令牌返回 401 并清除会话', e.code === 401 && api.userId() === null, { code: e.code }));
  const failed = results.filter(r => r.result === '失败').length;
  console.log(JSON.stringify({ summary: { total: results.length, passed: results.length - failed, failed } }));
  process.exitCode = failed ? 1 : 0;
})().catch(e => { console.error('冒烟中断：', e.message); process.exitCode = 1; });
