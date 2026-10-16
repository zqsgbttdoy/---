const drafts = require('./utils/drafts');
App({
  globalData: { foreground: false, online: true },
  onLaunch() {
    wx.onNetworkStatusChange(res => {
      this.globalData.online = res.isConnected;
      if (res.isConnected && this.globalData.foreground) drafts.syncQueued().catch(() => {});
    });
  },
  onShow() {
    this.globalData.foreground = true;
    wx.getNetworkType({ success: res => {
      this.globalData.online = res.networkType !== 'none';
      if (this.globalData.online) drafts.syncQueued().catch(() => {});
    } });
  },
  onHide() { this.globalData.foreground = false; }
});
