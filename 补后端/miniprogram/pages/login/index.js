const api = require('../../utils/api');
Page({
  data: { username: '', password: '', busy: false, error: '', consent: false },
  onShow() { if (api.userId()) wx.redirectTo({ url: '/pages/home/index' }); },
  input(e) { this.setData({ [e.currentTarget.dataset.field]: e.detail.value }); },
  consent(e) { this.setData({ consent: e.detail.value.includes('yes') }); },
  privacy() { wx.openPrivacyContract({ fail: () => this.setData({ error: '请管理员先在微信后台配置隐私保护指引' }) }); },
  async login() {
    if (this.data.busy) return;
    if (!this.data.consent) { this.setData({ error: '请先阅读并同意隐私保护说明' }); return; }
    if (!this.data.username || !this.data.password) { this.setData({ error: '请输入账号和密码' }); return; }
    this.setData({ busy: true, error: '' });
    try {
      const session = await api.request('/auth/login', 'POST', { username: this.data.username, password: this.data.password });
      api.saveSession(session); this.setData({ password: '' });
      wx.redirectTo({ url: '/pages/home/index' });
    } catch (e) { this.setData({ error: e.message }); }
    finally { this.setData({ busy: false }); }
  }
});
