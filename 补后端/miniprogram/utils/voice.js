const config = require('../config');
let current = null;
function finish(session, error, text) {
  if (current !== session) return;
  current = null;
  session.onEnd();
  if (session.cancelled) return;
  if (error) session.onError(error);
  else if (text) session.onText(text);
}
function stopManager(session) {
  if (session.phase === 'stopping') return;
  session.phase = 'stopping';
  try { session.manager.stop(); }
  catch (e) { finish(session, new Error('未能结束录音，请重试或直接输入')); }
}
function start(onText, onError, onEnd) {
  if (!config.voiceEnabled) { onEnd(); onError(new Error('尚未开通同声传译插件，请先使用文字输入')); return; }
  if (current) { onEnd(); onError(new Error('正在准备或结束录音，请稍后重试')); return; }
  const session = current = { onText, onError, onEnd, phase: 'preparing', cancelled: false, stopRequested: false };
  wx.getNetworkType({ success(res) {
    if (current !== session) return;
    if (res.networkType === 'none') { finish(session, new Error('语音识别需要联网；可先离线填写文字')); return; }
    wx.authorize({ scope: 'scope.record', success() {
      if (current !== session) return;
      try {
        const manager = session.manager = requirePlugin('WechatSI').getRecordRecognitionManager();
        manager.onStart = () => {
          if (current !== session) return;
          session.phase = 'recording';
          if (session.stopRequested) stopManager(session);
        };
        manager.onStop = result => finish(session, null, result && result.result);
        manager.onError = () => finish(session, new Error('语音识别失败，请重试或直接输入'));
        session.phase = 'starting';
        manager.start({ duration: 60000, lang: 'zh_CN' });
      } catch (e) { finish(session, new Error('语音插件不可用，请使用文字输入')); }
    }, fail() { finish(session, new Error('未授权录音，仍可直接填写文字')); } });
  }, fail() { finish(session, new Error('无法检查网络，请使用文字输入')); } });
}
function stop(cancel = false) {
  const session = current;
  if (!session) return;
  session.cancelled = session.cancelled || cancel;
  session.stopRequested = true;
  if (session.phase === 'preparing') { session.cancelled = true; finish(session); }
  else if (session.phase === 'recording') stopManager(session);
}
module.exports = { start, stop };
