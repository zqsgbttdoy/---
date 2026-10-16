/* 四类 PC 记录共用业务组件，使用与小程序相同的字段、权限、版本和状态。 */
(function () {
  'use strict';
  const { schemas, today, statuses, pack, unpack } = window.FieldSchema;
  const root = document.getElementById('fieldWorkspace');
  const kind = root.dataset.kind, schema = schemas[kind], esc = App.escape;
  const api = async (...args) => { const result = await App.apiFetch(...args); assertAccount(); return result; };
  if (!App.requireAuth()) return;
  App.initTopbar();
  const uid = String(App.getUser().userId), storageKey = 'field-pc:' + location.origin + ':' + uid + ':' + kind;
  let projects = [], page = 1, total = 0, querySeq = 0, draft = null, busy = false, dirty = false, photos = [], attachments = [], reviewAllowed = false;
  const $ = id => document.getElementById(id);
  const parse = value => { try { return JSON.parse(value || '[]'); } catch (e) { return []; } };
  const key = () => 'pc-' + Date.now().toString(36) + '-' + crypto.getRandomValues(new Uint32Array(4)).join('-');
  const notice = message => { $('fieldError').textContent = message || ''; };
  const run = fn => (...args) => Promise.resolve().then(() => { if (!busy) { assertAccount(); return fn(...args); } }).catch(e => notice(e.message || '操作失败，请重试'));
  let pendingUploads = [];
  function assertAccount() { if (String((App.getUser() || {}).userId) !== uid) throw new Error('账号已变化，请重新登录原账号恢复草稿'); }
  function persistDraft() {
    assertAccount();
    if (!draft.localId) draft.localId = key();
    draft.updatedAt = Date.now();
    sessionStorage.setItem(storageKey + ':draft:' + draft.localId, JSON.stringify(draft));
  }
  function removeDraft() { if (draft && draft.localId) sessionStorage.removeItem(storageKey + ':draft:' + draft.localId); }
  function savedDrafts() {
    assertAccount();
    const items = [];
    for (let i = 0; i < sessionStorage.length; i++) {
      const name = sessionStorage.key(i);
      if (name === storageKey || name.startsWith(storageKey + ':conflict:') || name.startsWith(storageKey + ':draft:')) {
        try { items.push({ name, value: JSON.parse(sessionStorage.getItem(name)) }); } catch (e) { notice('存在无法解析的旧草稿，请保留浏览器数据并联系管理员'); }
      }
    }
    return items.sort((a, b) => (b.value.updatedAt || 0) - (a.value.updatedAt || 0));
  }
  const own = record => String(record.authorId || record.inspectorId || uid) === uid;
  const editable = record => own(record) && (!record.id || ['draft', 'rejected'].includes(record.status));
  function options(list) { return list.map(p => '<option value="' + p.id + '">' + esc(p.name) + '</option>').join(''); }
  root.innerHTML = '<div class="fw-toolbar"><label>项目 <select id="fwProject"><option value="">全部获授权项目</option></select></label>'
    + '<label>状态 <select id="fwStatus"><option value="">全部状态</option>' + Object.keys(statuses).filter(s => s !== 'pending').map(s => '<option value="' + s + '">' + statuses[s] + '</option>').join('') + '</select></label>'
    + '<label>从 <input id="fwFrom" type="date"></label><label>至 <input id="fwTo" type="date"></label><label>编号 <input id="fwCode"></label>'
    + '<label><input id="fwMine" type="checkbox">仅本人</label><button id="fwSearch">查询</button><button id="fwNew">新建' + schema.title + '</button><button id="fwResume">恢复本机草稿</button><button id="fwExport">导出筛选结果 CSV</button><button id="fwMembers">项目成员授权</button></div>'
    + '<section id="fwDraftPanel" hidden><h3>本账号待恢复草稿</h3><div id="fwDraftList"></div></section>'
    + '<p id="fieldError" role="alert" class="fw-error"></p><p id="fwSummary"></p><div class="fw-table"><table><thead><tr><th>编号</th><th>项目</th><th>日期</th><th>填写人 ID</th><th>状态</th><th>业务版本</th><th>操作</th></tr></thead><tbody id="fwRows"></tbody></table></div>'
    + '<div class="fw-toolbar"><button id="fwPrev">上一页</button><span id="fwPage"></span><button id="fwNext">下一页</button></div>'
    + '<section id="fwEditor" hidden><div class="fw-toolbar"><h2 id="fwTitle"></h2><button id="fwClose">关闭正文</button><button id="fwPrint">打印当前记录</button></div><p id="fwState"></p><div id="fwForm"></div>'
    + '<div id="fwMedia"></div><p id="fwUploadState"></p><button id="fwRetryUpload" hidden>重试失败的文件</button><label id="fwUploadLabel">上传照片或附件（逐个确认）<input id="fwUpload" type="file" multiple accept="image/*,.pdf,.doc,.docx,.xls,.xlsx,.txt,.zip"></label>'
    + '<div class="fw-toolbar"><button id="fwSource">从当日已审核日记填入空白栏目</button><button id="fwSave">保存草稿</button><button id="fwSubmit">确认提交审核</button><button id="fwApprove">审核通过</button><button id="fwReject">退回修改</button><button id="fwDelete">删除草稿</button><button id="fwRevise">建立修订稿</button><button id="fwHistory">处理历史</button><button id="fwCompare">查看服务器当前版本</button><button id="fwFork">另存为新草稿</button></div><div id="fwHistoryContent"></div></section>'
    + '<section id="fwMemberPanel" hidden><h2>项目成员授权（仅管理员）</h2><p>项目负责人默认可审核；人员台账的单项目分配仍兼容。此处显式停用成员后不再继承该项目权限。</p><div id="fwMemberList"></div><label>人员 ID <input id="fwMemberId" type="number" min="1"></label><label><input id="fwMemberEnabled" type="checkbox" checked>启用项目访问</label><label><input id="fwMemberReview" type="checkbox">允许审核</label><button id="fwMemberSave">确认保存授权</button></section>';

  function filters() { return { projectId: $('fwProject').value, status: $('fwStatus').value, dateFrom: $('fwFrom').value, dateTo: $('fwTo').value, code: $('fwCode').value, authorId: $('fwMine').checked ? uid : undefined }; }
  async function load() {
    const seq = ++querySeq;
    const data = await App.apiGetPage('/' + kind, Object.assign(filters(), { pageNum: page, pageSize: 20 }));
    if (seq !== querySeq) return;
    total = data.total;
    $('fwRows').innerHTML = data.list.map(r => '<tr><td>' + esc(r.code) + '</td><td>' + esc((projects.find(p => p.id === r.projectId) || {}).name || r.projectId) + '</td><td>' + esc(r[schema.date])
      + '</td><td>' + esc(r.authorId || r.inspectorId) + '</td><td>' + esc(statuses[r.status] || r.status) + '</td><td>' + (r.version || 1) + '</td><td><button data-id="' + r.id + '">查看 / 办理</button></td></tr>').join('') || '<tr><td colspan="7">暂无记录</td></tr>';
    $('fwPage').textContent = '共 ' + total + ' 条，第 ' + page + ' / ' + Math.max(1, Math.ceil(total / 20)) + ' 页';
    $('fwPrev').disabled = page <= 1; $('fwNext').disabled = page * 20 >= total;
    notice('');
    if ($('fwProject').value) {
      const summary = await api('/field/summary?' + App.buildQuery({ projectId: $('fwProject').value, date: today() }));
      if (seq !== querySeq) return;
      const counts = summary[kind];
      $('fwSummary').textContent = '今日可见记录：草稿 ' + counts.draft + ' · 待审核 ' + counts.submitted + ' · 通过 ' + counts.approved + ' · 退回 ' + counts.rejected + '（不把草稿计为已提交）';
    } else $('fwSummary').textContent = '按服务器项目权限筛选；日期未选择时查询全部日期。';
  }
  function collect() {
    if (!draft) return;
    root.querySelectorAll('[data-field]').forEach(el => { draft.body[el.dataset.field] = el.value; });
    draft.body.projectId = Number(draft.body.projectId);
    draft.body.photos = JSON.stringify(photos); draft.body.attachments = JSON.stringify(attachments);
  }
  function localSave() {
    collect();
    if (!draft || !editable(draft.record)) return;
    if (String(App.getUser().userId) !== uid) throw new Error('登录账号已变化，请保留本页内容并重新登录');
    dirty = true;
    persistDraft();
    $('fwState').textContent = '仅本机保存，尚未同步到服务器';
  }
  function form(body, record) {
    const read = !editable(record);
    const fields = [['code', '编号（留空自动生成）'], [schema.date, '记录日期 *']].concat(schema.fields);
    if (kind === 'supervision-logs') fields.push(['weekStart', '周起始日期（可选，不影响单日取数）'], ['weekEnd', '周结束日期（可选）']);
    $('fwForm').innerHTML = '<label>所属项目 * <select data-field="projectId" ' + (record.id || read ? 'disabled' : '') + '>' + options(projects) + '</select></label>'
      + fields.map(f => '<label>' + esc(f[1]) + (f[0] === 'weather' && kind === 'supervision-diaries' ? ' *' : '') + '<textarea data-field="' + f[0] + '" maxlength="60000" ' + (read ? 'readonly' : '') + '>' + esc(body[f[0]]) + '</textarea></label>').join('');
    root.querySelector('[data-field="projectId"]').value = body.projectId;
    root.querySelectorAll('[data-field]').forEach(el => el.addEventListener('input', () => {
      if (el.dataset.field === schema.date || el.dataset.field === 'projectId') draft.body.diarySources = '[]';
      try { localSave(); } catch (e) { notice('本机保存失败，请勿关闭页面：' + e.message); }
    }));
    $('fwTitle').textContent = schema.title + (record.code ? ' · ' + record.code : ' · 新建');
    $('fwState').textContent = record.id ? (statuses[record.status] || record.status) + ' · 并发版本 ' + record.lockVersion + (record.rejectReason ? ' · 退回原因：' + record.rejectReason : '') : '仅本机草稿';
    ['fwSave', 'fwSubmit', 'fwUploadLabel'].forEach(id => $(id).hidden = read);
    $('fwSource').hidden = read || kind !== 'supervision-logs';
    $('fwDelete').hidden = read || !record.id;
    $('fwRevise').hidden = record.status !== 'approved' || !own(record);
    ['fwApprove', 'fwReject'].forEach(id => $(id).hidden = !reviewAllowed || !['submitted', 'pending'].includes(record.status));
    ['fwHistory', 'fwCompare'].forEach(id => $(id).hidden = !record.id);
    $('fwFork').hidden = !own(record);
    $('fwHistoryContent').textContent = '';
    if (busy) $('fwEditor').querySelectorAll('button, input, textarea, select').forEach(e => e.disabled = true);
  }
  async function show(local) {
    draft = local;
    photos = parse(draft.body.photos).map(p => typeof p === 'string' ? { url: p } : p);
    attachments = parse(draft.body.attachments).map(p => typeof p === 'string' ? { url: p } : p);
    reviewAllowed = false;
    $('fwEditor').hidden = false; form(draft.body, draft.record); renderMedia();
    try {
      const summary = await api('/field/summary?' + App.buildQuery({ projectId: draft.body.projectId, date: draft.body[schema.date] }));
      if (draft !== local) return;
      reviewAllowed = summary.canReview;
      ['fwApprove', 'fwReject'].forEach(id => $(id).hidden = !reviewAllowed || !['submitted', 'pending'].includes(draft.record.status));
    } catch (e) { notice(e.message); }
    $('fwEditor').scrollIntoView({ behavior: 'smooth' });
  }
  async function mayLeave() {
    if (pendingUploads.length) throw new Error('仍有未成功上传的文件，请重试完成后再切换');
    if (dirty) { localSave(); return App.confirm('本机草稿已单独保留，可从“恢复本机草稿”找回。确定切换？', { danger: false }); }
    return true;
  }
  async function open(id) {
    if (!await mayLeave()) return;
    const cached = savedDrafts().find(item => String(item.value.record.id) === String(id));
    if (cached) {
      dirty = true; await show(cached.value); persistDraft();
      if (!cached.name.startsWith(storageKey + ':draft:')) sessionStorage.removeItem(cached.name);
      notice('已恢复本机未同步内容，请先对照服务器版本。'); return;
    }
    const record = await api('/' + kind + '/' + id); dirty = false;
    const data = unpack(kind, record), body = {};
    ['code', 'projectId', schema.date, 'photos', 'attachments', 'diarySources', 'weekStart', 'weekEnd'].concat(schema.fields.map(f => f[0])).forEach(k => { if (data[k] != null) body[k] = data[k]; });
    await show({ record, body, requestId: key() });
  }
  async function create() {
    if (!projects.length) throw new Error('暂无获授权项目，请联系管理员');
    if (!await mayLeave()) return;
    const body = { projectId: Number($('fwProject').value) || projects[0].id, [schema.date]: today(), photos: '[]', attachments: '[]' };
    dirty = false; await show({ record: { status: 'draft' }, body, requestId: key() }); localSave();
  }
  async function renderMedia() {
    const current = draft;
    $('fwMedia').innerHTML = '';
    for (const [type, list] of [['照片', photos], ['附件', attachments]]) {
      for (const item of list) {
        const row = document.createElement('div'); row.className = 'fw-file';
        const button = document.createElement('button'); button.textContent = type + '：' + (item.name || '已上传文件');
        button.onclick = run(async () => {
          const target = window.open('about:blank', '_blank');
          if (target) target.opener = null;
          try { const result = await api('/files/access?' + App.buildQuery({ url: item.url })); if (target) target.location = result.url; else notice('请允许浏览器弹出附件窗口后重试'); }
          catch (e) { if (target) target.close(); throw e; }
        });
        row.appendChild(button);
        if (type === '照片') {
          const image = document.createElement('img'); image.alt = item.description || '现场照片'; row.appendChild(image);
          api('/files/access?' + App.buildQuery({ url: item.url })).then(link => { if (draft === current) image.src = link.url; }).catch(e => { image.alt = e.message; });
          if (editable(draft.record)) { const text = document.createElement('input'); text.placeholder = '照片部位说明'; text.value = item.description || ''; text.oninput = () => { item.description = text.value; try { localSave(); } catch (e) { notice(e.message); } }; row.appendChild(text); }
        }
        if (editable(draft.record)) {
          const remove = document.createElement('button'); remove.textContent = '移除'; remove.onclick = run(async () => { list.splice(list.indexOf(item), 1); localSave(); renderMedia(); }); row.appendChild(remove);
        }
        $('fwMedia').appendChild(row);
      }
    }
    if (draft.body.diarySources) { const source = document.createElement('p'); source.textContent = '引用日记：' + parse(draft.body.diarySources).map(s => s.code + ' / ' + s.date + ' / v' + s.lockVersion).join('；'); $('fwMedia').appendChild(source); }
  }
  async function locked(action) {
    if (busy) return;
    busy = true; $('fwEditor').querySelectorAll('button, input, textarea, select').forEach(e => e.disabled = true);
    try { await action(); } finally {
      busy = false; $('fwEditor').querySelectorAll('button, input, textarea, select').forEach(e => e.disabled = false);
      const select = root.querySelector('[data-field="projectId"]'); if (select && draft) select.disabled = !!draft.record.id || !editable(draft.record);
    }
  }
  async function save(submit) {
    if (pendingUploads.length) throw new Error('请先重试未成功上传的文件，不能显示表单已完成');
    if (submit && !await App.confirm('核对项目、日期、正文及照片后提交审核？提交后正文锁定。', { danger: false })) return;
    await locked(async () => {
      localSave();
      if (!draft.pending) draft.pending = { body: pack(kind, Object.assign({}, draft.body, { status: 'draft' })), id: draft.record.id, version: draft.record.lockVersion };
      persistDraft();
      const pending = draft.pending, path = '/' + kind + (pending.id ? '/' + pending.id : '');
      let record;
      try { record = await api(path, { method: pending.id ? 'PUT' : 'POST', body: pending.body, headers: pending.id ? { 'X-Record-Version': pending.version } : { 'Idempotency-Key': draft.requestId } }); }
      catch (error) {
        if ([400, 413, 415, 422].includes(error.code)) { draft.pending = null; persistDraft(); }
        if (error.code !== 409 || !pending.id) throw error;
        const remote = await api(path);
        if (!Object.keys(pending.body).every(k => JSON.stringify(remote[k] == null ? null : remote[k]) === JSON.stringify(pending.body[k] == null ? null : pending.body[k]))) throw error;
        record = remote;
      }
      const current = pack(kind, Object.assign({}, draft.body, { status: 'draft' }));
      draft.record = record; draft.pending = null;
      if (!draft.body.code || draft.body.code === pending.body.code) draft.body.code = record.code;
      persistDraft();
      if (JSON.stringify(current) !== JSON.stringify(pending.body)) {
        notice('此前未确认的请求已恢复；本机新修改仍保留，请再次保存后提交。'); return;
      }
      if (submit) {
        try { record = await api('/' + kind + '/' + record.id + '/submit', { method: 'POST', headers: { 'X-Record-Version': record.lockVersion } }); }
        catch (error) { const remote = await api('/' + kind + '/' + record.id); if (!['submitted', 'approved'].includes(remote.status)) throw error; record = remote; }
        draft.record = record;
      }
      dirty = false; removeDraft(); await show(draft); await load();
      notice(submit ? '服务器已确认提交审核。' : '服务器草稿已保存，PC 与小程序可见同一记录。');
    });
  }
  async function review(status) {
    let reason = '';
    if (status === 'rejected') { reason = await App.prompt({ title: '填写退回原因' }); if (reason == null) return; }
    else if (!await App.confirm('确认审核通过当前正文及附件？', { danger: false })) return;
    await locked(async () => { draft.record = await api('/' + kind + '/' + draft.record.id + '/approve', { method: 'POST', body: { status, rejectReason: reason }, headers: { 'X-Record-Version': draft.record.lockVersion } }); await show(draft); await load(); });
  }
  async function exportRows() {
    const rows = []; let number = 1; const params = filters();
    while (true) { const data = await App.apiGetPage('/' + kind, Object.assign({}, params, { pageNum: number++, pageSize: 100 })); rows.push(...data.list); if (rows.length >= data.total || !data.list.length) break; }
    const fields = [['code', '编号'], ['projectId', '项目 ID'], [schema.date, '日期'], ['status', '状态']].concat(schema.fields);
    const cell = value => '"' + String(value == null ? '' : value).replace(/^[=+@\-\t\r]/, "'$&").replace(/"/g, '""') + '"';
    const csv = [fields.map(f => cell(f[1])).join(',')].concat(rows.map(r => { const data = unpack(kind, r); return fields.map(f => cell(data[f[0]])).join(','); })).join('\r\n');
    const url = URL.createObjectURL(new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8' })); const a = document.createElement('a'); a.href = url; a.download = schema.title + '-' + today() + '.csv'; a.click(); setTimeout(() => URL.revokeObjectURL(url), 5000);
  }
  $('fwSearch').onclick = run(async () => { page = 1; await load(); });
  $('fwPrev').onclick = run(async () => { page--; await load(); }); $('fwNext').onclick = run(async () => { page++; await load(); });
  $('fwRows').onclick = run(async event => { const button = event.target.closest('[data-id]'); if (button) await open(button.dataset.id); });
  $('fwNew').onclick = run(create); $('fwExport').onclick = run(exportRows);
  $('fwResume').onclick = run(async () => {
    const saved = savedDrafts(); if (!saved.length) throw new Error('本账号在此浏览器没有待恢复草稿');
    $('fwDraftPanel').hidden = false; $('fwDraftList').textContent = '';
    saved.forEach(item => {
      const button = document.createElement('button'), value = item.value;
      button.textContent = '项目 ' + value.body.projectId + ' / ' + value.body[schema.date] + ' / ' + (value.body.code || '未编号') + ' / ' + new Date(value.updatedAt || 0).toLocaleString();
      button.onclick = run(async () => {
        if (!await mayLeave()) return;
        dirty = true; await show(value); persistDraft();
        if (!item.name.startsWith(storageKey + ':draft:')) sessionStorage.removeItem(item.name);
        $('fwDraftPanel').hidden = true; notice('本机稿已恢复；冲突内容请先对照服务器版本。');
      });
      $('fwDraftList').appendChild(button);
    });
  });
  $('fwClose').onclick = run(async () => { if (await mayLeave()) $('fwEditor').hidden = true; });
  $('fwSave').onclick = run(() => save(false)); $('fwSubmit').onclick = run(() => save(true));
  $('fwApprove').onclick = run(() => review('approved')); $('fwReject').onclick = run(() => review('rejected'));
  $('fwDelete').onclick = run(async () => { if (!await App.confirm('删除本人草稿？')) return; await locked(async () => { await api('/' + kind + '/' + draft.record.id, { method: 'DELETE', headers: { 'X-Record-Version': draft.record.lockVersion } }); dirty = false; removeDraft(); $('fwEditor').hidden = true; await load(); }); });
  $('fwRevise').onclick = run(async () => { if (!await App.confirm('保留已审核原件并建立修订稿？', { danger: false })) return; const body = Object.assign({}, draft.body, { sourceRecordId: draft.record.id }); delete body.code; await show({ record: { status: 'draft' }, body, requestId: key() }); localSave(); });
  $('fwFork').onclick = run(async () => {
    if (!await App.confirm('原记录保持不变；另存副本可能形成重复填报，请核对后再提交。', { danger: false })) return;
    localSave();
    const body = Object.assign({}, draft.body); delete body.code; delete body.sourceRecordId;
    await show({ record: { status: 'draft' }, body, requestId: key() }); localSave();
  });
  $('fwHistory').onclick = run(async () => { const history = await api('/' + kind + '/' + draft.record.id + '/history'); $('fwHistoryContent').innerHTML = history.map(h => '<details><summary>' + esc(h.actorName + ' · ' + h.action + ' · ' + h.createTime) + '</summary><p>' + esc(h.reason) + '</p><pre>' + esc(JSON.stringify(JSON.parse(h.snapshot), null, 2)) + '</pre></details>').join(''); });
  $('fwCompare').onclick = run(async () => { const remote = await api('/' + kind + '/' + draft.record.id); $('fwHistoryContent').innerHTML = '<h3>服务器当前版本（本机表单未覆盖）</h3><pre>' + esc(JSON.stringify(remote, null, 2)) + '</pre>'; });
  $('fwSource').onclick = run(async () => { collect(); const source = await api('/field/diary-source?' + App.buildQuery({ projectId: draft.body.projectId, date: draft.body.logDate })); let filled = false; schema.fields.forEach(f => { if (!String(draft.body[f[0]] || '').trim() && source[f[0]]) { draft.body[f[0]] = source[f[0]]; filled = true; } }); if (filled) draft.body.diarySources = JSON.stringify(source.sources); form(draft.body, draft.record); localSave(); renderMedia(); notice(filled ? '只填入空白栏目，来源已关联；请人工确认后提交。' : '没有可填入内容，原文未改变。'); });
  async function uploadPending() {
    await locked(async () => {
      const failed = [];
      for (const entry of pendingUploads) {
        try {
          $('fwUploadState').textContent = '正在上传：' + entry.file.name;
          if (!entry.remote) { entry.remote = await App.uploadFile(entry.file); assertAccount(); }
          const list = entry.file.type.startsWith('image/') ? photos : attachments;
          if (!list.some(p => p.url === entry.remote.url)) list.push(Object.assign({}, entry.remote, { source: 'album', description: '' }));
          localSave(); await renderMedia();
        } catch (e) { entry.error = e.message; failed.push(entry); }
      }
      pendingUploads = failed;
      $('fwRetryUpload').hidden = !failed.length;
      $('fwUploadState').textContent = failed.length ? failed.map(x => x.file.name + '：' + x.error).join('；') + '。请勿关闭本页，可逐次重试。' : '全部附件已上传；请继续保存表单。';
    });
  }
  $('fwUpload').onchange = run(async event => { pendingUploads.push(...Array.from(event.target.files).map(file => ({ file }))); event.target.value = ''; await uploadPending(); });
  $('fwRetryUpload').onclick = run(uploadPending);
  $('fwPrint').onclick = () => {
    const fields = Array.from($('fwForm').querySelectorAll('textarea, select'));
    fields.forEach(el => { const text = document.createElement('div'); text.className = 'fw-print-value'; text.textContent = el.tagName === 'SELECT' ? (el.options[el.selectedIndex] || {}).text || '' : el.value; text.style.whiteSpace = 'pre-wrap'; el.after(text); el.hidden = true; });
    document.body.classList.add('fw-print');
    try { window.print(); } finally { document.body.classList.remove('fw-print'); fields.forEach(el => { el.hidden = false; }); root.querySelectorAll('.fw-print-value').forEach(el => el.remove()); }
  };
  async function members() { const pid = $('fwProject').value; if (!pid) throw new Error('请先选择单一项目'); const list = await api('/projects/' + pid + '/members'); $('fwMemberPanel').hidden = false; $('fwMemberList').textContent = list.map(m => m.userId + ' · ' + m.userName + ' · ' + (m.enabled ? '启用' : '停用') + ' · 审核 ' + m.canReview).join('\n') || '暂无显式授权记录'; }
  $('fwMembers').onclick = run(members);
  $('fwMemberSave').onclick = run(async () => { const pid = $('fwProject').value; if (!pid) throw new Error('请选择项目'); if (!await App.confirm('确认变更当前项目的人员访问及审核权限？')) return; await api('/projects/' + pid + '/members', { method: 'PUT', body: { userId: Number($('fwMemberId').value), enabled: $('fwMemberEnabled').checked, canReview: $('fwMemberReview').checked } }); await members(); });
  window.addEventListener('beforeunload', event => { if (App.getToken() && (dirty || pendingUploads.length)) { event.preventDefault(); event.returnValue = ''; } });
  run(async () => { let n = 1; while (true) { const data = await App.apiGetPage('/projects', { pageNum: n++, pageSize: 100 }); projects.push(...data.list); if (projects.length >= data.total || !data.list.length) break; } $('fwProject').insertAdjacentHTML('beforeend', options(projects)); await load(); })();
})();
