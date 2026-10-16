// 运行：node --test 补后端/miniprogram/tests/drafts.test.cjs
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const schema = require('../utils/schema');
const source = fs.readFileSync(path.join(__dirname, '../utils/drafts.js'), 'utf8');
const copy = value => value === undefined ? undefined : JSON.parse(JSON.stringify(value));
const error = (code, message = '请求失败') => Object.assign(new Error(message), { code });
function deferred() { let resolve; const promise = new Promise(r => { resolve = r; }); return { promise, resolve }; }
function setup() {
  const state = { uid: '1', store: new Map(), calls: [], uploads: [], removed: [], foreground: true, quota: false, server: null };
  const wx = {
    env: { USER_DATA_PATH: '/persistent' },
    getStorageSync: key => copy(state.store.get(key)),
    setStorageSync(key, value) { if (state.quota) throw new Error('quota'); state.store.set(key, copy(value)); },
    getFileSystemManager: () => ({ copyFile: options => options.success(), unlink: options => state.removed.push(options.filePath) })
  };
  const api = {
    namespace: () => 'test-domain', userId: () => state.uid,
    assertUser(uid) { if (!uid || state.uid !== String(uid)) throw new Error('账号已变化'); },
    async upload(file) { state.uploads.push(file); return { url: '/api/uploads/a.jpg', name: 'a.jpg', size: 1 }; },
    async request(url, method = 'GET', body, headers) {
      state.calls.push(copy({ url, method, body, headers }));
      if (method === 'GET') return copy(state.server);
      state.server = Object.assign({}, body, { id: 10, lockVersion: state.server ? state.server.lockVersion + 1 : 0, status: url.endsWith('/submit') ? 'submitted' : 'draft' });
      return copy(state.server);
    }
  };
  function load() {
    const context = { module: { exports: {} }, require: name => name === './api' ? api : schema, wx, getApp: () => ({ globalData: { foreground: state.foreground } }), console };
    vm.runInNewContext(source, context, { filename: 'drafts.js' });
    return context.module.exports;
  }
  state.drafts = load(); state.api = api; state.wx = wx; state.reload = () => { state.drafts = load(); }; return state;
}
const body = () => ({ projectId: 2, diaryDate: '2026-09-23', construction: '现场原文', photos: '[]', attachments: '[]' });
const create = state => state.drafts.create('supervision-diaries', body());

test('离线草稿重启恢复并按账号隔离', () => {
  const s = setup(), d = create(s); s.reload();
  assert.equal(s.drafts.get(d.localId).body.construction, '现场原文');
  s.uid = '2'; assert.equal(s.drafts.list().length, 0);
  s.uid = '1'; assert.equal(s.drafts.list().length, 1);
});
test('新建响应丢失后重放同一正文与 key，保留后续编辑', async () => {
  const s = setup(), d = create(s), request = s.api.request; let lost = true;
  s.api.request = async (...args) => {
    if (args[1] === 'POST' && lost) { lost = false; await request(...args); throw error(undefined, '响应丢失'); }
    if (args[1] === 'POST') { s.calls.push(copy({ method: args[1], body: args[2], headers: args[3] })); return copy(s.server); }
    return request(...args);
  };
  await assert.rejects(s.drafts.sync(d.localId));
  s.drafts.edit(d.localId, { body: Object.assign(body(), { construction: '新内容' }) });
  await s.drafts.sync(d.localId);
  assert.deepEqual(s.calls[0].body, s.calls[1].body);
  assert.deepEqual(s.calls[0].headers, s.calls[1].headers);
  assert.equal(s.drafts.get(d.localId).dirty, true);
  await s.drafts.sync(d.localId);
  assert.equal(s.server.construction, '新内容'); assert.equal(s.drafts.get(d.localId).dirty, false);
});
test('服务器生成编号回填，旧页面正文不能清掉已分配编号', async () => {
  const s = setup(), d = create(s), request = s.api.request;
  s.api.request = async (...args) => Object.assign(await request(...args), { code: 'DIARY-assigned' });
  await s.drafts.sync(d.localId);
  assert.equal(s.drafts.get(d.localId).body.code, 'DIARY-assigned');
  s.drafts.edit(d.localId, { body: body() });
  await s.drafts.sync(d.localId);
  assert.equal(s.calls[1].body.code, 'DIARY-assigned');
});
test('移除已上传本机照片同步时不残留旧引用', async () => {
  const s = setup(), d = create(s);
  s.drafts.edit(d.localId, { media: [{ id: 'p1', path: '/persistent/a.jpg' }] });
  await s.drafts.sync(d.localId);
  const saved = Object.assign(body(), { photos: s.server.photos });
  s.drafts.edit(d.localId, { body: saved, media: [] });
  await s.drafts.sync(d.localId);
  assert.deepEqual(JSON.parse(s.server.photos), []);
});
test('服务端 400 明确拒绝后可纠正文案再保存', async () => {
  const s = setup(), d = create(s), request = s.api.request;
  s.api.request = async () => { throw error(400); };
  await assert.rejects(s.drafts.sync(d.localId));
  assert.equal(s.drafts.get(d.localId).pending, null);
  s.drafts.edit(d.localId, { body: Object.assign(body(), { construction: '纠正内容' }) });
  s.api.request = request; await s.drafts.sync(d.localId);
  assert.equal(s.server.construction, '纠正内容');
});
test('并行同步同草稿只有一个在途任务', async () => {
  const s = setup(), d = create(s), gate = deferred(), request = s.api.request;
  s.api.request = async (...args) => { await gate.promise; return request(...args); };
  const a = s.drafts.sync(d.localId), b = s.drafts.sync(d.localId);
  assert.equal(a, b); gate.resolve(); await a; assert.equal(s.calls.length, 1);
});
test('版本冲突保留原 pending，自动同步不覆盖', async () => {
  const s = setup(); const d = s.drafts.create('supervision-diaries', body(), { id: 10, status: 'draft', lockVersion: 0 });
  s.drafts.edit(d.localId, { body: Object.assign(body(), { construction: '手机稿' }) });
  s.api.request = async (url, method) => { if (method === 'PUT') throw error(409); return { construction: 'PC稿', id: 10, lockVersion: 1 }; };
  await assert.rejects(s.drafts.sync(d.localId));
  assert.equal(s.drafts.get(d.localId).state, '内容冲突');
  assert.ok(s.drafts.get(d.localId).pending); await s.drafts.syncQueued();
  assert.equal(s.drafts.get(d.localId).body.construction, '手机稿');
});
test('更新响应丢失后仅接纳服务器完全相同内容', async () => {
  const s = setup(); const d = s.drafts.create('supervision-diaries', body(), { id: 10, status: 'draft', lockVersion: 0 });
  s.drafts.edit(d.localId, { body: body() }); let applied = false;
  s.api.request = async (url, method, input) => {
    if (method === 'PUT' && !applied) { applied = true; s.server = Object.assign({}, input, { id: 10, lockVersion: 1 }); throw error(undefined); }
    if (method === 'PUT') throw error(409);
    return copy(s.server);
  };
  await assert.rejects(s.drafts.sync(d.localId)); await s.drafts.sync(d.localId);
  assert.equal(s.drafts.get(d.localId).version, 1); assert.equal(s.drafts.get(d.localId).pending, null);
});
test('页面旧照片对象不能覆盖同步过程已确认的 URL', async () => {
  const s = setup(), d = create(s), gate = deferred(), sent = deferred(), request = s.api.request;
  const photo = { id: 'p1', path: '/persistent/a.jpg', description: '' };
  s.drafts.edit(d.localId, { media: [photo] });
  s.api.request = async (...args) => { sent.resolve(); await gate.promise; return request(...args); };
  const work = s.drafts.sync(d.localId); await sent.promise;
  s.drafts.edit(d.localId, { media: [photo], body: Object.assign(body(), { construction: '上传时编辑' }) });
  assert.equal(s.drafts.get(d.localId).media[0].url, '/api/uploads/a.jpg');
  gate.resolve(); await work; await s.drafts.sync(d.localId);
  assert.equal(s.uploads.length, 1); assert.equal(s.server.construction, '上传时编辑');
});
test('同步中新增照片也必须上传后才保存', async () => {
  const s = setup(), d = create(s), gate = deferred(), entered = deferred();
  s.drafts.edit(d.localId, { media: [{ id: 'p1', path: '/persistent/1.jpg' }] });
  s.api.upload = async file => { s.uploads.push(file); if (s.uploads.length === 1) { entered.resolve(); await gate.promise; } return { url: '/api/uploads/' + s.uploads.length + '.jpg' }; };
  const work = s.drafts.sync(d.localId); await entered.promise;
  s.drafts.edit(d.localId, { media: s.drafts.get(d.localId).media.concat({ id: 'p2', path: '/persistent/2.jpg' }) });
  gate.resolve(); await work;
  assert.equal(s.uploads.length, 2); assert.equal(JSON.parse(s.server.photos).length, 2);
});
test('提交期间编辑不丢失，显示冲突而非全部同步', async () => {
  const s = setup(), d = create(s), gate = deferred(), entered = deferred(), request = s.api.request;
  s.api.request = async (...args) => { if (args[0].endsWith('/submit')) { entered.resolve(); await gate.promise; } return request(...args); };
  const work = s.drafts.submit(d.localId); await entered.promise;
  s.drafts.edit(d.localId, { body: Object.assign(body(), { construction: '提交期间新写' }) });
  gate.resolve(); const result = await work;
  assert.equal(result.state, '内容冲突'); assert.equal(result.body.construction, '提交期间新写');
});
test('后台不启动新同步，登录过期保留原账号草稿', async () => {
  const s = setup(), d = create(s); s.foreground = false; await s.drafts.syncQueued(); assert.equal(s.calls.length, 0);
  s.foreground = true; s.api.request = async () => { s.uid = null; throw error(401); };
  await assert.rejects(s.drafts.sync(d.localId)); s.uid = '1';
  assert.ok(s.drafts.get(d.localId).pending); assert.equal(s.drafts.get(d.localId).body.construction, '现场原文');
});
test('存储不足抛错且保留上次成功的草稿', () => {
  const s = setup(), d = create(s); s.quota = true;
  assert.throws(() => s.drafts.edit(d.localId, { body: Object.assign(body(), { construction: '未落盘内容' }) }), /存储不足/);
  s.quota = false; assert.equal(s.drafts.get(d.localId).body.construction, '现场原文');
});
test('照片存入持久目录，清理已同步稿不删冲突副本照片', async () => {
  const s = setup(), d = create(s), photo = await s.drafts.persistPhoto('/temp/a.jpg');
  assert.match(photo.path, /^\/persistent\/field-/);
  s.drafts.edit(d.localId, { media: [photo] }); await s.drafts.sync(d.localId);
  const fork = s.drafts.fork(d.localId); s.drafts.forget(d.localId);
  assert.equal(s.removed.length, 0); assert.equal(s.drafts.get(fork.localId).media[0].path, photo.path);
});

test('刷新服务器草稿后不能恢复 PC 已移除的照片', async () => {
  const s = setup(), d = create(s);
  s.drafts.edit(d.localId, { media: [{ id: 'p1', path: '/persistent/a.jpg' }] });
  await s.drafts.sync(d.localId);
  s.drafts.create(d.kind, body(), { id: 10, status: 'draft', lockVersion: 1 });
  assert.equal(s.drafts.get(d.localId).media.length, 0);
  s.drafts.edit(d.localId, { body: body() }); await s.drafts.sync(d.localId);
  assert.deepEqual(JSON.parse(s.server.photos), []);
});
test('刷新服务器草稿同步照片说明，不以本机旧说明覆盖', async () => {
  const s = setup(), d = create(s);
  s.drafts.edit(d.localId, { media: [{ id: 'p1', path: '/persistent/a.jpg', description: '旧说明' }] });
  await s.drafts.sync(d.localId);
  const updated = Object.assign(body(), { photos: JSON.stringify([{ url: '/api/uploads/a.jpg', description: 'PC补充' }]) });
  s.drafts.create(d.kind, updated, { id: 10, status: 'rejected', lockVersion: 1, rejectReason: '补充部位' });
  assert.equal(s.drafts.get(d.localId).media[0].description, 'PC补充');
  assert.equal(s.drafts.get(d.localId).rejectReason, '补充部位');
  s.drafts.edit(d.localId, { body: updated }); await s.drafts.sync(d.localId);
  assert.equal(JSON.parse(s.server.photos)[0].description, 'PC补充');
});
test('页面存储失败暂停自动同步，重新落盘后继续', async () => {
  const s = setup(), d = create(s), changed = Object.assign(body(), { construction: '内存新内容' });
  s.quota = true; assert.throws(() => s.drafts.edit(d.localId, { body: changed }));
  s.quota = false; await s.drafts.syncQueued(); assert.equal(s.calls.length, 0);
  await assert.rejects(s.drafts.sync(d.localId), /未保存修改/);
  s.drafts.edit(d.localId, { body: changed }); await s.drafts.syncQueued();
  assert.equal(s.server.construction, '内存新内容');
});
test('在途响应不能把存储失败的新编辑标成已同步或清除', async () => {
  const s = setup(), d = create(s), gate = deferred(), entered = deferred(), request = s.api.request;
  s.api.request = async (...args) => { entered.resolve(); await gate.promise; return request(...args); };
  const work = s.drafts.sync(d.localId); await entered.promise;
  s.quota = true; assert.throws(() => s.drafts.edit(d.localId, { body: Object.assign(body(), { construction: '尚未落盘' }) }));
  s.quota = false; gate.resolve(); await work;
  assert.equal(s.drafts.get(d.localId).dirty, true);
  assert.throws(() => s.drafts.forget(d.localId), /未保存修改/);
  await s.drafts.syncQueued(); assert.equal(s.calls.length, 1);
});

test('已通过和已退回本机副本保留真实业务状态提示', () => {
  const s = setup();
  const d = s.drafts.create('supervision-diaries', body(), { id: 10, status: 'approved', lockVersion: 2 });
  assert.equal(d.state, '审核通过');
  const refreshed = s.drafts.create(d.kind, body(), { id: 10, status: 'rejected', lockVersion: 3, rejectReason: '补充' });
  assert.equal(refreshed.state, '已退回'); assert.equal(refreshed.rejectReason, '补充');
});
test('提交期间新编辑存储失败不能误报本机内容全部已提交', async () => {
  const s = setup(), d = create(s), gate = deferred(), entered = deferred(), request = s.api.request;
  s.api.request = async (...args) => { if (args[0].endsWith('/submit')) { entered.resolve(); await gate.promise; } return request(...args); };
  const work = s.drafts.submit(d.localId); await entered.promise;
  s.quota = true; assert.throws(() => s.drafts.edit(d.localId, { body: Object.assign(body(), { construction: '提交时新编辑' }) }));
  s.quota = false; gate.resolve(); const result = await work;
  assert.equal(result.state, '内容冲突'); assert.equal(result.dirty, true);
  assert.throws(() => s.drafts.create(d.kind, body(), s.server), /未保存修改/);
});

const voiceSource = fs.readFileSync(path.join(__dirname, '../utils/voice.js'), 'utf8');
function setupVoice(enabled = true) {
  const s = { network: [], auth: [], starts: 0, stops: 0, texts: [], errors: [], ends: 0 };
  s.manager = { start(options) { s.starts++; s.options = options; }, stop() { s.stops++; } };
  const context = { module: { exports: {} }, require: () => ({ voiceEnabled: enabled }),
    requirePlugin: () => { if (s.pluginError) throw new Error('插件不可用'); return { getRecordRecognitionManager: () => s.manager }; },
    wx: { getNetworkType: options => s.network.push(options), authorize: options => s.auth.push(options) } };
  vm.runInNewContext(voiceSource, context, { filename: 'voice.js' });
  s.voice = context.module.exports;
  s.start = () => s.voice.start(text => s.texts.push(text), e => s.errors.push(e.message), () => s.ends++);
  s.authorize = () => { s.network.at(-1).success({ networkType: 'wifi' }); s.auth.at(-1).success(); };
  return s;
}
test('网络检查期间取消录音，迟到响应不能再申请录音授权', () => {
  const s = setupVoice(); s.start(); s.voice.stop(true);
  s.network[0].success({ networkType: 'wifi' });
  assert.equal(s.auth.length, 0); assert.equal(s.starts, 0); assert.equal(s.ends, 1);
});
test('授权等待期间取消，重新进入页面也不会被旧授权启动录音', () => {
  const s = setupVoice(); s.start(); s.network[0].success({ networkType: 'wifi' });
  s.voice.stop(true); s.start(); s.auth[0].success(); assert.equal(s.starts, 0);
  s.authorize(); assert.equal(s.starts, 1);
});
test('录音准备期间重复点击只能创建一个授权流程', () => {
  const s = setupVoice(); s.start(); s.start();
  assert.equal(s.network.length, 1); assert.match(s.errors[0], /稍后重试/);
  s.authorize(); assert.equal(s.starts, 1);
});
test('插件启动过程中隐藏页面，在启动确认后停止且不回填文字', () => {
  const s = setupVoice(); s.start(); s.authorize(); s.voice.stop(true);
  assert.equal(s.stops, 0); s.manager.onStart(); assert.equal(s.stops, 1);
  s.manager.onStop({ result: '不应回填' }); assert.deepEqual(s.texts, []); assert.equal(s.ends, 1);
});
test('手动结束正常录音，识别结果只回调一次', () => {
  const s = setupVoice(); s.start(); s.authorize(); s.manager.onStart();
  s.voice.stop(); s.voice.stop(); assert.equal(s.stops, 1);
  const callback = s.manager.onStop; callback({ result: 'C30，轴线A' }); callback({ result: '重复' });
  assert.deepEqual(s.texts, ['C30，轴线A']); assert.equal(s.ends, 1); assert.equal(s.options.duration, 60000);
});
test('停止回调前禁止新录音覆盖旧插件处理器', () => {
  const s = setupVoice(); s.start(); s.authorize(); s.manager.onStart();
  s.voice.stop(true); s.start(); assert.equal(s.network.length, 1);
  s.manager.onStop({ result: '旧结果' }); s.start(); s.authorize();
  assert.equal(s.starts, 2); assert.deepEqual(s.texts, []);
});
test('插件关闭、断网、授权拒绝及插件错误都有文字回退', () => {
  const disabled = setupVoice(false); disabled.start(); assert.equal(disabled.ends, 1); assert.equal(disabled.errors.length, 1);
  const offline = setupVoice(); offline.start(); offline.network[0].success({ networkType: 'none' }); assert.equal(offline.ends, 1); assert.equal(offline.auth.length, 0);
  const denied = setupVoice(); denied.start(); denied.network[0].success({ networkType: 'wifi' }); denied.auth[0].fail(); assert.equal(denied.ends, 1); assert.equal(denied.starts, 0);
  const broken = setupVoice(); broken.pluginError = true; broken.start(); broken.authorize(); assert.equal(broken.ends, 1); assert.equal(broken.errors.length, 1);
  const failed = setupVoice(); failed.start(); failed.authorize(); failed.manager.onError(); assert.equal(failed.ends, 1); assert.equal(failed.errors.length, 1);
});

const pageSource = fs.readFileSync(path.join(__dirname, '../pages/record/index.js'), 'utf8');
function setupPage(s) {
  let page;
  const voice = { start(onText) { s.onText = onText; }, stop(cancel) { s.voiceCancelled = cancel; } };
  s.api.fileUrl = async url => 'signed:' + url;
  s.wx.showModal = options => { s.modal = options; };
  s.wx.redirectTo = options => { s.redirect = options.url; };
  vm.runInNewContext(pageSource, { Page: value => { page = value; }, wx: s.wx,
    require: name => ({ '../../utils/api': s.api, '../../utils/drafts': s.drafts, '../../utils/voice': voice, '../../utils/schema': schema })[name] });
  page.data = copy(page.data); page.uid = s.uid; page.kind = 'supervision-diaries'; page.visible = true;
  page.setData = values => Object.assign(page.data, copy(values));
  return page;
}
test('照片链接迟到时不覆盖刚输入的说明', async () => {
  const s = setup(), p = setupPage(s), d = create(s), gate = deferred();
  p.localId = d.localId; p.showDraft(d);
  p.data.body.photos = JSON.stringify([{ url: '/api/uploads/a.jpg', description: '旧' }]);
  p.data.photos = [{ url: '/api/uploads/a.jpg', description: '旧' }];
  s.api.fileUrl = () => gate.promise;
  const work = p.loadFiles(p.data.body);
  p.remotePhotoDescription({ currentTarget: { dataset: { url: '/api/uploads/a.jpg' } }, detail: { value: '新说明' } });
  assert.equal(p.data.photos[0].description, '新说明'); gate.resolve('signed:a'); await work;
  assert.equal(p.data.photos[0].description, '新说明');
});
test('移除照片后迟到的签名链接不能恢复照片', async () => {
  const s = setup(), p = setupPage(s), d = create(s), gate = deferred();
  p.localId = d.localId; p.showDraft(d); p.data.body.photos = '["/api/uploads/a.jpg"]';
  s.api.fileUrl = () => gate.promise; const work = p.loadFiles(p.data.body);
  p.removeRemotePhoto({ currentTarget: { dataset: { url: '/api/uploads/a.jpg' } } });
  gate.resolve('signed:a'); await work;
  assert.equal(p.data.photos.length, 0); assert.equal(JSON.parse(p.data.body.photos).length, 0);
});
test('只读详情不沿用冲突和清理按钮，草稿不沿用修订按钮', () => {
  const s = setup(), p = setupPage(s), d = create(s);
  p.data.conflict = true; p.data.canClear = true;
  p.showRecord(Object.assign(body(), { id: 10, authorId: 1, status: 'approved' }));
  assert.equal(p.data.conflict, false); assert.equal(p.data.canClear, false); assert.equal(p.data.canRevise, true);
  p.localId = d.localId; p.showDraft(d); assert.equal(p.data.canRevise, false); assert.equal(p.data.canClear, false);
});
test('本机已通过稿修订从服务器取原件，不依赖失效页面对象', async () => {
  const s = setup(), p = setupPage(s);
  const record = Object.assign(body(), { id: 10, authorId: 1, status: 'approved', lockVersion: 2 });
  const d = s.drafts.create(p.kind, body(), record); p.localId = d.localId; p.showDraft(d); s.server = record;
  assert.equal(p.data.canRevise, true); assert.equal(p.server, undefined);
  const work = p.revise(); s.modal.success({ confirm: true }); await work;
  assert.ok(s.redirect); assert.equal(s.drafts.list().find(x => x.localId !== d.localId).body.sourceRecordId, 10);
});
test('语音核对弹窗期间换账号或隐藏页面，不能写入旧稿', async () => {
  for (const transition of ['account', 'hide']) {
    const s = setup(), p = setupPage(s), d = create(s); p.localId = d.localId; p.showDraft(d);
    p.recordVoice({ currentTarget: { dataset: { field: 'construction' } } });
    const work = s.onText('新语音');
    if (transition === 'account') s.uid = '2'; else p.onHide();
    s.modal.success({ confirm: true, content: '已核对' }); await work;
    assert.equal(p.data.body.construction, '现场原文');
    if (transition === 'hide') assert.equal(s.voiceCancelled, true);
  }
});
