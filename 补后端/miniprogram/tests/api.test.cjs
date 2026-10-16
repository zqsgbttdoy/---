// 运行：node --test 补后端/miniprogram/tests/api.test.cjs
// 使用 wx 桩执行 utils/api.js 源码，验证 Token 存储、请求头注入、401 处理与域名校验；不访问真实网络。
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const source = fs.readFileSync(path.join(__dirname, '../utils/api.js'), 'utf8');
function setup(baseUrl) {
  const state = { store: new Map(), requests: [] };
  const wx = {
    getStorageSync: key => state.store.get(key),
    setStorageSync(key, value) { state.store.set(key, value); },
    removeStorageSync(key) { state.store.delete(key); },
    request(options) { state.requests.push(options); if (state.reply) state.reply(options); },
    uploadFile(options) { state.requests.push(Object.assign({ upload: true }, options)); if (state.reply) state.reply(options); }
  };
  const config = { baseUrl };
  const context = { module: { exports: {} }, require: name => name === '../config' ? config : config, wx, console };
  vm.runInNewContext(source, context, { filename: 'api.js' });
  state.api = context.module.exports; state.wx = wx; state.config = config;
  state.login = (extra = {}) => state.api.saveSession(Object.assign({ token: 't-1', userId: 7, userName: '张三', roles: ['supervisor'] }, extra));
  return state;
}
const replyOk = (state, data) => { state.reply = o => o.success({ statusCode: 200, data: { code: 200, message: 'ok', data } }); };

test('本机 http 127.0.0.1 调试地址放行并拼接 /api 前缀', async () => {
  const s = setup('http://127.0.0.1:8091'); replyOk(s, { id: 1 });
  await s.api.request('/users', 'GET', { pageNum: 1 });
  assert.equal(s.requests[0].url, 'http://127.0.0.1:8091/api/users');
  assert.equal(s.requests[0].method, 'GET');
});
test('localhost 带端口同样放行；生产 http 域名仍拒绝', async () => {
  const ok = setup('http://localhost:8091'); replyOk(ok, {});
  await ok.api.request('/projects');
  assert.equal(ok.requests.length, 1);
  for (const bad of ['http://example.com', 'https://a.com/api', '', 'ftp://x']) {
    const s = setup(bad);
    await assert.rejects(s.api.request('/projects'), /HTTPS|配置/);
  }
});
test('登录后请求携带 Bearer Token，按域名隔离存储', async () => {
  const s = setup('https://api.example.com'); s.login();
  replyOk(s, { ok: true });
  await s.api.request('/auth/info');
  assert.equal(s.requests[0].header.Authorization, 'Bearer t-1');
  assert.equal(s.store.get('field:https://api.example.com:session').userId, 7);
  const other = setup('https://other.example.com');
  assert.equal(other.api.userId(), null);
});
test('HTTP 401 与业务 code 401 均清除会话并抛出可识别错误', async () => {
  const s = setup('https://api.example.com'); s.login();
  s.reply = o => o.success({ statusCode: 401, data: { code: 401, message: '过期' } });
  await assert.rejects(s.api.request('/users'), e => e.code === 401);
  assert.equal(s.api.userId(), null);
  s.login();
  s.reply = o => o.success({ statusCode: 200, data: { code: 401, message: '过期' } });
  await assert.rejects(s.api.request('/users'), e => e.code === 401);
  assert.equal(s.api.userId(), null);
});
test('业务 code 非 200 透传服务器消息，不吞错', async () => {
  const s = setup('https://api.example.com'); s.login();
  s.reply = o => o.success({ statusCode: 200, data: { code: 400, message: '参数不完整' } });
  await assert.rejects(s.api.request('/x', 'POST', {}), /参数不完整/);
});
test('字符串响应尝试 JSON 解析，乱码显式报错', async () => {
  const s = setup('https://api.example.com'); replyOk(s, null);
  s.reply = o => o.success({ statusCode: 200, data: '{"code":200,"data":{"a":1}}' });
  assert.deepEqual(JSON.parse(JSON.stringify(await s.api.request('/x'))), { a: 1 });
  s.reply = o => o.success({ statusCode: 200, data: '<html>502</html>' });
  await assert.rejects(s.api.request('/x'), /无法解析/);
});
test('网络失败给出可重试提示且不破坏会话', async () => {
  const s = setup('http://127.0.0.1:8091'); s.login();
  s.reply = o => o.fail(new Error('down'));
  await assert.rejects(s.api.request('/users'), /网络不可用/);
  assert.equal(s.api.userId(), '7');
});
test('isAdmin 仅在角色包含 admin 时为真', () => {
  const s = setup('https://api.example.com');
  assert.equal(s.api.isAdmin(), false);
  s.login(); assert.equal(s.api.isAdmin(), false);
  s.login({ roles: ['admin', 'supervisor'] }); assert.equal(s.api.isAdmin(), true);
  s.login({ roles: 'admin' }); assert.equal(s.api.isAdmin(), false);
});
test('allProjects 循环取完分页并保持顺序', async () => {
  const s = setup('https://api.example.com'); s.login();
  const pages = [{ list: [{ id: 1 }, { id: 2 }], total: 3 }, { list: [{ id: 3 }], total: 3 }];
  s.reply = o => o.success({ statusCode: 200, data: { code: 200, data: pages.shift() } });
  const list = await s.api.allProjects();
  assert.deepEqual(JSON.parse(JSON.stringify(list.map(p => p.id))), [1, 2, 3]);
  assert.equal(s.requests.length, 2);
});
test('上传携带令牌', async () => {
  const s = setup('https://api.example.com'); s.login();
  replyOk(s, { url: '/api/uploads/a.png' });
  const file = await s.api.upload('/local/a.png');
  assert.equal(s.requests[0].header.Authorization, 'Bearer t-1');
  assert.equal(file.url, '/api/uploads/a.png');
});
test('响应返回时账号已切换则拒绝交付结果', async () => {
  const s = setup('https://api.example.com'); s.login();
  let pending;
  s.reply = o => { pending = o.success; };
  const promise = s.api.request('/users');
  s.login({ userId: 8, token: 't-8' });
  pending({ statusCode: 200, data: { code: 200, data: {} } });
  await assert.rejects(promise, /身份已变化/);
});
test('GET 剔除 undefined/null 参数（真实 wx.request 会拼成字符串 "undefined"），POST 保留 null', async () => {
  const s = setup('http://127.0.0.1:8081'); s.login(); replyOk(s, { list: [], total: 0 });
  await s.api.request('/daily-inspections', 'GET', { projectId: 1, pageNum: 1, authorId: undefined, status: undefined, name: null, keyword: '' });
  assert.deepEqual(JSON.parse(JSON.stringify(s.requests[0].data)), { projectId: 1, pageNum: 1, keyword: '' });
  await s.api.request('/departments', 'POST', { name: 'A', parentId: null });
  assert.equal(s.requests[1].data.parentId, null);
});
