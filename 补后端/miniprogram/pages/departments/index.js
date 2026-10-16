const api = require('../../utils/api');
Page({
  data: { rows: [], total: 0, keyword: '', loading: false, error: '', isAdmin: false, form: null, parentNames: {}, saving: false },
  onShow() {
    if (!api.userId()) { wx.reLaunch({ url: '/pages/login/index' }); return; }
    const isAdmin = api.isAdmin();
    this.setData({ isAdmin, error: '' });
    if (!isAdmin) return;
    this.load().catch(e => this.fail(e));
  },
  fail(e) { this.setData({ error: e.message, loading: false }); if (e.code === 401) wx.reLaunch({ url: '/pages/login/index' }); },
  input(e) { this.setData({ keyword: e.detail.value }); },
  formInput(e) { this.setData({ ['form.' + e.currentTarget.dataset.field]: e.detail.value }); },
  async load() {
    if (this.data.loading) return;
    const uid = api.userId();
    this.setData({ loading: true });
    try {
      const data = await api.request('/departments', 'GET', { pageNum: 1, pageSize: 200, name: this.data.keyword || undefined });
      api.assertUser(uid);
      const parentNames = {}; data.list.forEach(d => { parentNames[String(d.id)] = d.name; });
      const rows = data.list.slice().sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0)).map(d => Object.assign({}, d, {
        parentText: d.parentId ? (parentNames[String(d.parentId)] || '上级已删除') : '顶级部门',
        statusText: d.status === 'active' ? '正常' : d.status || '未登记',
        leaderText: d.leader || '未指定', descText: d.description || '无描述'
      }));
      this.setData({ rows, total: data.total, parentNames, error: '' });
    } finally { if (uid === api.userId()) this.setData({ loading: false }); }
  },
  create() {
    const options = [{ id: '', name: '顶级部门' }].concat(this.data.rows.map(d => ({ id: d.id, name: d.name })));
    this.setData({ options, form: { id: null, name: '', parentId: '', parentName: '顶级部门', leader: '', description: '', sortOrder: String(options.length) } });
  },
  edit(e) {
    const d = this.data.rows.find(r => String(r.id) === String(e.currentTarget.dataset.id)); if (!d) return;
    const options = [{ id: '', name: '顶级部门' }].concat(this.data.rows.filter(r => r.id !== d.id).map(r => ({ id: r.id, name: r.name })));
    this.setData({ options, form: { id: d.id, name: d.name, parentId: d.parentId ? String(d.parentId) : '',
      parentName: d.parentId ? (this.data.parentNames[String(d.parentId)] || '顶级部门') : '顶级部门',
      leader: d.leader || '', description: d.description || '', sortOrder: d.sortOrder == null ? '' : String(d.sortOrder) } });
  },
  pickParent(e) {
    const opt = this.data.options[Number(e.detail.value)];
    this.setData({ 'form.parentId': opt.id, 'form.parentName': opt.name });
  },
  closeForm() { this.setData({ form: null }); },
  async save() {
    const form = this.data.form;
    if (!form.name.trim()) { this.setData({ error: '部门名称不能为空' }); return; }
    if (form.name.trim().length > 100) { this.setData({ error: '部门名称不能超过100个字符' }); return; }
    if (form.description && form.description.length > 500) { this.setData({ error: '描述不能超过500个字符' }); return; }
    const order = form.sortOrder === '' ? null : Number(form.sortOrder);
    if (order != null && (!Number.isInteger(order) || order < 0)) { this.setData({ error: '排序必须是 0 或正整数' }); return; }
    const uid = api.userId();
    const body = { name: form.name.trim(), parentId: form.parentId ? Number(form.parentId) : null,
      leader: form.leader.trim(), description: form.description.trim(), sortOrder: order, status: 'active' };
    this.setData({ saving: true, error: '' });
    try {
      if (form.id) await api.request('/departments/' + form.id, 'PUT', body);
      else await api.request('/departments', 'POST', body);
      api.assertUser(uid);
      wx.showToast({ title: '已保存', icon: 'success' });
      this.setData({ form: null, saving: false });
      this.load().catch(e => this.fail(e));
    } catch (e) { this.setData({ saving: false }); this.fail(e); }
  },
  remove(e) {
    const d = this.data.rows.find(r => String(r.id) === String(e.currentTarget.dataset.id)); if (!d) return;
    const kids = this.data.rows.filter(r => String(r.parentId) === String(d.id));
    if (kids.length) { this.setData({ error: '「' + d.name + '」下还有 ' + kids.length + ' 个子部门，请先处理子部门' }); return; }
    wx.showModal({ title: '删除部门', content: '确认删除「' + d.name + '」？已关联该部门的人员不受影响，但需重新指派。',
      success: async res => {
        if (!res.confirm) return;
        const uid = api.userId();
        try {
          await api.request('/departments/' + d.id, 'DELETE');
          api.assertUser(uid); wx.showToast({ title: '已删除', icon: 'success' });
          this.load().catch(e => this.fail(e));
        } catch (e) { this.fail(e); }
      } });
  }
});
