const weather = [['weather', '天气'], ['wind', '风力'], ['temperature', '气温']];
const work = [['management', '管理情况'], ['inspection', '巡检情况'], ['morningMeeting', '晨会情况'], ['drawingReview', '图纸审查'], ['otherMatters', '其他事项']];
const schemas = {
  'supervision-diaries': { title: '监理日记', date: 'diaryDate', fields: weather.concat([['construction', '施工情况']], work) },
  'supervision-logs': { title: '监理日志', date: 'logDate', fields: weather.concat([['content', '工作内容']], work) },
  'daily-inspections': { title: '日常巡检', date: 'inspectionDate', fields: [['location', '检查部位'], ['content', '巡视内容'], ['issues', '发现问题']] },
  'sideline-inspections': { title: '旁站检查', date: 'inspectionDate', fields: [['location', '旁站部位 *'], ['process', '旁站工序 *'], ['startTime', '开始时间（YYYY-MM-DD HH:mm:ss）*'], ['endTime', '结束时间（YYYY-MM-DD HH:mm:ss）*'], ['manageCount', '施工管理人员数（非负整数）*'], ['workerCount', '施工人员数（非负整数）*'], ['result', '旁站结果 *'], ['docsText', '关联工程资料编号 / 名称（每行一项）*'], ['issuesText', '发现问题及处理情况'], ['content', '其他旁站记录']] }
};
function today() { const d = new Date(); return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0'); }
const statuses = { draft: '草稿', submitted: '待审核', pending: '待审核', approved: '审核通过', rejected: '已退回', recorded: '已记录', fixed: '已整改' };
function unpack(kind, input) {
  const body = Object.assign({}, input);
  if (kind === 'sideline-inspections') {
    try {
      const detail = JSON.parse(body.content);
      if (detail && typeof detail === 'object') {
        Object.assign(body, { process: detail.process || '', result: detail.result || '', manageCount: detail.manageCount,
          workerCount: detail.workerCount, docsText: (detail.docs || []).join('\n'),
          issuesText: typeof detail.issues === 'string' ? detail.issues : JSON.stringify(detail.issues || []), content: detail.text || '' });
      }
    } catch (e) { /* 旧文字记录原样保留，正式提交时补全旁站必填项。 */ }
  }
  return body;
}
function pack(kind, input) {
  const body = Object.assign({}, input);
  if (kind === 'sideline-inspections') {
    body.content = JSON.stringify({ process: body.process || '', result: body.result || '',
      manageCount: body.manageCount === '' || body.manageCount == null ? null : Number(body.manageCount),
      workerCount: body.workerCount === '' || body.workerCount == null ? null : Number(body.workerCount),
      docs: (body.docsText || '').split('\n').map(s => s.trim()).filter(Boolean), issues: body.issuesText || '', text: body.content || '' });
    ['process', 'result', 'manageCount', 'workerCount', 'docsText', 'issuesText'].forEach(k => delete body[k]);
    ['startTime', 'endTime'].forEach(k => { if (body[k]) body[k] = body[k].replace(' ', 'T'); else body[k] = null; });
  }
  return body;
}
const fieldSchema = { schemas, today, statuses, pack, unpack };
if (typeof module !== 'undefined') module.exports = fieldSchema;
else window.FieldSchema = fieldSchema;
