/* 四类 PC 记录共用业务组件，使用与小程序相同的字段、权限、版本和状态。 */
(function () {
  'use strict';
  const { schemas, today, statuses, pack, unpack, diaryPeople, logPrefillPatch } = window.FieldSchema;
  const root = document.getElementById('fieldWorkspace');
  const kind = root.dataset.kind, schema = schemas[kind], esc = App.escape;
  const api = async (...args) => { const result = await App.apiFetch(...args); assertAccount(); return result; };
  if (!App.requireAuth()) return;
  App.initTopbar();
  const uid = String(App.getUser().userId), storageKey = 'field-pc:' + location.origin + ':' + uid + ':' + kind;
  const isDiary = kind === 'supervision-diaries', isLog = kind === 'supervision-logs', hasTemplate = isDiary || isLog;
  const uploadView = isDiary
    ? { label: '现场照片与附件', title: '点击或拖拽现场照片、附件到此处上传', sub: '图片计入现场照片；PDF／Word／Excel／TXT／ZIP 计入附件。提交至少需要一张现场照片。' }
    : isLog
      ? { label: '附件上传', title: '<span class="fd-dz-link">点击上传</span> 或拖放文件', sub: '图片计入照片；PDF／Word／Excel／TXT／ZIP 计入附件。文件上传成功后仍需保存表单。' }
      : { label: '附件材料上传', title: '<span class="fd-dz-link">点击上传</span> 或拖拽文件到此处', sub: '图片计入照片，PDF／Word／Excel／TXT／ZIP 计入附件' };
  const submitLabel = hasTemplate ? '提交' : '提交审核';
  let projects = [], page = 1, total = 0, querySeq = 0, draft = null, busy = false, dirty = false, photos = [], attachments = [], reviewAllowed = false, summaryCache = null;
  const $ = id => document.getElementById(id);
  const parse = value => { try { return JSON.parse(value || '[]'); } catch (e) { return []; } };
  // 服务端时间以 ISO 返回（含 T），界面与导出统一为标签声明的 YYYY-MM-DD HH:mm:ss
  const prettyTime = value => value == null ? '' : String(value).replace('T', ' ').replace(/\.\d+$/, '');
  const key = () => 'pc-' + Date.now().toString(36) + '-' + crypto.getRandomValues(new Uint32Array(4)).join('-');
  const projectRequiredMessage = '请先在上方选择本次填报项目，系统将自动带入工程名称和当天日期';
  const notice = message => {
    const text = message || '';
    ['fieldError', 'fwError'].forEach(id => { const el = $(id); if (el) el.textContent = text; });
  };
  function clearProjectNotice() {
    ['fieldError', 'fwError'].forEach(id => {
      const el = $(id);
      if (el && el.textContent === projectRequiredMessage) el.textContent = '';
    });
  }
  const run = fn => (...args) => Promise.resolve().then(() => { if (!busy) { assertAccount(); return fn(...args); } }).catch(e => notice(e.message || '操作失败，请重试'));
  let pendingUploads = [], prefillSequence = 0, fieldEdits = {};
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
  const editable = record => !(draft && draft.readOnly) && own(record) && (!record.id || ['draft', 'rejected'].includes(record.status));
  function options(list) { return list.map(p => '<option value="' + p.id + '">' + esc(p.name) + '</option>').join(''); }

  /* ---------------- 编辑器骨架：标题 + 独立滚动正文 + 固定底部操作栏 ---------------- */
  root.innerHTML = '<div class="fw-toolbar"><label>项目 <select id="fwProject"><option value="">全部获授权项目</option></select></label>'
    + '<label>状态 <select id="fwStatus"><option value="">全部状态</option>' + Object.keys(statuses).filter(s => s !== 'pending').map(s => '<option value="' + s + '">' + statuses[s] + '</option>').join('') + '</select></label>'
    + '<label>从 <input id="fwFrom" type="date"></label><label>至 <input id="fwTo" type="date"></label><label>编号 <input id="fwCode"></label>'
    + '<label><input id="fwMine" type="checkbox">仅本人</label><button type="button" id="fwSearch">查询</button><button type="button" id="fwNew">新建' + schema.title + '</button><button type="button" id="fwResume">恢复本机草稿</button><button type="button" id="fwExport">导出筛选结果 CSV</button><button type="button" id="fwMembers">项目成员授权</button></div>'
    + '<section id="fwDraftPanel" hidden><h3>本账号待恢复草稿</h3><div id="fwDraftList"></div></section>'
    + '<p id="fieldError" role="alert" class="fw-error"></p><p id="fwSummary"></p><div class="fw-table"><table><thead><tr><th>编号</th><th>项目</th><th>日期</th><th>填写人 ID</th><th>状态</th><th>业务版本</th><th>操作</th></tr></thead><tbody id="fwRows"></tbody></table></div>'
    + '<div class="fw-toolbar"><button type="button" id="fwPrev">上一页</button><span id="fwPage"></span><button type="button" id="fwNext">下一页</button></div>'
    + '<div id="fwEditor" class="fw-editor" hidden>'
      + '<div class="fd-modal" role="dialog" aria-modal="true" aria-labelledby="fwTitle">'
        + '<div class="fd-header"><div><h2 class="fd-title" id="fwTitle"></h2><div class="fd-sub" id="fwState"></div></div>'
        + '<button type="button" class="fd-close" id="fwClose" title="关闭" aria-label="关闭">✕</button></div>'
        + '<div class="fd-body">'
          + '<p id="fwError" class="fd-error" role="alert"></p>'
          + '<p id="fwPrefillState" class="fd-hint" role="status"></p>'
          + '<div id="fwForm" class="fd-grid"><div id="fwGuide" class="fd-wide"></div></div>'
          + '<div id="fwMedia"></div>'
          + '<div class="fd-field" id="fwUploadField">'
            + '<div class="fd-label">' + uploadView.label + '</div>'
            + '<label class="fd-dropzone" id="fwUploadLabel" for="fwUpload">'
              + '<span class="fd-dz-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M7 18a4 4 0 1 1 .6-7.96A6 6 0 0 1 19 12a3 3 0 0 1-1 5.83M12 16V8m0 0-3 3m3-3 3 3"/></svg></span>'
              + '<span class="fd-dz-title">' + uploadView.title + '</span>'
              + '<span class="fd-dz-sub">' + uploadView.sub + '</span>'
              + '<input id="fwUpload" type="file" multiple accept="image/*,.pdf,.doc,.docx,.xls,.xlsx,.txt,.zip">'
            + '</label>'
            + (schema.attachmentHint ? '<div class="fd-hint">' + esc(schema.attachmentHint) + '</div>' : '')
            + (hasTemplate ? '' : '<div class="fd-hint">文件逐个上传并确认；未成功上传的文件不能提交，可点击“重试失败的文件”。</div>')
            + '<div class="fd-files"><button type="button" id="fwRetryUpload" hidden>重试失败的文件</button><p id="fwUploadState" class="fd-hint"></p></div>'
          + '</div>'
          + '<div id="fwHistoryContent" class="fw-history"></div>'
        + '</div>'
        + '<div class="fd-footer">'
          + '<div class="fd-footer-left">'
            + '<button type="button" id="fwPrint" class="fd-btn fd-btn-ghost">打印</button>'
            + '<button type="button" id="fwPrefill" class="fd-btn fd-btn-ghost">重新预填共享字段</button>'
            + '<button type="button" id="fwSource" class="fd-btn fd-btn-ghost" title="手动整编原文并关联来源，与自动字段预填独立">整编日记原文（含来源）</button>'
            + '<button type="button" id="fwHistory" class="fd-btn fd-btn-ghost">处理历史</button>'
            + '<button type="button" id="fwCompare" class="fd-btn fd-btn-ghost">查看服务器版本</button>'
            + '<button type="button" id="fwRevise" class="fd-btn fd-btn-ghost">建立修订稿</button>'
            + '<button type="button" id="fwFork" class="fd-btn fd-btn-ghost">另存为新草稿</button>'
            + '<button type="button" id="fwDelete" class="fd-btn fd-btn-ghost-danger">删除草稿</button>'
          + '</div>'
          + '<div class="fd-footer-right">'
            + '<button type="button" id="fwApprove" class="fd-btn fd-btn-primary">审核通过</button>'
            + '<button type="button" id="fwReject" class="fd-btn fd-btn-ghost-danger">退回修改</button>'
            + '<button type="button" id="fwSave" class="fd-btn fd-btn-draft">保存草稿</button>'
            + '<button type="button" id="fwCancel" class="fd-btn fd-btn-cancel">取消</button>'
            + '<button type="button" id="fwSubmit" class="fd-btn fd-btn-primary">' + submitLabel + '</button>'
          + '</div>'
        + '</div>'
      + '</div>'
    + '</div>'
    + '<section id="fwMemberPanel" hidden><h2>项目成员授权（仅管理员）</h2><p>项目负责人默认可审核；人员台账的单项目分配仍兼容。此处显式停用成员后不再继承该项目权限。</p><div id="fwMemberList"></div><label>人员 ID <input id="fwMemberId" type="number" min="1"></label><label><input id="fwMemberEnabled" type="checkbox" checked>启用项目访问</label><label><input id="fwMemberReview" type="checkbox">允许审核</label><button type="button" id="fwMemberSave">确认保存授权</button></section>';

  /* ---------------- 字段编排：只调整呈现顺序与控件类型，字段名与提交内容不变 ---------------- */
  const AUTHOR_LABEL = { 'supervision-diaries': '记录人', 'supervision-logs': '填写人', 'daily-inspections': '检查人员', 'sideline-inspections': '旁站人员' }[kind] || '填写人';
  const labels = { [schema.date]: '日期', projectId: '项目名称', code: '编号', weather: '天气', wind: '风力', temperature: '气温', weekStart: '周起始日期（可选）', weekEnd: '周结束日期（可选）' };
  schema.fields.forEach(f => { labels[f[0]] = f[1]; });
  if (hasTemplate) Object.assign(labels, { projectId: '工程名称', reviewer: '审核人' });
  const requiredKeys = new Set([schema.date, 'projectId']);
  if (hasTemplate) requiredKeys.add('author');
  if (isDiary) requiredKeys.add('weather');
  // 与服务端提交校验一致的必填项：巡检要求检查部位/巡视内容，旁站还要求工序、时间、人数、结果、关联资料
  ({
    'daily-inspections': ['location', 'content'],
    'sideline-inspections': ['location', 'process', 'startTime', 'endTime', 'manageCount', 'workerCount', 'result', 'docsText']
  }[kind] || []).forEach(field => requiredKeys.add(field));
  const HINTS = Object.assign({ code: '留空由系统生成，可按需修改。' }, schema.hints || {});
  const GUIDE_TEXT = { 'supervision-logs': '工作内容按当日实际情况填写；管理情况、巡检情况与晨会、图纸审查分栏记录。', 'daily-inspections': '按巡检部位逐项记录，发现问题与整改情况需前后对应。', 'sideline-inspections': '旁站部位、时间与见证取样内容需与现场实际一致。' };

  function topKeys() {
    const rows = [];
    if (isDiary) rows.push('projectId', schema.date, 'author', 'reviewer', 'weather', 'wind', 'temperature', 'code');
    else if (isLog) rows.push('projectId', schema.date, 'author', 'reviewer', 'weather', 'wind', 'temperature', 'code');
    else rows.push(schema.date, 'projectId', 'author', 'code');
    const names = schema.fields.map(f => f[0]);
    return rows.filter(k => ['author', 'reviewer', 'projectId', 'code'].includes(k) || k === schema.date || names.includes(k));
  }
  function longKeys() { const top = topKeys(); return schema.fields.map(f => f[0]).filter(k => !top.includes(k)); }
  function compatibilityKeys() { return isLog ? (schema.legacyFields || []).concat('weekStart', 'weekEnd') : []; }
  function controlKind(field) {
    if (field === 'projectId') return 'project';
    if (field === schema.date || field === 'weekStart' || field === 'weekEnd') return 'date';
    if (['code', 'weather', 'wind', 'temperature'].includes(field) || (schema.shortFields || []).includes(field)) return 'text';
    return 'textarea';
  }
  function fieldCell(field, body, read, projectLocked, opts) {
    const option = opts || {}, label = labels[field] || field, hint = HINTS[field];
    const inlineHint = hasTemplate && !['author', 'reviewer', 'projectId', schema.date].includes(field);
    const placeholder = esc((inlineHint ? hint : (schema.placeholders || {})[field]) || '');
    const help = hint && !inlineHint ? '<div class="fd-hint" id="fwHint-' + field + '">' + esc(hint) + '</div>' : '';
    const described = !hint ? '' : inlineHint
      ? ' title="' + esc(hint) + '" aria-description="' + esc(hint) + '"'
      : ' aria-describedby="fwHint-' + field + '"';
    if (hasTemplate && ['author', 'reviewer', 'projectId'].includes(field)) {
      const people = diaryPeople(draft.record, App.getUser());
      const value = field === 'projectId' ? (projects.find(p => String(p.id) === String(body.projectId)) || {}).name || '项目#' + body.projectId : people[field];
      return '<div class="fd-field"><div class="fd-label">' + esc(field === 'author' ? AUTHOR_LABEL : label) + '</div><div class="fd-readonly">' + esc(value) + '</div>' + help + '</div>';
    }
    if (field === 'author') {
      return '<div class="fd-field"><div class="fd-label">' + esc(AUTHOR_LABEL) + (requiredKeys.has('author') ? '<span class="fd-req">*</span>' : '') + '</div><div class="fd-readonly">' + esc((App.getUser() || {}).userName || '当前登录账号') + '</div>'
        + (hasTemplate ? '' : '<div class="fd-hint">按当前登录账号记录，不支持手工修改。</div>') + '</div>';
    }
    const kindOfControl = controlKind(field), value = body[field] == null ? '' : String(body[field]);
    let control;
    if (kindOfControl === 'project') {
      control = '<select class="fd-select" id="fwF-projectId" data-field="projectId" ' + (projectLocked ? 'disabled' : '') + '>' + options(projects) + '</select>';
    } else if (kindOfControl === 'date' && (!value || /^\d{4}-\d{2}-\d{2}$/.test(value))) {
      /* 空值或标准 ISO 日期用日期控件；非标准历史值退回文本框，避免控件吞掉原值 */
      control = '<input type="date" class="fd-input" id="fwF-' + field + '" data-field="' + field + '" value="' + esc(value) + '"' + described + (read || hasTemplate && field === schema.date ? ' readonly' : '') + '>';
    } else if (kindOfControl === 'text' || kindOfControl === 'date') {
      const maxLength = hasTemplate ? (field === 'code' ? 50 : 30) : 60;
      control = '<input type="text" class="fd-input" id="fwF-' + field + '" data-field="' + field + '" maxlength="' + maxLength + '" value="' + esc(value) + '" placeholder="' + placeholder + '"' + described + (read || hasTemplate && field === schema.date ? ' readonly' : '') + '>';
    } else {
      control = '<textarea class="fd-textarea" id="fwF-' + field + '" data-field="' + field + '" maxlength="60000" placeholder="' + placeholder + '"' + described + (read ? ' readonly' : '') + '>' + esc(value) + '</textarea>';
    }
    return '<div class="fd-field' + (option.wide ? ' fd-wide' : '') + '">'
      + (option.headless ? '' : '<label class="fd-label" for="fwF-' + field + '">' + esc(label)
        + (requiredKeys.has(field) ? '<span class="fd-req">*</span>' : '') + '</label>')
      + control + help + '</div>';
  }
  function renderGuide() {
    const box = $('fwGuide');
    if (!box) return;
    box.hidden = false;
    const fileLine = (photos.length + attachments.length)
      ? '<div class="fd-info-item on">照片 ' + photos.length + ' 张 · 附件 ' + attachments.length + ' 个</div>'
      : '<div class="fd-info-item">暂无上传文件</div>';
    if (!hasTemplate) {
      box.innerHTML = '<div class="fd-info"><div class="fd-info-col"><div class="fd-info-title">填写提示</div><div class="fd-info-item on">' + esc(GUIDE_TEXT[kind] || '按现场实际情况逐栏填写，提交前请核对项目与日期。') + '</div></div>'
        + '<div class="fd-info-col"><div class="fd-info-title">本次上传文件</div>' + fileLine + '</div></div>';
      return;
    }
    box.innerHTML = '<div class="fd-guide-title">' + (isDiary ? '监理日记填写指引 · 14 项规范' : '监理日志填写指引 · 9 项规范') + '</div><div class="fd-info"><div class="fd-info-col"><div class="fd-info-title">记录要求</div><div class="fd-info-item on">' + esc(schema.guide) + '</div>'
      + (schema.prefillHint ? '<div class="fd-info-item on">' + esc(schema.prefillHint) + '</div>' : '')
      + '</div><div class="fd-info-col"><div class="fd-info-title">本次上传文件</div>' + fileLine + '</div></div>';
  }

  function filters() { return { projectId: $('fwProject').value, status: $('fwStatus').value, dateFrom: $('fwFrom').value, dateTo: $('fwTo').value, code: $('fwCode').value, authorId: $('fwMine').checked ? uid : undefined }; }
  async function load() {
    const seq = ++querySeq;
    // 先抹掉上一屏内容再请求：避免查询/翻页时旧记录短暂残留
    $('fwRows').innerHTML = '<tr data-app-placeholder="1"><td colspan="7" style="text-align:center;color:#9CA3AF;padding:24px;">数据加载中...</td></tr>';
    const data = await App.apiGetPage('/' + kind, Object.assign(filters(), { pageNum: page, pageSize: 20 }));
    if (seq !== querySeq) return;
    total = data.total;
    $('fwRows').innerHTML = data.list.map(r => '<tr><td>' + esc(r.code) + '</td><td>' + esc((projects.find(p => p.id === r.projectId) || {}).name || r.projectId) + '</td><td>' + esc(r[schema.date])
      + '</td><td>' + esc(hasTemplate ? diaryPeople(r).author : r.authorId || r.inspectorId) + '</td><td>' + esc(statuses[r.status] || r.status) + '</td><td>' + (r.version || 1) + '</td><td><button type="button" data-id="' + r.id + '">查看 / 办理</button></td></tr>').join('') || '<tr data-app-placeholder="1"><td colspan="7" style="text-align:center;color:#9CA3AF;padding:24px;">暂无记录</td></tr>';
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
  function renderState(record) {
    if (!record) { $('fwState').textContent = ''; return; }
    if (!record.id) { $('fwState').innerHTML = '<span class="fw-chip draft">本机草稿</span>尚未同步到服务器'; return; }
    const status = record.status || 'draft';
    const chip = '<span class="fw-chip ' + esc(status) + '">' + esc(statuses[status] || status) + '</span>';
    const meta = '并发版本 ' + (record.lockVersion == null ? '—' : record.lockVersion);
    $('fwState').innerHTML = chip + esc(meta)
      + (record.rejectReason ? '<div class="fw-sub-reject">退回原因：' + esc(record.rejectReason) + '</div>' : '');
  }
  function form(body, record) {
    const read = !editable(record), projectLocked = !!record.id || read;
    const topHtml = topKeys().map(field => fieldCell(field, body, read, projectLocked)).join('');
    const guideHtml = '<div id="fwGuide" class="fd-wide"></div>';
    let bodyHtml;
    if (isDiary) {
      bodyHtml = '<div class="fd-section-heading fd-wide">基础信息</div>' + topHtml + guideHtml
        + '<div class="fd-section-heading fd-wide">当日工作记录</div>'
        + longKeys().map(field => fieldCell(field, body, read, projectLocked, { wide: true })).join('');
    } else if (isLog) {
      const compatibility = compatibilityKeys(), hasLegacy = compatibility.some(field => String(body[field] || '').trim());
      bodyHtml = '<div class="fd-section-heading fd-wide">基础信息</div>' + topHtml + guideHtml
        + '<div class="fd-section-heading fd-wide">当日安全监理记录</div>'
        + longKeys().filter(field => !compatibility.includes(field)).map(field => fieldCell(field, body, read, projectLocked, { wide: true })).join('')
        + '<details class="fd-compat"' + (hasLegacy ? ' open' : '') + '><summary>原有栏目与日记整编内容（保留原文）</summary><div class="fd-compat-grid">'
        + compatibility.map(field => fieldCell(field, body, read, projectLocked, { wide: controlKind(field) === 'textarea' })).join('') + '</div></details>';
    } else {
      const longs = longKeys(), grouped = longs.length >= 3;
      // 栏目标题统一承载必填红星，并去掉字段名里自带的星号，避免出现“* *”或样式不一致
      const sectionTitle = field => (labels[field] || field).replace(/\s*\*+$/, '');
      bodyHtml = guideHtml + topHtml + longs.map(field => grouped
        ? '<div class="fd-sec"><div class="fd-sec-title">' + esc(sectionTitle(field)) + (requiredKeys.has(field) ? '<span class="fd-req">*</span>' : '') + '</div>' + fieldCell(field, body, read, projectLocked, { wide: true, headless: true }) + '</div>'
        : fieldCell(field, body, read, projectLocked, { wide: true })).join('');
    }
    $('fwForm').innerHTML = bodyHtml;
    const select = root.querySelector('[data-field="projectId"]');
    if (select) select.value = body.projectId;
    root.querySelectorAll('[data-field]').forEach(el => el.addEventListener('input', () => {
      fieldEdits[el.dataset.field] = (fieldEdits[el.dataset.field] || 0) + 1;
      if (el.dataset.field === schema.date || el.dataset.field === 'projectId') draft.body.diarySources = '[]';
      try { localSave(); } catch (e) { notice('本机保存失败，请勿关闭页面：' + e.message); }
    }));
    // 项目或日期变化后刷新“今日填写指引”，取数失败不影响填写
    root.querySelectorAll('[data-field="' + schema.date + '"], [data-field="projectId"]').forEach(el => el.addEventListener('change', () => {
      try { localSave(); } catch (e) { notice('本机保存失败，请勿关闭页面：' + e.message); }
      if (!draft.body.projectId || !draft.body[schema.date]) return;
      run(async () => { summaryCache = await api('/field/summary?' + App.buildQuery({ projectId: draft.body.projectId, date: draft.body[schema.date] })); renderGuide(); })();
    }));
    $('fwTitle').textContent = record.code ? schema.title + ' · ' + record.code : '新建' + schema.title;
    renderState(record);
    ['fwSave', 'fwSubmit', 'fwUploadField'].forEach(id => $(id).hidden = read);
    $('fwSource').hidden = read || kind !== 'supervision-logs';
    $('fwPrefill').hidden = read || !isLog;
    $('fwDelete').hidden = read || !record.id;
    $('fwRevise').hidden = !!draft.readOnly || record.status !== 'approved' || !own(record);
    ['fwApprove', 'fwReject'].forEach(id => $(id).hidden = !!draft.readOnly || !reviewAllowed || !['submitted', 'pending'].includes(record.status));
    ['fwHistory', 'fwCompare'].forEach(id => $(id).hidden = !record.id);
    $('fwFork').hidden = !!draft.readOnly || !own(record);
    $('fwHistoryContent').textContent = '';
    if (busy) $('fwEditor').querySelectorAll('button, input, textarea, select').forEach(e => e.disabled = true);
  }
  function openEditor() { $('fwEditor').hidden = false; document.body.classList.add('fw-modal-open'); }
  function cancelPrefill() { prefillSequence++; $('fwPrefillState').textContent = ''; }
  function closeEditor() { cancelPrefill(); $('fwEditor').hidden = true; document.body.classList.remove('fw-modal-open'); }
  async function prefillLog(automatic = false) {
    if (!isLog || !draft || busy || draft.pending || !editable(draft.record) || $('fwEditor').hidden) return;
    collect();
    const local = draft, projectId = draft.body.projectId, date = draft.body.logDate;
    const sequence = ++prefillSequence, edits = Object.assign({}, fieldEdits);
    const valid = () => sequence === prefillSequence && draft === local && !busy && !local.pending
      && !$('fwEditor').hidden && editable(local.record) && local.body.projectId === projectId && local.body.logDate === date;
    $('fwPrefillState').textContent = '正在读取同项目、日志当日的已审核日记，可继续填写…';
    try {
      const source = await api('/field/diary-prefill?' + App.buildQuery({ projectId, date }));
      if (!valid()) return;
      collect();
      const excluded = schema.prefillFields.filter(field => (fieldEdits[field] || 0) !== (edits[field] || 0) || automatic && fieldEdits[field]);
      const patch = logPrefillPatch(local.body, source, excluded), fields = Object.keys(patch);
      if (fields.length) {
        Object.assign(local.body, patch);
        fields.forEach(field => { const el = $('fwF-' + field); if (el) el.value = patch[field]; });
        const compatibility = $('fwForm').querySelector('.fd-compat');
        if (compatibility && fields.some(field => schema.legacyFields.includes(field))) compatibility.open = true;
        localSave();
      }
      $('fwPrefillState').textContent = fields.length ? '已预填 ' + fields.length + ' 个共享字段，仅供参考；可修改后保存，已有内容未覆盖。'
        : '暂无可预填的空白字段或当日已审核日记，已有内容保持不变。';
    } catch (e) {
      if (valid() && String((App.getUser() || {}).userId) === uid)
        $('fwPrefillState').textContent = '日记预填未完成：' + e.message + '。可继续手动填写，或点击“重新预填共享字段”。';
    }
  }
  async function show(local, autoPrefill = false) {
    cancelPrefill(); fieldEdits = {};
    draft = local;
    photos = parse(draft.body.photos).map(p => typeof p === 'string' ? { url: p } : p);
    attachments = parse(draft.body.attachments).map(p => typeof p === 'string' ? { url: p } : p);
    reviewAllowed = false;
    openEditor(); form(draft.body, draft.record); renderMedia(); renderGuide();
    const body = $('fwEditor').querySelector('.fd-body'); if (body) body.scrollTop = 0;
    // 预填独立启动，统计接口延迟不能拖延取数或在保存后重新补回空栏。
    if (autoPrefill) void prefillLog(true);
    try {
      const summary = await api('/field/summary?' + App.buildQuery({ projectId: draft.body.projectId, date: draft.body[schema.date] }));
      if (draft !== local) return;
      summaryCache = summary;
      reviewAllowed = summary.canReview;
      ['fwApprove', 'fwReject'].forEach(id => $(id).hidden = !!draft.readOnly || !reviewAllowed || !['submitted', 'pending'].includes(draft.record.status));
      renderGuide();
    } catch (e) { if (draft === local) notice(e.message); }
  }
  async function mayLeave() {
    if (pendingUploads.length) throw new Error('仍有未成功上传的文件，请重试完成后再切换');
    if (dirty) { localSave(); return App.confirm('本机草稿已单独保留，可从“恢复本机草稿”找回。确定切换？', { danger: false }); }
    return true;
  }
  async function open(id, readOnly = false) {
    if (!await mayLeave()) return;
    const cached = !readOnly && savedDrafts().find(item => String(item.value.record.id) === String(id));
    if (cached) {
      dirty = true; await show(cached.value, true); persistDraft();
      if (!cached.name.startsWith(storageKey + ':draft:')) sessionStorage.removeItem(cached.name);
      notice('已恢复本机未同步内容，请先对照服务器版本。'); return;
    }
    const record = await api('/' + kind + '/' + id); dirty = false;
    const data = unpack(kind, record), body = {};
    ['code', 'projectId', schema.date, 'photos', 'attachments', 'diarySources', 'weekStart', 'weekEnd'].concat(schema.fields.map(f => f[0])).forEach(k => { if (data[k] != null) body[k] = data[k]; });
    ['startTime', 'endTime'].forEach(k => { if (body[k]) body[k] = prettyTime(body[k]); });
    await show({ record, body, readOnly, requestId: key() }, true);
  }
  async function create() {
    if (!projects.length) throw new Error('暂无获授权项目，请联系管理员');
    if (hasTemplate && !$('fwProject').value && projects.length > 1) {
      $('fwProject').focus();
      throw new Error(projectRequiredMessage);
    }
    if (!await mayLeave()) return;
    if (hasTemplate) clearProjectNotice();
    const body = { projectId: Number($('fwProject').value) || projects[0].id, [schema.date]: today(), photos: '[]', attachments: '[]' };
    dirty = false; await show({ record: { status: 'draft' }, body, requestId: key() }, true); localSave();
  }
  function openFile(url) {
    run(async () => {
      const target = window.open('about:blank', '_blank');
      if (target) target.opener = null;
      try { const result = await api('/files/access?' + App.buildQuery({ url })); if (target) target.location = result.url; else notice('请允许浏览器弹出附件窗口后重试'); }
      catch (e) { if (target) target.close(); throw e; }
    })();
  }
  function renderMedia() {
    const current = draft, editableNow = editable(draft.record);
    $('fwMedia').innerHTML = '';
    if (photos.length) {
      const field = document.createElement('div'); field.className = 'fd-field';
      field.innerHTML = '<div class="fd-label">现场照片（' + photos.length + '）</div>';
      const grid = document.createElement('div'); grid.className = 'fd-photo-grid';
      photos.forEach(item => {
        const card = document.createElement('div'); card.className = 'fd-photo-item';
        const thumb = document.createElement('div'); thumb.className = 'fd-photo-thumb';
        const image = document.createElement('img'); image.alt = item.description || '现场照片'; thumb.appendChild(image);
        api('/files/access?' + App.buildQuery({ url: item.url }))
          .then(link => { if (draft === current) image.src = link.url; })
          .catch(e => { if (draft === current) { thumb.textContent = e.message || '照片不可预览'; } });
        card.appendChild(thumb);
        if (editableNow) {
          const text = document.createElement('input'); text.placeholder = '照片部位说明'; text.value = item.description || '';
          text.oninput = () => { item.description = text.value; try { localSave(); } catch (e) { notice(e.message); } };
          card.appendChild(text);
        }
        const act = document.createElement('div'); act.className = 'fd-photo-act';
        const view = document.createElement('button'); view.type = 'button'; view.textContent = '查看'; view.onclick = () => openFile(item.url);
        act.appendChild(view);
        if (editableNow) {
          const remove = document.createElement('button'); remove.type = 'button'; remove.className = 'fd-danger'; remove.textContent = '移除';
          remove.onclick = run(async () => { photos.splice(photos.indexOf(item), 1); localSave(); renderMedia(); renderGuide(); });
          act.appendChild(remove);
        }
        card.appendChild(act);
        grid.appendChild(card);
      });
      field.appendChild(grid);
      $('fwMedia').appendChild(field);
    }
    if (attachments.length) {
      const field = document.createElement('div'); field.className = 'fd-field';
      field.innerHTML = '<div class="fd-label">附件材料（' + attachments.length + '）</div>';
      const list = document.createElement('div'); list.className = 'fd-files';
      attachments.forEach(item => {
        const row = document.createElement('div'); row.className = 'fd-file-row';
        const name = document.createElement('span'); name.className = 'fd-file-name'; name.textContent = item.name || '已上传文件';
        row.appendChild(name);
        const view = document.createElement('button'); view.type = 'button'; view.textContent = '查看'; view.onclick = () => openFile(item.url);
        row.appendChild(view);
        if (editableNow) {
          const remove = document.createElement('button'); remove.type = 'button'; remove.className = 'fd-danger'; remove.textContent = '移除';
          remove.onclick = run(async () => { attachments.splice(attachments.indexOf(item), 1); localSave(); renderMedia(); renderGuide(); });
          row.appendChild(remove);
        }
        list.appendChild(row);
      });
      field.appendChild(list);
      $('fwMedia').appendChild(field);
    }
    if (draft.body.diarySources) {
      const source = document.createElement('div'); source.className = 'fd-hint';
      source.textContent = '引用日记：' + parse(draft.body.diarySources).map(s => s.code + ' / ' + s.date + ' / v' + s.lockVersion).join('；');
      $('fwMedia').appendChild(source);
    }
  }
  async function locked(action) {
    if (busy) return;
    cancelPrefill();
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
    else if (!await App.confirm('确认审核通过当前正文及附件？', { danger: false, okText: '审核通过' })) return;
    await locked(async () => { draft.record = await api('/' + kind + '/' + draft.record.id + '/approve', { method: 'POST', body: { status, rejectReason: reason }, headers: { 'X-Record-Version': draft.record.lockVersion } }); await show(draft); await load(); });
  }
  async function exportRows() {
    const rows = []; let number = 1; const params = filters();
    while (true) { const data = await App.apiGetPage('/' + kind, Object.assign({}, params, { pageNum: number++, pageSize: 100 })); rows.push(...data.list); if (rows.length >= data.total || !data.list.length) break; }
    const fields = [['code', '编号'], ['projectId', '项目 ID'], [schema.date, '日期'], ['status', '状态']]
      .concat(hasTemplate ? [['projectName', '工程名称'], ['authorName', AUTHOR_LABEL], ['reviewerName', '审核人及审核记录']] : [], schema.fields,
        isLog ? [['weekStart', '周起始日期'], ['weekEnd', '周结束日期'], ['photos', '照片清单'], ['attachments', '附件清单'], ['diarySources', '引用日记']] : []);
    const cell = value => '"' + String(value == null ? '' : value).replace(/^[=+@\-\t\r]/, "'$&").replace(/"/g, '""') + '"';
    const csv = [fields.map(f => cell(f[1].replace(/\s*\*+$/, ''))).join(',')].concat(rows.map(r => {
      const data = unpack(kind, r);
      ['startTime', 'endTime'].forEach(k => { if (data[k]) data[k] = prettyTime(data[k]); });
      if (hasTemplate) {
        const people = diaryPeople(r);
        Object.assign(data, { projectName: (projects.find(p => String(p.id) === String(r.projectId)) || {}).name || '项目#' + r.projectId, authorName: people.author, reviewerName: people.reviewer, status: statuses[r.status] || r.status });
      }
      return fields.map(f => cell(data[f[0]])).join(',');
    })).join('\r\n');
    const url = URL.createObjectURL(new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8' })); const a = document.createElement('a'); a.href = url; a.download = schema.title + '-' + today() + '.csv'; a.click(); setTimeout(() => URL.revokeObjectURL(url), 5000);
  }
  $('fwSearch').onclick = run(async () => { page = 1; await load(); });
  $('fwPrev').onclick = run(async () => { page--; await load(); }); $('fwNext').onclick = run(async () => { page++; await load(); });
  $('fwRows').onclick = run(async event => { const button = event.target.closest('[data-id]'); if (button) await open(button.dataset.id); });
  $('fwNew').onclick = run(create); $('fwExport').onclick = run(exportRows);
  if (hasTemplate) $('fwProject').onchange = () => {
    if (projects.some(p => String(p.id) === $('fwProject').value)) clearProjectNotice();
  };
  $('fwResume').onclick = run(async () => {
    const saved = savedDrafts(); if (!saved.length) throw new Error('本账号在此浏览器没有待恢复草稿');
    $('fwDraftPanel').hidden = false; $('fwDraftList').textContent = '';
    saved.forEach(item => {
      const button = document.createElement('button'), value = item.value;
      button.type = 'button';
      button.textContent = '项目 ' + value.body.projectId + ' / ' + value.body[schema.date] + ' / ' + (value.body.code || '未编号') + ' / ' + new Date(value.updatedAt || 0).toLocaleString();
      button.onclick = run(async () => {
        if (!await mayLeave()) return;
        dirty = true; await show(value, true); persistDraft();
        if (!item.name.startsWith(storageKey + ':draft:')) sessionStorage.removeItem(item.name);
        $('fwDraftPanel').hidden = true; notice('本机稿已恢复；冲突内容请先对照服务器版本。');
      });
      $('fwDraftList').appendChild(button);
    });
  });
  const leaveEditor = run(async () => { if (await mayLeave()) closeEditor(); });
  $('fwClose').onclick = leaveEditor; $('fwCancel').onclick = leaveEditor;
  $('fwSave').onclick = run(() => save(false)); $('fwSubmit').onclick = run(() => save(true));
  $('fwApprove').onclick = run(() => review('approved')); $('fwReject').onclick = run(() => review('rejected'));
  $('fwDelete').onclick = run(async () => { if (!await App.confirm('删除本人草稿？')) return; await locked(async () => { await api('/' + kind + '/' + draft.record.id, { method: 'DELETE', headers: { 'X-Record-Version': draft.record.lockVersion } }); dirty = false; removeDraft(); closeEditor(); await load(); }); });
  $('fwRevise').onclick = run(async () => { if (!await App.confirm('保留已审核原件并建立修订稿？', { danger: false })) return; const body = Object.assign({}, draft.body, { sourceRecordId: draft.record.id }); delete body.code; await show({ record: { status: 'draft' }, body, requestId: key() }, true); localSave(); });
  $('fwFork').onclick = run(async () => {
    if (!await App.confirm('原记录保持不变；另存副本可能形成重复填报，请核对后再提交。', { danger: false })) return;
    localSave();
    const body = Object.assign({}, draft.body); delete body.code; delete body.sourceRecordId;
    await show({ record: { status: 'draft' }, body, requestId: key() }, true); localSave();
  });
  $('fwHistory').onclick = run(async () => { const history = await api('/' + kind + '/' + draft.record.id + '/history'); $('fwHistoryContent').innerHTML = history.map(h => '<details><summary>' + esc(h.actorName + ' · ' + h.action + ' · ' + h.createTime) + '</summary><p>' + esc(h.reason) + '</p><pre>' + esc(JSON.stringify(JSON.parse(h.snapshot), null, 2)) + '</pre></details>').join(''); });
  $('fwCompare').onclick = run(async () => { const remote = await api('/' + kind + '/' + draft.record.id); $('fwHistoryContent').innerHTML = '<h3>服务器当前版本（本机表单未覆盖）</h3><pre>' + esc(JSON.stringify(remote, null, 2)) + '</pre>'; });
  $('fwPrefill').onclick = run(() => prefillLog(false));
  $('fwSource').onclick = run(async () => { cancelPrefill(); collect(); const source = await api('/field/diary-source?' + App.buildQuery({ projectId: draft.body.projectId, date: draft.body.logDate })); let filled = false; schema.fields.forEach(f => { if (!String(draft.body[f[0]] || '').trim() && source[f[0]]) { draft.body[f[0]] = source[f[0]]; filled = true; } }); if (filled) draft.body.diarySources = JSON.stringify(source.sources); form(draft.body, draft.record); localSave(); renderMedia(); renderGuide(); notice(filled ? '只填入空白栏目，来源已关联；请人工确认后提交。' : '没有可填入内容，原文未改变。'); });
  async function uploadPending() {
    await locked(async () => {
      const failed = [];
      for (const entry of pendingUploads) {
        try {
          $('fwUploadState').textContent = '正在上传：' + entry.file.name;
          if (!entry.remote) { entry.remote = await App.uploadFile(entry.file); assertAccount(); }
          const list = entry.file.type.startsWith('image/') ? photos : attachments;
          if (!list.some(p => p.url === entry.remote.url)) list.push(Object.assign({}, entry.remote, { source: 'album', description: '' }));
          localSave(); await renderMedia(); renderGuide();
        } catch (e) { entry.error = e.message; failed.push(entry); }
      }
      pendingUploads = failed;
      $('fwRetryUpload').hidden = !failed.length;
      $('fwUploadState').textContent = failed.length ? failed.map(x => x.file.name + '：' + x.error).join('；') + '。请勿关闭本页，可逐次重试。' : '全部附件已上传；请继续保存表单。';
    });
  }
  async function queueFiles(files) {
    const list = Array.from(files || []); if (!list.length) return;
    pendingUploads.push(...list.map(file => ({ file })));
    await uploadPending();
  }
  $('fwUpload').onchange = run(async event => { const files = Array.from(event.target.files || []); event.target.value = ''; await queueFiles(files); });
  $('fwRetryUpload').onclick = run(uploadPending);
  const dropzone = $('fwUploadLabel');
  ['dragenter', 'dragover'].forEach(name => dropzone.addEventListener(name, event => { event.preventDefault(); dropzone.classList.add('dragover'); }));
  ['dragleave', 'dragend'].forEach(name => dropzone.addEventListener(name, event => { event.preventDefault(); dropzone.classList.remove('dragover'); }));
  dropzone.addEventListener('drop', event => {
    event.preventDefault(); dropzone.classList.remove('dragover');
    const files = event.dataTransfer && event.dataTransfer.files;
    if (files && files.length) run(() => queueFiles(files))();
  });
  $('fwPrint').onclick = () => {
    const fields = Array.from($('fwForm').querySelectorAll('textarea, input, select'));
    fields.forEach(el => { const text = document.createElement('div'); text.className = 'fw-print-value'; text.textContent = el.tagName === 'SELECT' ? (el.options[el.selectedIndex] || {}).text || '' : el.value; text.style.whiteSpace = 'pre-wrap'; el.after(text); el.hidden = true; });
    const collapsed = Array.from($('fwForm').querySelectorAll('details:not([open])'));
    collapsed.forEach(el => { el.open = true; });
    document.body.classList.add('fw-print');
    try { window.print(); } finally { document.body.classList.remove('fw-print'); collapsed.forEach(el => { el.open = false; }); fields.forEach(el => { el.hidden = false; }); root.querySelectorAll('.fw-print-value').forEach(el => el.remove()); }
  };
  async function members() { const pid = $('fwProject').value; if (!pid) throw new Error('请先选择单一项目'); const list = await api('/projects/' + pid + '/members'); $('fwMemberPanel').hidden = false; $('fwMemberList').textContent = list.map(m => m.userId + ' · ' + m.userName + ' · ' + (m.enabled ? '启用' : '停用') + ' · 审核 ' + m.canReview).join('\n') || '暂无显式授权记录'; }
  $('fwMembers').onclick = run(members);
  $('fwMemberSave').onclick = run(async () => { const pid = $('fwProject').value; if (!pid) throw new Error('请选择项目'); if (!await App.confirm('确认变更当前项目的人员访问及审核权限？')) return; await api('/projects/' + pid + '/members', { method: 'PUT', body: { userId: Number($('fwMemberId').value), enabled: $('fwMemberEnabled').checked, canReview: $('fwMemberReview').checked } }); await members(); });
  window.addEventListener('beforeunload', event => { if (App.getToken() && (dirty || pendingUploads.length)) { event.preventDefault(); event.returnValue = ''; } });
  run(async () => {
    let n = 1;
    while (true) { const data = await App.apiGetPage('/projects', { pageNum: n++, pageSize: 100 }); projects.push(...data.list); if (projects.length >= data.total || !data.list.length) break; }
    $('fwProject').insertAdjacentHTML('beforeend', options(projects));
    if (hasTemplate) root.querySelector('thead th:nth-child(4)').textContent = AUTHOR_LABEL;
    await load();
    const params = new URLSearchParams(location.search), id = params.get('id');
    if (hasTemplate && id) {
      if (!/^\d+$/.test(id)) throw new Error(schema.title + '编号参数无效');
      await open(id, params.get('readonly') === 'true');
    }
  })();
})();
