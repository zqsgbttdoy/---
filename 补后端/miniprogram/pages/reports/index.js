const api = require('../../utils/api');
const reports = require('../../utils/reports');
const { schemas, today, statuses } = require('../../utils/schema');
const kinds = Object.keys(schemas);
Page({
  data: { kinds, kind: kinds[0], kindTitle: schemas[kinds[0]].title, types: kinds.map(k => ({ kind: k, title: schemas[k].title })),
    projects: [], projectIndex: -1, dateFrom: reports.shiftDate(today(), -6), dateTo: today(),
    summary: null, statusList: [], authorNames: {}, rows: [], loading: false, error: '' },
  async onShow() {
    if (!api.userId()) { wx.reLaunch({ url: '/pages/login/index' }); return; }
    this.setData({ isAdmin: api.isAdmin() });
    const cacheKey = api.namespace() + ':projects:' + api.userId();
    const projects = wx.getStorageSync(cacheKey) || [];
    const lastId = wx.getStorageSync(cacheKey + ':last');
    this.setData({ projects, projectIndex: Math.max(0, projects.findIndex(p => p.id === lastId)) });
    if (projects.length) this.reload().catch(e => this.fail(e));
  },
  fail(e) { this.setData({ error: e.message, loading: false }); if (e.code === 401) wx.reLaunch({ url: '/pages/login/index' }); },
  selectKind(e) { this.setData({ kind: e.currentTarget.dataset.kind, kindTitle: schemas[e.currentTarget.dataset.kind].title }); this.reload().catch(e => this.fail(e)); },
  selectProject(e) { this.setData({ projectIndex: Number(e.detail.value) }); this.reload().catch(e => this.fail(e)); },
  pickFrom(e) { this.setData({ dateFrom: e.detail.value }); this.reload().catch(e => this.fail(e)); },
  pickTo(e) { this.setData({ dateTo: e.detail.value }); this.reload().catch(e => this.fail(e)); },
  async reload() {
    const uid = api.userId();
    const project = this.data.projects[this.data.projectIndex];
    if (!project) { this.setData({ error: '没有获授权的项目，请联系管理员' }); return; }
    if (this.data.dateFrom > this.data.dateTo) { this.setData({ error: '开始日期不能晚于结束日期' }); return; }
    this.setData({ loading: true, error: '' });
    try {
      const params = { projectId: project.id, dateFrom: this.data.dateFrom, dateTo: this.data.dateTo };
      const records = await reports.fetchAll(api.request, '/' + this.data.kind, params);
      api.assertUser(uid);
      const summary = reports.aggregate(records);
      const authorNames = Object.assign({}, this.data.authorNames);
      try {
        const users = await reports.fetchAll(api.request, '/users', {}, 100);
        users.forEach(u => { authorNames[String(u.id)] = u.name; });
      } catch (e) { /* 用户字典失败时退回显示ID，不阻断报表。 */ }
      api.assertUser(uid);
      const statusList = ['draft', 'submitted', 'approved', 'rejected'].filter(s => summary.statusCounts[s])
        .map(s => ({ label: statuses[s] || s, count: summary.statusCounts[s] }));
      this.setData({ summary, statusList, authorNames, rows: summary.authors.map(a => Object.assign({}, a, {
        name: authorNames[a.authorId] || ('用户' + a.authorId),
        statusText: ['draft', 'submitted', 'approved', 'rejected'].map(s => (statuses[s] || s) + ' ' + a[s]).join(' · ')
      })), loading: false });
    } catch (e) { this.fail(e); }
  }
});
