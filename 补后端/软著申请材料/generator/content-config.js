/**
 * content-config.js
 * 文档内容配置：业务模块中文描述、Controller 中文名称、端点说明启发式规则、
 * 页面操作手册（进入路径 / 主要功能 / 操作步骤）。
 * 供 generate-docx.js 生成 Markdown 源稿时使用；人工可直接编辑生成后的
 * 软著申请材料.md，无需改动本文件。
 */
'use strict';

/** Controller 类名 → 中文模块名 */
const CONTROLLER_NAMES = {
  AuthController: '认证登录',
  DailyInspectionController: '日常巡检',
  DashboardController: '工作台',
  DepartmentController: '部门管理',
  FileUploadController: '文件上传',
  MeetingMinutesController: '会议纪要',
  MessageController: '消息中心',
  OperationLogController: '操作日志',
  PermissionController: '权限管理',
  ProjectController: '项目台账',
  ProjectDocumentController: '项目文档',
  RoleController: '角色管理',
  SafetyLogController: '安全日志',
  SidelineInspectionController: '旁站巡检',
  SubItemDatabaseController: '分部分项数据库',
  SupervisionDiaryController: '监理日记',
  SupervisionLogController: '监理日志',
  SupervisionMonthlyReportController: '监理月报',
  SupervisionNoticeController: '监理通知单',
  UserController: '用户管理',
  WorkContactController: '工作联系单',
  WorkStandardConfigController: '工作标准配置',
};

/** 第一章：按业务模块的功能说明 */
const FUNCTION_MODULES = [
  {
    name: '认证与安全模块',
    description:
      '提供系统统一登录入口。用户使用手机号与密码登录，服务端采用 BCrypt 算法校验密码摘要，校验通过后签发 JWT（JSON Web Token，HS256 算法）访问令牌；前端后续请求通过 Authorization 请求头携带令牌完成身份认证。模块同时提供退出登录、获取当前登录用户信息、修改密码等功能，登录口令全程不以明文存储。',
  },
  {
    name: '工作台模块',
    description:
      '登录后的系统首页驾驶舱。汇总展示当前用户可见的项目数量、各类监理业务单据（日记、日志、巡检、通知单等）的数量统计与待办事项，提供各业务模块的快捷入口，帮助监理人员快速掌握项目整体情况。',
  },
  {
    name: '项目台账模块',
    description:
      '对监理项目进行全生命周期台账管理，支持项目的新增、修改、删除与分页查询，记录项目名称、编号、建设单位、施工单位、监理单位、工期、状态等信息，是全系统各业务单据的项目归属基础。',
  },
  {
    name: '组织与权限模块（用户/角色/权限/部门）',
    description:
      '实现基于 RBAC（基于角色的访问控制）模型的组织权限体系。部门管理支持树形部门结构维护；用户管理（人员管理）支持监理人员档案的增删改查、状态启停用与密码重置；角色管理支持角色定义及角色-权限绑定；权限管理支持按模块维护权限点。用户通过用户-角色、角色-权限两级关联获得系统操作权限。',
  },
  {
    name: '监理日记模块',
    description:
      '监理人员按日填写个人监理日记，记录当日天气、施工部位、施工情况、监理工作情况、存在问题及处理意见等，支持新增、修改、删除、分页查询与审批流转（提交/审批通过/退回），并提供日记统计与日记报表页面进行汇总分析。',
  },
  {
    name: '监理日志模块',
    description:
      '以项目为单位按日填写项目监理日志，内容涵盖施工动态、监理巡视旁站情况、验收与材料进场情况、安全文明施工情况等，支持增删改查与审批流转，并配套日志统计页面按时间区间汇总。',
  },
  {
    name: '安全日志模块',
    description:
      '专项记录施工现场安全监理工作，包括安全巡查情况、危险性较大分部分项工程管控、安全隐患及整改闭环情况等，支持增删改查与审批流转，并配套安全日志统计页面进行安全态势分析。',
  },
  {
    name: '巡检管理模块（日常巡检/旁站巡检）',
    description:
      '日常巡检记录监理人员对施工现场的日常检查情况，包括检查部位、检查内容、发现问题与处理结果；旁站巡检针对关键部位、关键工序的旁站监理进行记录，包括旁站部位、起止时间、施工情况与监理意见。两类记录均支持增删改查与分页检索。',
  },
  {
    name: '监理文书模块（通知单/联系单/会议纪要）',
    description:
      '监理通知单用于向施工单位签发整改、停工等监理指令并跟踪回复闭环；工作联系单用于参建各方之间的日常工作联系与事项沟通；会议纪要用于记录监理例会、专题会议的议题、决议与参会人员。三类文书均支持编号管理、增删改查与状态流转。',
  },
  {
    name: '分部分项数据库模块',
    description:
      '维护工程分部分项划分标准数据库，按单位工程-分部工程-分项工程层级组织，为巡检、验收等业务提供标准化的部位与工序选择依据，支持条目的增删改查与检索。',
  },
  {
    name: '监理月报模块',
    description:
      '按月编制项目监理月报，汇总当月工程进度、质量、安全、造价控制情况及下月监理工作计划，支持月报的新增、修改、删除、分页查询与在线查阅。',
  },
  {
    name: '项目资料模块（项目文档/文件上传）',
    description:
      '项目文档管理支持项目过程资料的分类上传、检索与下载管理；文件上传服务提供统一的附件上传能力，采用扩展名白名单校验、防路径遍历、UUID 重命名与按日期分目录存储等安全加固策略，上传成功后返回统一的文件访问地址。',
  },
  {
    name: '消息中心模块',
    description:
      '提供站内消息能力，业务审批、指派等操作自动向相关人员发送消息，用户可查看消息列表、未读数量并进行已读标记，保障业务流转信息及时触达。',
  },
  {
    name: '操作日志模块',
    description:
      '自动记录用户在系统中的关键操作（操作人、动作、模块、详情、IP、时间），提供分页查询与追溯能力，满足审计合规要求。',
  },
  {
    name: '工作标准配置模块',
    description:
      '维护监理工作标准与流程配置（工作标准配置、流程管理），支持标准条目的增删改查，用于规范监理作业流程与考核依据。',
  },
];

/** 方法名 → 中文说明启发式规则（当源码无 Javadoc 时使用） */
function describeEndpoint(methodName, httpMethod, pathStr) {
  const rules = [
    [/^list$/, '分页查询列表（支持条件筛选）'],
    [/^getById$/, '按 ID 查询详情'],
    [/^getall/i, '查询全部列表'],
    [/^create$/, '新增记录'],
    [/^update$/, '按 ID 修改记录'],
    [/^delete$/, '按 ID 删除记录'],
    [/^approve$/, '审批流转（通过/退回）'],
    [/^login$/, '用户登录，校验密码并签发 JWT 令牌'],
    [/^logout$/, '退出登录'],
    [/^info$/, '获取当前登录用户信息'],
    [/^changePassword$/, '修改当前用户密码'],
    [/^resetPassword$/, '管理员重置用户密码'],
    [/^updateStatus$/, '启用/停用状态切换'],
    [/^upload$/, '上传文件（白名单校验、UUID 重命名存储）'],
    [/^tree$/, '查询树形结构'],
    [/^stat/i, '统计汇总查询'],
    [/^count/i, '数量统计查询'],
    [/^read$/i, '标记消息已读'],
    [/^readAll$/i, '全部标记已读'],
    [/^unread/i, '查询未读数量'],
    [/^assign/i, '分配/绑定关联关系'],
    [/^download/i, '下载文件'],
  ];
  for (const [re, desc] of rules) {
    if (re.test(methodName)) return desc;
  }
  if (pathStr.includes('/status')) return '更新状态';
  if (pathStr.includes('/reset-password')) return '重置密码';
  if (pathStr.includes('/approve')) return '审批流转';
  const verb = { GET: '查询', POST: '新增/提交', PUT: '修改', DELETE: '删除' }[httpMethod] || '操作';
  return verb + '（' + methodName + '）';
}

/** 第五章：页面操作手册（进入路径 / 主要功能 / 操作步骤） */
const PAGE_MANUALS = {
  'login.html': {
    entry: '浏览器访问系统地址，自动进入登录页（login.html）',
    functions: '系统登录入口，账号密码认证并签发访问令牌',
    steps: ['打开系统地址进入登录页', '输入手机号与密码', '点击"登录"按钮，认证通过后自动跳转至系统首页（index.html）', '认证失败时页面提示错误原因，可重新输入'],
  },
  'welcome.html': {
    entry: '浏览器直接访问 welcome.html（对外演示启动页）',
    functions: '系统对外演示与介绍页面，提供进入系统的入口',
    steps: ['打开 welcome.html 浏览系统介绍', '点击进入系统按钮跳转至登录页'],
  },
  'index.html': {
    entry: '登录成功后自动进入，或点击左侧菜单"工作台"',
    functions: '工作台首页，展示项目与业务单据统计、待办事项及各模块快捷入口',
    steps: ['登录后进入工作台查看统计卡片与待办', '点击统计卡片或快捷入口跳转至对应业务模块', '通过顶部导航查看消息与个人信息，可退出登录'],
  },
  'project-ledger.html': {
    entry: '左侧菜单"项目管理 → 项目台账"',
    functions: '监理项目台账的增删改查与状态维护',
    steps: ['进入页面查看项目列表，可按名称、状态筛选', '点击"新增"填写项目信息并保存', '点击列表行"编辑"修改项目信息', '点击"删除"并确认后移除项目'],
  },
  'project-data-upload.html': {
    entry: '左侧菜单"项目管理 → 项目资料上传"',
    functions: '项目过程资料的分类上传与管理',
    steps: ['选择所属项目与资料分类', '点击上传控件选择文件（支持白名单内的常见文档、图片、压缩包格式）', '上传成功后在列表中查看、下载或删除资料'],
  },
  'personnel-management.html': {
    entry: '左侧菜单"系统管理 → 人员管理"',
    functions: '监理人员档案管理、启停用与密码重置',
    steps: ['进入页面按部门、状态、姓名筛选人员', '点击"新增"录入人员信息并保存', '点击"编辑"修改档案，点击状态开关启用/停用', '点击"重置密码"将该用户密码重置为初始密码'],
  },
  'department-management.html': {
    entry: '左侧菜单"系统管理 → 部门管理"',
    functions: '树形部门结构维护',
    steps: ['查看部门列表及层级关系', '点击"新增"填写部门名称、上级部门、负责人后保存', '点击"编辑"/"删除"维护已有部门'],
  },
  'permission-settings.html': {
    entry: '左侧菜单"系统管理 → 权限设置"',
    functions: '角色管理与角色-权限、用户-角色绑定',
    steps: ['查看角色列表，点击"新增角色"创建角色', '选中角色后在权限面板勾选权限点并保存', '为用户分配角色，完成权限授予'],
  },
  'operation-log.html': {
    entry: '左侧菜单"系统管理 → 操作日志"',
    functions: '系统操作日志的分页查询与审计追溯',
    steps: ['进入页面按操作人、模块、时间范围筛选', '翻页浏览日志明细（操作动作、详情、IP、时间）'],
  },
  'message-center.html': {
    entry: '顶部导航消息图标或左侧菜单"消息中心"',
    functions: '站内消息查看与已读管理',
    steps: ['查看消息列表与未读标识', '点击消息查看详情，自动标记已读', '可执行"全部已读"批量处理'],
  },
  'supervision-diary.html': {
    entry: '左侧菜单"监理业务 → 监理日记"',
    functions: '个人监理日记的填写、查询与审批流转',
    steps: ['进入页面按项目、编号、状态、日期筛选日记', '点击"新增"填写天气、施工情况、监理工作情况等并保存', '点击"提交"进入审批流程，审批人执行"通过"或"退回"', '点击"编辑"/"删除"维护未提交的日记'],
  },
  'diary-statistics.html': {
    entry: '左侧菜单"监理业务 → 日记统计"',
    functions: '监理日记按项目、时间区间的汇总统计',
    steps: ['选择项目与统计时间区间', '查看填写数量、审批状态分布等统计结果'],
  },
  'diary-report.html': {
    entry: '左侧菜单"监理业务 → 日记报表"',
    functions: '监理日记报表查阅与导出打印',
    steps: ['选择项目与日期范围生成报表', '在线查阅报表内容，可打印存档'],
  },
  'supervision-log.html': {
    entry: '左侧菜单"监理业务 → 监理日志"',
    functions: '项目监理日志的填写、查询与审批流转',
    steps: ['按项目、日期、状态筛选日志', '点击"新增"填写施工动态、巡视旁站、验收材料、安全文明施工等内容', '提交审批，审批人执行"通过"或"退回"'],
  },
  'log-statistics.html': {
    entry: '左侧菜单"监理业务 → 日志统计"',
    functions: '监理日志按项目、时间区间的汇总统计',
    steps: ['选择项目与统计时间区间', '查看日志填写与审批情况统计'],
  },
  'safety-log.html': {
    entry: '左侧菜单"安全管理 → 安全日志"',
    functions: '安全监理日志的填写、查询与审批流转',
    steps: ['按项目、日期筛选安全日志', '点击"新增"记录安全巡查、危大工程管控、隐患整改等内容', '提交审批并跟踪审批结果'],
  },
  'safety-log-statistics.html': {
    entry: '左侧菜单"安全管理 → 安全日志统计"',
    functions: '安全日志按项目、时间区间的汇总统计',
    steps: ['选择项目与统计时间区间', '查看安全日志数量与隐患整改情况统计'],
  },
  'daily-inspection.html': {
    entry: '左侧菜单"巡检管理 → 日常巡检"',
    functions: '日常巡检记录的增删改查',
    steps: ['按项目、日期筛选巡检记录', '点击"新增"填写检查部位、检查内容、发现问题与处理结果', '点击"编辑"/"删除"维护记录'],
  },
  'sideline-inspection.html': {
    entry: '左侧菜单"巡检管理 → 旁站巡检"',
    functions: '关键部位/关键工序旁站记录的增删改查',
    steps: ['按项目、日期筛选旁站记录', '点击"新增"填写旁站部位、起止时间、施工及监理情况', '点击"编辑"/"删除"维护记录'],
  },
  'supervision-notice.html': {
    entry: '左侧菜单"监理文书 → 监理通知单"',
    functions: '监理通知单的签发、回复跟踪与闭环管理',
    steps: ['按项目、编号、状态筛选通知单', '点击"新增"填写通知事项与整改要求后签发', '跟踪施工单位回复情况，完成闭环确认'],
  },
  'work-contact.html': {
    entry: '左侧菜单"监理文书 → 工作联系单"',
    functions: '参建各方工作联系单的登记与查询',
    steps: ['按项目、编号筛选联系单', '点击"新增"填写联系事项、主送/抄送单位后保存', '点击"编辑"/"删除"维护联系单'],
  },
  'meeting-minutes.html': {
    entry: '左侧菜单"监理文书 → 会议纪要"',
    functions: '监理例会与专题会议纪要管理',
    steps: ['按项目、会议日期筛选纪要', '点击"新增"填写会议议题、参会人员、决议事项后保存', '在线查阅纪要详情'],
  },
  'sub-item-database.html': {
    entry: '左侧菜单"基础数据 → 分部分项数据库"',
    functions: '分部分项工程划分标准数据维护',
    steps: ['按单位工程/分部工程层级浏览条目', '点击"新增"录入分部分项条目并保存', '点击"编辑"/"删除"维护条目'],
  },
  'supervision-monthly-report.html': {
    entry: '左侧菜单"监理业务 → 监理月报"',
    functions: '项目监理月报的编制与查阅',
    steps: ['按项目、月份筛选月报', '点击"新增"编制当月进度、质量、安全、造价情况及下月计划', '在线查阅、编辑或删除月报'],
  },
  'work-standard-config.html': {
    entry: '左侧菜单"系统管理 → 工作标准配置"',
    functions: '监理工作标准条目的配置维护',
    steps: ['查看现有工作标准配置列表', '点击"新增"/"编辑"维护标准条目', '删除不再适用的标准条目'],
  },
  'process-management.html': {
    entry: '左侧菜单"系统管理 → 流程管理"',
    functions: '监理业务流程配置管理',
    steps: ['查看业务流程配置列表', '维护各业务单据的流转流程设置'],
  },
};

module.exports = { CONTROLLER_NAMES, FUNCTION_MODULES, PAGE_MANUALS, describeEndpoint };
