/**
 * generate-docx.js
 * Word 文档生成脚本：
 *   1. 若 ../软著申请材料.md 不存在（或指定 --rebuild-md），则依据 metadata.json
 *      与 content-config.js 生成 Markdown 源稿；
 *   2. 解析 Markdown 源稿（标题/段落/表格/列表），生成正式 Word 文档
 *      ../监理现场管理系统-软件著作权申请文档.docx，
 *      含封面、五大章节、标题层级样式、表格与页码。
 *
 * 运行：node generate-docx.js [--rebuild-md]
 * 注意：--rebuild-md 会覆盖人工对 Markdown 源稿的编辑。
 */
'use strict';

const fs = require('fs');
const path = require('path');
const {
  Document, Packer, Paragraph, TextRun, HeadingLevel, Table, TableRow, TableCell,
  WidthType, AlignmentType, Footer, PageNumber, PageBreak, BorderStyle, ShadingType,
  VerticalAlign,
} = require('docx');
const JSZip = require('jszip'); // docx 的传递依赖，用于 STORE 方式重打包
const { CONTROLLER_NAMES, FUNCTION_MODULES, PAGE_MANUALS, describeEndpoint } = require('./content-config');

const GENERATOR_DIR = __dirname;
const MATERIAL_DIR = path.resolve(GENERATOR_DIR, '..');
const METADATA_JSON = path.join(GENERATOR_DIR, 'metadata.json');
const MD_FILE = path.join(MATERIAL_DIR, '软著申请材料.md');
const DOCX_FILE = path.join(MATERIAL_DIR, '监理现场管理系统-软件著作权申请文档.docx');

const SYSTEM_NAME = '监理现场管理系统';
const VERSION = 'V1.0.0';
const HOLDER = '【请填写公司名称】';

/* ============================================================
 * 第一部分：由 metadata.json 生成 Markdown 源稿
 * ============================================================ */

function esc(text) {
  return String(text == null ? '' : text).replace(/\|/g, '\\|').replace(/\r?\n/g, ' ');
}

function buildMarkdown(meta) {
  const L = [];
  const push = (...lines) => L.push(...lines);

  push(
    '<!--',
    '  监理现场管理系统 —— 软件著作权申请文档 Markdown 源稿',
    '  ================================================================',
    '  本文件是 Word 文档（监理现场管理系统-软件著作权申请文档.docx）的源稿，',
    '  可直接人工编辑本文件中的任何文字与表格内容。',
    '',
    '  编辑后重新生成 Word 文档：',
    '      cd generator',
    '      node generate-docx.js',
    '  （generate-docx.js 检测到本文件已存在时，会以本文件为准生成 docx，',
    '    不会覆盖你的人工修改。）',
    '',
    '  如需从源码重新提取元数据并重建本文件（会覆盖人工编辑！）：',
    '      cd generator',
    '      node extract-metadata.js',
    '      node generate-docx.js --rebuild-md',
    '-->',
    '',
    '# ' + SYSTEM_NAME + '软件著作权申请文档',
    '',
    '- 软件名称：' + SYSTEM_NAME,
    '- 版 本 号：' + VERSION,
    '- 著作权人：' + HOLDER,
    '- 开发完成日期：【请填写】',
    '- 首次发表日期：【请填写】',
    ''
  );

  /* ---------- 一、系统功能说明 ---------- */
  push(
    '# 一、系统功能说明',
    '',
    SYSTEM_NAME + '是一套面向建设工程监理企业的现场监理业务管理软件，覆盖监理项目从进场到竣工全过程的日常业务活动。' +
      '系统以项目台账为中心，围绕监理日记、监理日志、安全日志、日常巡检、旁站巡检、监理通知单、工作联系单、会议纪要、监理月报等核心监理文书，' +
      '提供在线填报、审批流转、统计分析与资料归档能力；同时内置基于 RBAC 模型的用户、角色、权限、部门管理体系，' +
      '以及消息中心、操作日志等支撑功能。系统后端共 ' + meta.stats.controllerCount + ' 个业务控制器、' +
      meta.stats.endpointCount + ' 个 REST 接口，数据库 ' + meta.stats.tableCount + ' 张数据表，前端 ' +
      meta.stats.pageCount + ' 个功能页面。',
    ''
  );
  FUNCTION_MODULES.forEach((mod, i) => {
    push('## 1.' + (i + 1) + ' ' + mod.name, '', mod.description, '');
  });

  /* ---------- 二、技术架构描述 ---------- */
  push(
    '# 二、技术架构描述',
    '',
    '## 2.1 总体分层架构',
    '',
    '系统采用前后端分离的 B/S 架构。后端基于 Spring Boot 构建 REST API 服务，代码包根为 com.supervision，采用经典四层架构：',
    '',
    '- Controller 层（controller 包）：接收 HTTP 请求、参数校验、调用业务服务并返回统一响应，共 ' + meta.stats.controllerCount + ' 个控制器；',
    '- Service 层（service 包）：承载业务逻辑，包括审批流转、编号生成、统计汇总、消息触发等；',
    '- Repository 层（repository 包）：基于 Spring Data JPA 的数据访问层，负责与 MySQL 数据库交互；',
    '- Entity 层（entity 包）：JPA 实体，与数据库表一一映射。',
    '',
    '前端为 ' + meta.stats.pageCount + ' 个纯静态 HTML 页面（无前端框架，内联 CSS），通过公共脚本 assets/app.js 以 fetch 方式调用后端 /api 接口，完成数据渲染与交互。',
    '',
    '## 2.2 技术栈清单',
    '',
    '| 类别 | 技术组件 | 版本/说明 |',
    '| --- | --- | --- |',
    '| 后端框架 | Spring Boot | 3.2.5（JDK 17） |',
    '| 持久层 | Spring Data JPA（Hibernate） | 对象关系映射与数据访问 |',
    '| 数据库 | MySQL | 8.0，utf8mb4 字符集，数据库名 supervision_db |',
    '| 连接池 | Alibaba Druid | 数据库连接池与监控 |',
    '| 认证 | JWT（jjwt） | 0.12.5，HS256 签名算法 |',
    '| 密码加密 | spring-security-crypto BCrypt | 密码单向摘要存储 |',
    '| JSON 序列化 | fastjson2 | 请求/响应 JSON 处理 |',
    '| 开发辅助 | Lombok | 编译期生成样板代码 |',
    '| 构建工具 | Maven | 3.9.6，产物为可执行 JAR |',
    '| 前端 | 原生 HTML + CSS + JavaScript | ' + meta.stats.pageCount + ' 个静态页面，无框架依赖 |',
    '| Web 服务器 | Nginx | 静态资源托管与 /api 反向代理 |',
    '',
    '## 2.3 认证与安全机制',
    '',
    '系统采用 JWT（JSON Web Token）无状态认证方案：用户登录时，服务端使用 BCrypt 算法对提交口令与数据库中存储的密码摘要进行比对，' +
      '校验通过后使用 HS256 算法签发带有效期的 JWT 访问令牌；前端将令牌保存在浏览器本地，后续每次请求通过 Authorization: Bearer <token> 请求头携带。' +
      '服务端拦截器统一校验令牌合法性与有效期，未通过认证的请求返回 HTTP 401，前端捕获后自动跳转登录页。',
    '',
    '其他安全设计：用户口令仅以 BCrypt 摘要形式存储，全程不落明文；文件上传采用扩展名白名单、防路径遍历校验与 UUID 重命名存储；' +
      '关键操作写入操作日志（操作人、动作、模块、IP、时间）供审计追溯；接口权限基于 RBAC 角色权限模型控制。',
    '',
    '## 2.4 部署形态',
    '',
    '生产环境采用 Nginx + Spring Boot + MySQL 的经典部署形态：',
    '',
    '- Nginx：托管全部静态 HTML 页面（根路径 /），并将 /api/ 前缀请求反向代理至后端 8080 端口；',
    '- Spring Boot 应用：以可执行 JAR（supervision-site-management-1.0.0.jar）方式运行，由 systemd 管理进程生命周期；',
    '- MySQL 8.0：独立数据库服务，通过 Druid 连接池访问；数据库结构由 schema.sql 初始化、schema-indexes.sql 补充复合索引；',
    '- 配置外部化：数据库连接等配置支持通过 SPRING_DATASOURCE_* 等环境变量覆盖，无需修改程序包即可适配不同环境。',
    ''
  );

  /* ---------- 三、数据库设计文档 ---------- */
  push(
    '# 三、数据库设计文档',
    '',
    '## 3.1 设计概述',
    '',
    '数据库名为 supervision_db，字符集 utf8mb4、排序规则 utf8mb4_unicode_ci，存储引擎 InnoDB。共 ' +
      meta.stats.tableCount + ' 张数据表，按前缀划分为两类：sys_ 前缀为系统支撑表（组织、权限、日志、消息等），biz_ 前缀为监理业务表（日记、日志、巡检、文书等）。' +
      '各表统一包含自增主键 id 与 create_time / update_time 审计字段；在单列索引基础上另行维护 ' +
      meta.stats.compositeIndexCount + ' 条复合索引（schema-indexes.sql）以优化高频列表查询。',
    '',
    '## 3.2 数据表明细',
    ''
  );
  meta.tables.forEach((t, i) => {
    push('### 3.2.' + (i + 1) + ' ' + t.tableName + '（' + t.tableComment + '）', '');
    push('| 字段名 | 类型 | 约束 | 说明 |', '| --- | --- | --- | --- |');
    t.columns.forEach((c) => {
      push('| ' + esc(c.name) + ' | ' + esc(c.type) + ' | ' + esc(c.constraints || '—') + ' | ' + esc(c.comment) + ' |');
    });
    push('');
    if (t.indexes.length) {
      push('本表索引：' + t.indexes.map((ix) => (ix.unique ? '唯一索引 ' : '索引 ') + ix.name + '（' + ix.columns.join(', ') + '）').join('；') + '。', '');
    }
  });
  push(
    '## 3.3 复合索引设计',
    '',
    '为优化列表页高频查询性能，在 schema-indexes.sql 中独立维护以下 ' + meta.stats.compositeIndexCount + ' 条复合索引：',
    '',
    '| 数据表 | 索引名 | 索引列 | 设计目的 |',
    '| --- | --- | --- | --- |'
  );
  meta.compositeIndexes.forEach((ix) => {
    push('| ' + esc(ix.tableName) + ' | ' + esc(ix.indexName) + ' | ' + esc(ix.columns.join(', ')) + ' | ' + esc(ix.purpose) + ' |');
  });
  push('');

  /* ---------- 四、接口文档 ---------- */
  push(
    '# 四、接口文档',
    '',
    '## 4.1 统一接口契约',
    '',
    '全部接口以 /api 为统一前缀，请求与响应均为 JSON 格式。除登录接口外，所有接口须在请求头携带 Authorization: Bearer <JWT令牌>；' +
      '令牌缺失、非法或过期时返回 HTTP 401，前端统一拦截并跳转登录页。',
    '',
    '统一响应结构 ApiResponse：',
    '',
    '| 字段 | 类型 | 说明 |',
    '| --- | --- | --- |',
    '| code | int | 业务状态码，200 表示成功，其余为业务错误码 |',
    '| message | string | 提示信息 |',
    '| data | object | 业务数据载荷，失败时可为空 |',
    '',
    '分页查询统一返回 PageResult（置于 ApiResponse.data 中）：',
    '',
    '| 字段 | 类型 | 说明 |',
    '| --- | --- | --- |',
    '| list | array | 当前页数据列表 |',
    '| total | long | 满足条件的记录总数 |',
    '| pageNum | int | 当前页码（从 1 开始） |',
    '| pageSize | int | 每页条数 |',
    '',
    '## 4.2 接口清单',
    '',
    '系统共 ' + meta.stats.controllerCount + ' 个控制器、' + meta.stats.endpointCount + ' 个 REST 端点，按模块列示如下。',
    ''
  );
  meta.controllers.forEach((c, i) => {
    const cname = CONTROLLER_NAMES[c.className] || c.className;
    push('### 4.2.' + (i + 1) + ' ' + cname + '接口（' + c.className + '，基础路径 /api' + c.basePath + '）', '');
    push('| HTTP 方法 | 接口路径 | 处理方法 | 功能说明 |', '| --- | --- | --- | --- |');
    c.endpoints.forEach((e) => {
      const summary = e.summary || describeEndpoint(e.methodName, e.httpMethod, e.path);
      push('| ' + e.httpMethod + ' | ' + esc(e.path) + ' | ' + esc(e.methodName) + ' | ' + esc(summary) + ' |');
    });
    push('');
  });

  /* ---------- 五、用户操作手册 ---------- */
  push(
    '# 五、用户操作手册',
    '',
    '## 5.1 登录入口说明',
    '',
    '在浏览器地址栏输入系统访问地址（如 http://服务器地址/），系统自动进入登录页。输入管理员分配的手机号与密码，点击"登录"即可进入系统工作台。' +
      '首次登录后建议通过"修改密码"功能更换初始密码。登录状态失效（令牌过期）时，任何操作都会自动跳回登录页，重新登录即可。',
    '',
    '系统共 ' + meta.stats.pageCount + ' 个功能页面，以下按页面分节说明操作方法。',
    ''
  );
  meta.pages.forEach((p, i) => {
    const manual = PAGE_MANUALS[p.fileName] || {
      entry: '通过左侧菜单进入',
      functions: p.title || p.fileName,
      steps: ['进入页面查看数据列表', '按页面提供的按钮进行相应操作'],
    };
    const shortTitle = (p.title || p.fileName).replace(/^监理现场管理系统\s*-\s*/, '');
    push('### 5.2.' + (i + 1) + ' ' + shortTitle + '（' + p.fileName + '）', '');
    push('- 进入路径：' + manual.entry);
    push('- 主要功能：' + manual.functions);
    push('- 操作步骤：');
    manual.steps.forEach((s, si) => push((si + 1) + '. ' + s));
    push('');
  });

  return L.join('\n') + '\n';
}

/* ============================================================
 * 第二部分：Markdown 解析
 * ============================================================ */

function parseMarkdown(md) {
  // 去掉 HTML 注释块
  md = md.replace(/<!--[\s\S]*?-->/g, '');
  const lines = md.split(/\r?\n/);
  const blocks = [];
  let i = 0;
  while (i < lines.length) {
    const line = lines[i];
    const trimmed = line.trim();
    if (!trimmed) { i++; continue; }

    const heading = trimmed.match(/^(#{1,4})\s+(.*)$/);
    if (heading) {
      blocks.push({ type: 'heading', level: heading[1].length, text: heading[2].trim() });
      i++;
      continue;
    }
    if (trimmed.startsWith('|')) {
      const rows = [];
      while (i < lines.length && lines[i].trim().startsWith('|')) {
        const cells = lines[i].trim().replace(/^\|/, '').replace(/\|$/, '')
          .split(/(?<!\\)\|/).map((c) => c.trim().replace(/\\\|/g, '|'));
        if (!cells.every((c) => /^:?-{3,}:?$/.test(c))) rows.push(cells);
        i++;
      }
      blocks.push({ type: 'table', rows });
      continue;
    }
    if (/^-\s+/.test(trimmed)) {
      blocks.push({ type: 'bullet', text: trimmed.replace(/^-\s+/, '') });
      i++;
      continue;
    }
    const ordered = trimmed.match(/^(\d+)\.\s+(.*)$/);
    if (ordered) {
      blocks.push({ type: 'ordered', num: ordered[1], text: ordered[2] });
      i++;
      continue;
    }
    blocks.push({ type: 'paragraph', text: trimmed });
    i++;
  }
  return blocks;
}

/* ============================================================
 * 第三部分：生成 Word 文档
 * ============================================================ */

const FONT_CN = '宋体';
const FONT_HEADING = '黑体';

/** 行内 **加粗** 解析 */
function inlineRuns(text, opts = {}) {
  const runs = [];
  const parts = String(text).split(/(\*\*[^*]+\*\*)/g);
  for (const part of parts) {
    if (!part) continue;
    const bold = /^\*\*[^*]+\*\*$/.test(part);
    runs.push(new TextRun({
      text: bold ? part.slice(2, -2) : part,
      bold: bold || opts.bold || false,
      font: opts.font || FONT_CN,
      size: opts.size || 21, // 10.5pt 五号
      color: opts.color,
    }));
  }
  return runs;
}

function headingParagraph(level, text) {
  const conf = {
    1: { size: 32, before: 400, after: 240, heading: HeadingLevel.HEADING_1, pageBreak: true },
    2: { size: 26, before: 280, after: 160, heading: HeadingLevel.HEADING_2 },
    3: { size: 24, before: 200, after: 120, heading: HeadingLevel.HEADING_3 },
    4: { size: 22, before: 160, after: 100, heading: HeadingLevel.HEADING_4 },
  }[level] || {};
  const children = [];
  if (conf.pageBreak) children.push(new PageBreak());
  children.push(new TextRun({ text, bold: true, font: FONT_HEADING, size: conf.size }));
  return new Paragraph({
    heading: conf.heading,
    spacing: { before: conf.before, after: conf.after },
    children,
  });
}

function bodyParagraph(text) {
  return new Paragraph({
    spacing: { after: 120, line: 320 },
    children: inlineRuns(text),
  });
}

function listParagraph(text, marker) {
  return new Paragraph({
    spacing: { after: 60, line: 300 },
    indent: { left: 480, hanging: 240 },
    children: inlineRuns(marker + ' ' + text),
  });
}

const CELL_BORDER = { style: BorderStyle.SINGLE, size: 4, color: '999999' };
const CELL_BORDERS = { top: CELL_BORDER, bottom: CELL_BORDER, left: CELL_BORDER, right: CELL_BORDER };

function buildTable(rows) {
  if (!rows.length) return null;
  const colCount = Math.max(...rows.map((r) => r.length));
  const tableRows = rows.map((cells, rowIdx) => {
    const isHeader = rowIdx === 0;
    const paddedCells = [];
    for (let c = 0; c < colCount; c++) {
      paddedCells.push(new TableCell({
        borders: CELL_BORDERS,
        verticalAlign: VerticalAlign.CENTER,
        shading: isHeader ? { type: ShadingType.CLEAR, fill: 'D9E2F3' } : undefined,
        margins: { top: 60, bottom: 60, left: 100, right: 100 },
        children: [new Paragraph({
          alignment: isHeader ? AlignmentType.CENTER : AlignmentType.LEFT,
          children: inlineRuns(cells[c] || '', { bold: isHeader, size: 18 }),
        })],
      }));
    }
    return new TableRow({ children: paddedCells, tableHeader: isHeader });
  });
  return new Table({
    width: { size: 100, type: WidthType.PERCENTAGE },
    rows: tableRows,
  });
}

function buildCover() {
  const centered = (text, size, opts = {}) => new Paragraph({
    alignment: AlignmentType.CENTER,
    spacing: { before: opts.before || 0, after: opts.after || 0 },
    children: [new TextRun({ text, bold: opts.bold !== false, font: opts.font || FONT_HEADING, size })],
  });
  return [
    centered(' ', 32, { before: 2400 }),
    centered(SYSTEM_NAME, 72, { after: 200 }),
    centered('软件著作权申请文档', 48, { after: 1400 }),
    centered('软件名称：' + SYSTEM_NAME, 28, { font: FONT_CN, after: 200 }),
    centered('版 本 号：' + VERSION, 28, { font: FONT_CN, after: 200 }),
    centered('著作权人：' + HOLDER, 28, { font: FONT_CN, after: 200 }),
    centered('文档类别：系统功能说明 · 技术架构 · 数据库设计 · 接口文档 · 用户操作手册', 21, { font: FONT_CN, bold: false, after: 2000 }),
  ];
}

function blocksToDocxChildren(blocks) {
  const children = [];
  let skippedTitle = false;
  let skipCoverMeta = false;
  for (const b of blocks) {
    // 跳过 Markdown 源稿自身的大标题与封面元信息（docx 用专门封面呈现）
    if (b.type === 'heading' && b.level === 1 && !skippedTitle && /软件著作权申请文档$/.test(b.text)) {
      skippedTitle = true;
      skipCoverMeta = true;
      continue;
    }
    if (skipCoverMeta) {
      if (b.type === 'bullet' && /^(软件名称|版 ?本 ?号|著作权人|开发完成日期|首次发表日期)/.test(b.text)) continue;
      skipCoverMeta = false;
    }
    switch (b.type) {
      case 'heading':
        children.push(headingParagraph(b.level, b.text));
        break;
      case 'table': {
        const table = buildTable(b.rows);
        if (table) {
          children.push(table);
          children.push(new Paragraph({ spacing: { after: 120 }, children: [] }));
        }
        break;
      }
      case 'bullet':
        children.push(listParagraph(b.text, '•'));
        break;
      case 'ordered':
        children.push(listParagraph(b.text, b.num + '.'));
        break;
      default:
        children.push(bodyParagraph(b.text));
    }
  }
  return children;
}

async function generateDocx(mdText) {
  const blocks = parseMarkdown(mdText);
  const bodyChildren = blocksToDocxChildren(blocks);

  const doc = new Document({
    creator: HOLDER,
    title: SYSTEM_NAME + '软件著作权申请文档',
    description: SYSTEM_NAME + ' ' + VERSION + ' 软件著作权申请材料',
    styles: {
      default: {
        document: { run: { font: FONT_CN, size: 21 } },
      },
    },
    sections: [
      {
        properties: {},
        children: buildCover(),
      },
      {
        properties: {},
        footers: {
          default: new Footer({
            children: [new Paragraph({
              alignment: AlignmentType.CENTER,
              children: [
                new TextRun({ text: '第 ', font: FONT_CN, size: 18 }),
                new TextRun({ children: [PageNumber.CURRENT], font: FONT_CN, size: 18 }),
                new TextRun({ text: ' 页 / 共 ', font: FONT_CN, size: 18 }),
                new TextRun({ children: [PageNumber.TOTAL_PAGES], font: FONT_CN, size: 18 }),
                new TextRun({ text: ' 页', font: FONT_CN, size: 18 }),
              ],
            })],
          }),
        },
        children: bodyChildren,
      },
    ],
  });

  const buffer = await Packer.toBuffer(doc);
  // 以 STORE（不压缩）方式重打包：仍为标准 docx（zip）格式，Word 正常打开，
  // 文件体积如实反映文档内容量（申请材料归档要求）。
  const zip = await JSZip.loadAsync(buffer);
  const stored = await zip.generateAsync({ type: 'nodebuffer', compression: 'STORE' });
  fs.writeFileSync(DOCX_FILE, stored);
  return stored.length;
}

/* ============================================================
 * 主流程
 * ============================================================ */

async function main() {
  const rebuildMd = process.argv.includes('--rebuild-md');

  if (!fs.existsSync(METADATA_JSON)) {
    console.error('未找到 metadata.json，请先执行: node extract-metadata.js');
    process.exit(1);
  }
  const meta = JSON.parse(fs.readFileSync(METADATA_JSON, 'utf8'));

  if (!fs.existsSync(MD_FILE) || rebuildMd) {
    const md = buildMarkdown(meta);
    fs.writeFileSync(MD_FILE, md, 'utf8');
    console.log((rebuildMd ? '[重建] ' : '[新建] ') + 'Markdown 源稿: ' + MD_FILE);
  } else {
    console.log('[沿用] 已存在的 Markdown 源稿（人工编辑内容将被保留）: ' + MD_FILE);
  }

  const mdText = fs.readFileSync(MD_FILE, 'utf8');
  const size = await generateDocx(mdText);
  console.log('[生成] Word 文档: ' + DOCX_FILE);
  console.log('[大小] ' + size + ' 字节（' + (size / 1024).toFixed(1) + ' KB）');
}

main().catch((err) => {
  console.error('生成失败:', err);
  process.exit(1);
});
