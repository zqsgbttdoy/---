// 报表统计纯逻辑：日期偏移、分页全量拉取、按状态/作者聚合。不依赖 wx，可用 node --test 直接验证。
function pad(n) { return String(n).padStart(2, '0'); }
function formatDate(d) { return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()); }
// base 为 YYYY-MM-DD，days 为偏移天数（可为负）；用 UTC 避免夏令时/时区导致的日期漂移。
function shiftDate(base, days) {
  const parts = /^(\d{4})-(\d{2})-(\d{2})$/.exec(base);
  if (!parts) throw new Error('日期格式应为 YYYY-MM-DD');
  const d = new Date(Date.UTC(+parts[1], +parts[2] - 1, +parts[3] + days));
  return d.getUTCFullYear() + '-' + pad(d.getUTCMonth() + 1) + '-' + pad(d.getUTCDate());
}
// 循环取完分页数据；total 超过已取数量且页非空时继续，拒绝静默少计。
async function fetchAll(request, path, params, pageSize = 100) {
  const list = []; let page = 1;
  while (true) {
    const data = await request(path, 'GET', Object.assign({}, params, { pageNum: page++, pageSize }));
    const rows = data.list || [];
    list.push(...rows);
    if (!rows.length || list.length >= (data.total == null ? list.length : data.total)) return list;
  }
}
// records 为四类现场记录列表；返回总数、状态计数与按作者聚合明细。
function aggregate(records) {
  const statusCounts = {}, authors = new Map();
  for (const r of records || []) {
    statusCounts[r.status] = (statusCounts[r.status] || 0) + 1;
    const key = String(r.authorId == null ? 'unknown' : r.authorId);
    const a = authors.get(key) || { authorId: key, total: 0, approved: 0, submitted: 0, draft: 0, rejected: 0, dates: new Set() };
    a.total++;
    if (a[r.status] != null) a[r.status]++;
    if (r.diaryDate) a.dates.add(r.diaryDate); else if (r.logDate) a.dates.add(r.logDate); else if (r.inspectionDate) a.dates.add(r.inspectionDate);
    authors.set(key, a);
  }
  const authorList = Array.from(authors.values())
    .map(a => ({ authorId: a.authorId, total: a.total, approved: a.approved, submitted: a.submitted, draft: a.draft, rejected: a.rejected, workDays: a.dates.size }))
    .sort((x, y) => y.total - x.total || String(x.authorId).localeCompare(String(y.authorId)));
  return { total: (records || []).length, statusCounts, authors: authorList };
}
module.exports = { shiftDate, fetchAll, aggregate, formatDate };
