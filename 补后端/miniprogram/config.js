// 本地预览默认指向 e2e 静态代理（8081 的 /api 透传到后端 8080）；需在开发者工具勾选「不校验合法域名」。
// 正式发布前必须改为已备案的 HTTPS API 域名（不要追加 /api），并在微信后台配置 request/uploadFile 合法域名。
module.exports = {
  baseUrl: 'http://127.0.0.1:8081',
  // 微信后台添加同声传译插件且 app.json 声明插件后再启用。
  voiceEnabled: false
};
