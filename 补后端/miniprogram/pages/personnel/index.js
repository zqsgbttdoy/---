const api = require('../../utils/api');
const statusText = { active: '在职', inactive: '离职', leave: '休假' };
Page({
  data: { keyword: '', status: '', rows: [], total: 0, page: 1, more: false, loading: false, error: '', isAdmin: false, deptNames: {}, expandedId: null },
  onShow() {
    if (!api.userId()) { wx.reLaunch({ url: '/pages/login/index' }); return; }
    const isAdmin = api.isAdmin();
    this.setData({ isAdmin });
    if (isAdmin && !Object.keys(this.data.deptNames).length) this.loadDepartments();
    this.reset().catch(e => this.fail(e));
  },
  fail(e) { this.setData({ error: e.message, loading: false }); if (e.code === 401) wx.reLaunch({ url: '/pages/login/index' }); },
  input(e) { this.setData({ keyword: e.detail.value }); },
  pickStatus(e) { this.setData({ status: e.currentTarget.dataset.status || '' }); this.reset().catch(e => this.fail(e)); },
  async loadDepartments() {
    try {
      const uid = api.userId();
      const data = await api.request('/departments', 'GET', { pageNum: 1, pageSize: 200 });
      api.assertUser(uid);
      const deptNames = {}; data.list.forEach(d => { deptNames[String(d.id)] = d.name; });
      this.setData({ deptNames });
    } catch (e) { /* 部门字典失败仅影响名称展示。 */ }
  },
  reset() { this.page = 1; this.setData({ rows: [], more: false }); return this.load(); },
  async load() {
    if (this.data.loading) return;
    const seq = this.sequence = (this.sequence || 0) + 1, uid = api.userId();
    this.setData({ loading: true });
    try {
      const data = await api.request('/users', 'GET', { pageNum: this.page || 1, pageSize: 20,
        name: this.data.keyword || undefined, status: this.data.status || undefined });
      if (seq !== this.sequence) return;
      api.assertUser(uid);
      const rows = this.data.rows.concat(data.list.map(u => Object.assign({}, u, {
        statusLabel: statusText[u.status] || u.status,
        deptName: this.data.deptNames[String(u.deptId)] || '',
        hireText: u.hireDate || '未登记', genderText: u.gender === 'male' ? '男' : u.gender === 'female' ? '女' : u.gender || '未登记'
      })));
      this.setData({ rows, total: data.total, more: rows.length < data.total, error: '' });
    } finally { if (seq === this.sequence) this.setData({ loading: false }); }
  },
  async more() { if (this.data.loading || !this.data.more) return; this.page = (this.page || 1) + 1; try { await this.load(); } catch (e) { this.page--; this.fail(e); } },
  onHide() { this.sequence = (this.sequence || 0) + 1; },
  expand(e) { const id = e.currentTarget.dataset.id; this.setData({ expandedId: this.data.expandedId === id ? null : id }); },
  toggleStatus(e) {
    const user = this.data.rows.find(r => r.id === e.currentTarget.dataset.id); if (!user) return;
    const options = ['在职', '离职', '休假'];
    wx.showActionSheet({ itemList: options.map(o => '设为' + o + (statusText[user.status] === o ? '（当前）' : '')),
      success: res => {
        const next = ['active', 'inactive', 'leave'][res.tapIndex];
        if (next === user.status) { wx.showToast({ title: '状态未变化', icon: 'none' }); return; }
        wx.showModal({ title: '调整在职状态', content: '确认将「' + user.name + '」设为' + statusText[next] + '？' + (next === 'active' ? '' : '非在职账号将无法登录任何端。'),
          success: r => { if (r.confirm) this.doToggle(user, next); } });
      } });
  },
  async doToggle(user, next) {
    const uid = api.userId();
    try {
      await api.request('/users/' + user.id + '/status?status=' + encodeURIComponent(next), 'PUT');
      api.assertUser(uid); wx.showToast({ title: '已设为' + statusText[next], icon: 'success' });
      this.reset().catch(e => this.fail(e));
    } catch (e) { this.fail(e); }
  },
  resetPassword(e) {
    const user = this.data.rows.find(r => r.id === e.currentTarget.dataset.id); if (!user) return;
    wx.showModal({ title: '重置密码', content: '为「' + user.name + '」生成新的随机密码？重置后旧密码立即失效，请当面告知本人。',
      success: async res => {
        if (!res.confirm) return;
        const uid = api.userId();
        try {
          const password = await api.request('/users/' + user.id + '/reset-password', 'PUT');
          api.assertUser(uid);
          wx.showModal({ title: '重置成功', content: '新密码：' + password + '（仅本次可见，请尽快转告本人修改）', showCancel: false });
        } catch (e) { this.fail(e); }
      } });
  }
});
