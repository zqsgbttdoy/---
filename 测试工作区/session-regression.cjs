'use strict';
// 执行真实前端基座，用独立标签页存储与可控网络验证会话竞态；不依赖第三方包。
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.join(__dirname, '..', '补后端');
const source = fs.readFileSync(path.join(root, 'assets/app.js'), 'utf8');
let count = 0;
function check(name, fn) { fn(); count++; console.log('[PASS] ' + name); }
function storage(initial = {}, notify = () => {}) {
  const values = { ...initial };
  return new Proxy(values, {
    get(target, key) {
      if (key === 'getItem') return k => target[k] ?? null;
      if (key === 'setItem') return (k, v) => { target[k] = String(v); notify(k, String(v)); };
      if (key === 'removeItem') return k => { delete target[k]; notify(k, null); };
      if (key === 'key') return i => Object.keys(target)[i] ?? null;
      if (key === 'length') return Object.keys(target).length;
      return target[key];
    }
  });
}
const sharedValues = {};
const tabs = [];
function tab(session, pathname = '/login.html') {
  const listeners = {}, nodes = {};
  const element = () => ({ style: {}, textContent: '', innerHTML: '', dataset: {}, addEventListener() {},
    appendChild(el) { if (el.id) nodes[el.id] = el; }, replaceChildren() { this.cleared = true; } });
  const name = element(), role = element(), right = element(), avatar = element();
  const document = { readyState: 'loading', documentElement: element(), body: element(),
    addEventListener() {}, createElement: element,
    querySelector: s => s === '.header-right' ? right : null,
    querySelectorAll: s => s === '.user-name, .up-name' ? [name] : s === '.up-role' ? [role] : s.includes('.user-avatar') ? [avatar] : [],
    getElementById: id => nodes[id] || null };
  const localStorage = new Proxy(sharedValues, { get(target, key) {
    if (key === 'getItem') return k => target[k] ?? null;
    if (key === 'setItem') return (k, v) => { target[k] = String(v); tabs.filter(t => t !== context).forEach(t => t.emit('storage', { key: k, newValue: String(v) })); };
    if (key === 'removeItem') return k => { delete target[k]; };
    return target[key];
  } });
  const context = { document, localStorage, sessionStorage: session || storage(), console, Promise, FormData,
    atob: value => Buffer.from(value, 'base64').toString('binary'),
    location: { pathname, replace(url) { this.redirect = url; } },
    addEventListener(type, handler) { (listeners[type] ||= []).push(handler); },
    emit(type, event) { (listeners[type] || []).forEach(fn => fn(event)); },
    fetch: async () => ({ status: 200, json: async () => ({ code: 200, data: {} }) }),
    setTimeout, clearTimeout, nameNode: name, roleNode: role, nodes };
  context.window = context;
  tabs.push(context);
  vm.createContext(context);
  vm.runInContext(source, context);
  return context;
}
function login(userId, userName, roles = []) {
  const payload = Buffer.from(JSON.stringify({ jti: 'test-' + userId + '-' + Math.random(), exp: Math.floor(Date.now() / 1000) + 3600 })).toString('base64url');
  return { token: 'test.' + payload + '.signature', userId, userName, roles };
}
async function main() {
  sharedValues.token = 'legacy-token'; sharedValues.userName = '旧管理员'; sharedValues.roles = '["admin"]';
  const a = tab(), b = tab();
  check('不接受旧版共享登录缓存', () => { assert.equal(a.App.getToken(), ''); assert.equal(sharedValues.token, undefined); });
  const admin = login(1, 'admin', ['admin']), worker = login(2, '普通监理');
  a.App.saveSession(admin); b.App.saveSession(worker);
  check('两标签页账号及权限独立', () => { assert.equal(a.App.getUser().userName, 'admin'); assert.equal(b.App.getUser().userName, '普通监理'); assert.equal(b.App.getUser().roles.length, 0); });
  check('凭证从不写入共享存储', () => { assert.equal(Object.values(sharedValues).includes(admin.token), false); assert.equal(Object.values(sharedValues).includes(worker.token), false); });
  const refreshed = tab(a.sessionStorage);
  check('同标签页导航和刷新保留完整会话', () => assert.equal(refreshed.App.getToken(), admin.token));
  const copied = tab(storage({ 'supervision.session.v2': JSON.stringify(admin) }));
  copied.location.pathname = '/project-ledger.html';
  a.sessionStorage.setItem('userSettings', '{}'); a.sessionStorage.setItem('field-pc:test', '{"draft":true}');
  a.location.pathname = '/index.html';
  a.fetch = () => new Promise(() => {});
  a.App.logout();
  check('退出接口挂起也立即退出并清空个人缓存', () => { assert.equal(a.App.getToken(), ''); assert.equal(a.location.redirect, 'login.html'); assert.equal(a.document.body.cleared, true); assert.equal(a.sessionStorage.getItem('userSettings'), null); assert.equal(a.sessionStorage.getItem('field-pc:test'), null); });
  check('复制同一登录的页面收到失效通知', () => { assert.equal(copied.App.getToken(), ''); assert.equal(copied.location.redirect, 'login.html'); });
  check('其他独立账号不会被串改或退出', () => { assert.equal(b.App.getToken(), worker.token); assert.equal(b.App.getUser().roles.length, 0); });
  const c = tab(); c.App.saveSession(login(3, '旧用户'));
  let resolveJson;
  c.fetch = async () => ({ status: 200, json: () => new Promise(resolve => { resolveJson = resolve; }) });
  const pending = c.App.apiFetch('/auth/info').then(() => false, () => true);
  await Promise.resolve(); await Promise.resolve();
  c.App.saveSession(login(4, '新用户'));
  resolveJson({ code: 200, data: { userName: '旧用户', roles: ['admin'] } });
  const discarded = await pending;
  check('JSON解析期间切换账号，旧响应不可回写', () => { assert.equal(discarded, true); assert.equal(c.App.getUser().userName, '新用户'); });
  let resolve401;
  c.fetch = () => new Promise(resolve => { resolve401 = resolve; });
  const stale = c.App.apiFetch('/auth/info').catch(() => {});
  c.App.saveSession(login(5, '再次登录'));
  resolve401({ status: 401 }); await stale;
  check('旧请求的401不能踢掉新会话', () => assert.equal(c.App.getUser().userName, '再次登录'));
  const guard = tab(undefined, '/permission-settings.html');
  check('直接打开业务页无登录则隐藏并跳转', () => { assert.equal(guard.location.redirect, 'login.html'); assert.equal(guard.document.documentElement.style.visibility, 'hidden'); });
  const d = tab(); d.App.saveSession(login(6, '缓存旧名', ['admin']));
  d.fetch = async url => ({ status: 200, json: async () => ({ code: 200, data: url.endsWith('/auth/info') ? { userId: 6, userName: '权威姓名', roles: [] } : 0 }) });
  await d.App.initTopbar();
  check('顶部姓名和角色以当前后端身份为准', () => { assert.equal(d.nameNode.textContent, '权威姓名'); assert.equal(d.App.getUser().roles.length, 0); assert.equal(d.roleNode.textContent, '当前登录用户'); assert.ok(d.nodes.appLogout); });
  check('统一用户菜单包含资料/改密/日志/退出且默认收起', () => { assert.ok(d.nodes.appMenuProfile); assert.ok(d.nodes.appMenuPwd); assert.ok(d.nodes.appMenuLog); assert.equal(d.nodes.appUserMenu.style.display, 'none'); assert.ok(d.nodes.appChromeStyle); });
  d.location.pathname = '/index.html'; d.App.clearSession(); d.emit('pageshow', { persisted: true });
  check('浏览器后退恢复页面时重新执行守卫', () => assert.equal(d.location.redirect, 'login.html'));
  const e = tab(); e.App.saveSession(login(7, '离线用户')); e.location.pathname = '/index.html'; e.fetch = () => { throw new Error('网络已断开'); }; e.App.logout();
  check('同步网络异常不会阻止退出', () => assert.equal(e.location.redirect, 'login.html'));
  const pages = fs.readdirSync(root).filter(f => f.endsWith('.html') && !['login.html', 'welcome.html'].includes(f));
  check('24个业务页统一接入会话基座且脚本语法有效', () => {
    assert.equal(pages.length, 24);
    for (const page of pages) {
      const html = fs.readFileSync(path.join(root, page), 'utf8');
      assert.ok(html.includes('assets/app.js'), page);
      for (const script of html.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/gi)) if (!script[1].includes('src=')) new vm.Script(script[2]);
    }
  });
  console.log('SESSION TOTAL: ' + count + ' PASS: ' + count + ' FAIL: 0');
}
main().catch(error => { console.error('[FAIL]', error); process.exitCode = 1; });
