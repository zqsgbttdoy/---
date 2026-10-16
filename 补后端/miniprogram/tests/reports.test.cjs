// 运行：node --test 补后端/miniprogram/tests/reports.test.cjs
// 直接引入 utils/reports.js（纯逻辑，无 wx 依赖），验证日期偏移、分页取数与聚合规则。
const { test } = require('node:test');
const assert = require('node:assert/strict');
const reports = require('../utils/reports');

test('shiftDate 跨月跨年且拒绝非法格式', () => {
  assert.equal(reports.shiftDate('2026-01-05', -6), '2025-12-30');
  assert.equal(reports.shiftDate('2026-02-28', 1), '2026-03-01');
  assert.equal(reports.shiftDate('2024-02-28', 1), '2024-02-29');
  assert.equal(reports.shiftDate('2026-12-31', 1), '2027-01-01');
  assert.throws(() => reports.shiftDate('2026-1-5', 0), /YYYY-MM-DD/);
  assert.throws(() => reports.shiftDate('abc', 0), /YYYY-MM-DD/);
});

test('fetchAll 取完所有分页后停止，不静默少计', async () => {
  const pages = [
    { list: [{ id: 1 }, { id: 2 }], total: 5 },
    { list: [{ id: 3 }, { id: 4 }], total: 5 },
    { list: [{ id: 5 }], total: 5 }
  ];
  const calls = [];
  const request = async (path, method, params) => { calls.push(params); return pages.shift(); };
  const list = await reports.fetchAll(request, '/supervision-diaries', { projectId: 9 });
  assert.deepEqual(list.map(r => r.id), [1, 2, 3, 4, 5]);
  assert.equal(calls.length, 3);
  assert.equal(calls[0].projectId, 9);
  assert.equal(calls[1].pageNum, 2);
});

test('fetchAll 遇到空页立即返回，防止死循环', async () => {
  const request = async () => ({ list: [], total: 999 });
  assert.deepEqual(await reports.fetchAll(request, '/x', {}), []);
});

test('aggregate 统计状态分布并按作者去重工作日', () => {
  const records = [
    { authorId: 1, status: 'approved', diaryDate: '2026-09-21' },
    { authorId: 1, status: 'approved', diaryDate: '2026-09-21' },
    { authorId: 1, status: 'draft', diaryDate: '2026-09-22' },
    { authorId: 2, status: 'rejected', logDate: '2026-09-22' },
    { authorId: 2, status: 'submitted', inspectionDate: '2026-09-23' }
  ];
  const result = reports.aggregate(records);
  assert.equal(result.total, 5);
  assert.deepEqual(result.statusCounts, { approved: 2, draft: 1, rejected: 1, submitted: 1 });
  assert.deepEqual(result.authors.map(a => a.authorId), ['1', '2']);
  assert.equal(result.authors[0].total, 3);
  assert.equal(result.authors[0].workDays, 2, '同人同日两条记录只能计两个工作日中的两天');
  assert.equal(result.authors[0].approved, 2);
  assert.equal(result.authors[1].workDays, 2);
  assert.equal(result.authors[1].rejected, 1);
});

test('aggregate 处理空输入与缺失作者', () => {
  assert.deepEqual(reports.aggregate([]), { total: 0, statusCounts: {}, authors: [] });
  const result = reports.aggregate([{ status: 'approved' }]);
  assert.equal(result.authors[0].authorId, 'unknown');
  assert.equal(result.authors[0].workDays, 0);
});

test('aggregate 同数量作者按 ID 稳定排序', () => {
  const result = reports.aggregate([
    { authorId: 9, status: 'draft', diaryDate: '2026-09-21' },
    { authorId: 3, status: 'draft', diaryDate: '2026-09-21' }
  ]);
  assert.deepEqual(result.authors.map(a => a.authorId), ['3', '9']);
});

test('formatDate 补零输出', () => {
  assert.equal(reports.formatDate(new Date(2026, 8, 5)), '2026-09-05');
});
