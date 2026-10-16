# 微信小程序适配层 · 本地启动与部署指南

> 本目录是「监理现场管理系统」的微信小程序端，只做现场填报与轻量查询；
> 汇总归档、权限配置等重操作仍由 PC 网页端完成。**本目录的所有改动不影响原 Web 页面。**

## 一、这个小程序能做什么

| 页面 | 功能 | 谁能用 |
| --- | --- | --- |
| 登录 | 与 PC 端相同的账号密码登录 | 所有人 |
| 现场工作台 | 监理日记/日志/日常巡检/旁站 四类记录填写、拍照、语音转文字、离线草稿、提交审批 | 所有人（审批需项目审核权） |
| 人员管理 | 按姓名/状态搜索人员、查看详情；调整在职状态、重置密码 | 查看：所有人；编辑：仅管理员 |
| 部门管理 | 部门列表、新增/编辑/删除 | 仅管理员（后端接口整类要求 admin 角色） |
| 报表统计 | 按项目+日期区间统计四类记录的数量、状态分布、按人员明细 | 所有人（只统计自己可见的数据） |

## 二、本地预览（零基础三步走）

### 第 1 步：启动后端 + 8081 代理

> ⭐ **最简单**：到项目根目录双击 **一键启动.bat**（同时启动后端 8080 与代理 8081），再双击 **生成演示数据.bat** 灌入演示数据，即可跳到第 2 步。
> 下面是手工启动方式，仅在一键启动失败时使用。

在项目根目录（`完整版-监理 - qoder` 文件夹）打开 PowerShell，逐行复制执行：

```powershell
cd "补后端\backend"
$env:SPRING_PROFILES_ACTIVE = "e2e"
java -jar target\supervision-site-management-1.0.0.jar
```

> ❌ 提示 `Unable to access jarfile`？先到根目录双击 **一键启动.bat**（它会自动构建 jar），
> 再双击 **停止服务.bat** 关掉，然后重新执行本步骤。

看到日志出现 `Started SupervisionApplication` 后，**另开一个** PowerShell 窗口：

```powershell
cd "e:\希地工作\trae 文件保存\完整版-监理 - qoder"
node e2e-static-server.js
```

> ❌ 后端启动报 `Port 8080 was already in use`？说明您电脑上另有程序占用了 8080。
> 不需要关掉它，把后端改放到 8090 即可——第一步的启动命令改为：
> `java -jar target\supervision-site-management-1.0.0.jar --server.port=8090`，
> 并在第二条命令前多加一行：`$env:E2E_BACKEND_PORT = "8090"`，再启动 `node e2e-static-server.js`。
> （8081 对小程序的地址不变，无需修改 config.js。）

✅ **如何确认成功**：浏览器打开 `http://127.0.0.1:8081/login.html` 能看到登录页即为就绪。
（8081 代理会把 `/api` 转给后端——默认 8080，按上文改端口后为 8090，与小程序 `config.js` 中预设的 `http://127.0.0.1:8081` 地址一致。）

### 第 2 步：用微信开发者工具打开小程序

1. 下载安装 [微信开发者工具](https://developers.weixin.qq.com/miniprogram/dev/devtools/download.html)（选「稳定版」）。
2. 打开后选择 **导入项目**，目录选本文件夹（`补后端\miniprogram`），AppID 点「使用测试号（touristappid）」。
3. 导入后若请求失败：右上角 **详情 → 本地设置 → 勾选「不校验合法域名…」**（本项目 `project.config.json` 已默认关闭校验，若无效请手动确认该勾选项）。

### 第 3 步：登录验证

在模拟器登录页输入 `admin / 123456`（e2e 演示模式初始账号），进入工作台能看到项目下拉即成功。
❌ 报「网络不可用」→ 回到第 1 步确认两个窗口都没关闭；❌ 报「用户名或密码错误」→ 演示库重启后数据会重置，用初始密码即可。

## 三、正式发布前必改清单

| # | 事项 | 怎么做 |
| --- | --- | --- |
| 1 | API 域名 | 修改 `config.js` 的 `baseUrl` 为已备案 **HTTPS** 域名（如 `https://api你的域名`，不要带 `/api`）。代码层会强制拒绝非本机 http 地址 |
| 2 | AppID | `project.config.json` 的 `touristappid` 换成正式 AppID |
| 3 | 合法域名 | 微信公众平台「开发管理 → 开发设置 → 服务器域名」把 API 域名加入 request 与 uploadFile 白名单 |
| 4 | 隐私协议 | 公众平台配置《隐私保护指引》（相机、相册、麦克风用途），否则 `wx.openPrivacyContract` 与录音/拍照会被拦截 |
| 5 | 语音识别 | 需要语音转文字时：公众平台添加「微信同声传译」插件，并把 `config.js` 的 `voiceEnabled` 改为 `true`（配额：单次 60 秒、每分钟 250 条） |
| 6 | urlCheck | `project.config.json` 的 `setting.urlCheck` 改回 `true`，恢复域名校验 |

后端生产部署（Nginx 反代 `/api`、HTTPS 证书、MySQL）请交给运维按根目录 `ALIYUN_DEPLOYMENT.md` 与 `deploy/` 脚本执行；小程序只需指向该 HTTPS 域名。

## 四、目录结构与编码约定

```
miniprogram/
├── app.js / app.json / app.wxss     入口、页面注册、全局样式（复用 PC 端色彩令牌）
├── config.js                        API 域名与语音开关（发布前必查）
├── pages/                           login / home(工作台) / record(四类记录表单)
│                                    personnel(人员) / departments(部门) / reports(报表)
├── utils/
│   ├── api.js                       统一请求封装：Token 存储、401 清会话、按账号隔离、域名校验
│   ├── drafts.js                    本机离线草稿：账号隔离、幂等同步、配额保护
│   ├── schema.js                    四类记录字段驱动（与 PC 端 assets/field-records.js 同契约）
│   ├── reports.js                   报表纯逻辑（日期偏移/分页取数/聚合），可 node 直接单测
│   └── voice.js                     录音与同声传译插件封装
└── tests/                           模拟单元测试（不连真实网络/微信）
```

约定：页面只负责展示与交互，可测逻辑一律放进 `utils/`；所有写请求走 `utils/api.js`，
禁止页面内直接调 `wx.request`；接口契约遵循后端 `ApiResponse{code,data}` 与 `PageResult{list,total}`。

## 五、运行模拟测试

在项目根目录执行：

```powershell
node --test "补后端/miniprogram/tests/drafts.test.cjs" "补后端/miniprogram/tests/api.test.cjs" "补后端/miniprogram/tests/reports.test.cjs"
```

✅ 成功标志：末尾输出 `pass 50, fail 0`。这些测试用「桩」模拟微信环境与服务器，
不需要启动后端，也不发送任何真实请求。
