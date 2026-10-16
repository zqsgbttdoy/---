/**
 * extract-metadata.js
 * 元数据提取脚本：
 *   1. 扫描 backend 全部 Controller 源码，提取 REST 端点（路径、HTTP 方法、方法名、Javadoc 首行说明）；
 *   2. 解析 schema.sql，提取每张表的表名、中文注释、字段清单（字段名/类型/注释）；
 *   3. 解析 schema-indexes.sql，提取复合索引清单；
 *   4. 扫描 补后端 目录下的 *.html，提取页面文件名与 <title>；
 *   5. 输出 metadata.json，并在控制台打印统计信息。
 *
 * 只读扫描，不修改任何现有文件。
 * 运行：node extract-metadata.js
 */
'use strict';

const fs = require('fs');
const path = require('path');

// 目录定位：本脚本位于 补后端\软著申请材料\generator\
const GENERATOR_DIR = __dirname;
const MATERIAL_DIR = path.resolve(GENERATOR_DIR, '..');            // 补后端\软著申请材料
const BUHOUDUAN_DIR = path.resolve(MATERIAL_DIR, '..');            // 补后端
const BACKEND_DIR = path.join(BUHOUDUAN_DIR, 'backend');
const CONTROLLER_DIR = path.join(BACKEND_DIR, 'src', 'main', 'java', 'com', 'supervision', 'controller');
const SCHEMA_SQL = path.join(BACKEND_DIR, 'src', 'main', 'resources', 'schema.sql');
const SCHEMA_INDEXES_SQL = path.join(BACKEND_DIR, 'src', 'main', 'resources', 'schema-indexes.sql');
const OUTPUT_JSON = path.join(GENERATOR_DIR, 'metadata.json');

/* ============================================================
 * 一、Controller 端点提取
 * ============================================================ */

const MAPPING_HTTP = {
  GetMapping: 'GET',
  PostMapping: 'POST',
  PutMapping: 'PUT',
  DeleteMapping: 'DELETE',
  PatchMapping: 'PATCH',
};

/** 从注解参数中取出路径字符串，如 ("/xx") / (value = "/xx") / 空 */
function parseMappingPath(argText) {
  if (!argText) return '';
  const m = argText.match(/"([^"]*)"/);
  return m ? m[1] : '';
}

/** 提取 Javadoc 首行有效说明文字 */
function firstJavadocLine(javadoc) {
  if (!javadoc) return '';
  const lines = javadoc
    .split('\n')
    .map((l) => l.replace(/^\s*\/?\*+\/?/, '').trim())
    .filter((l) => l && !l.startsWith('@') && !l.startsWith('<'));
  return lines.length ? lines[0].replace(/<[^>]+>/g, '').trim() : '';
}

function parseController(filePath) {
  const source = fs.readFileSync(filePath, 'utf8');
  const className = path.basename(filePath, '.java');

  // 类级 @RequestMapping
  const classMappingMatch = source.match(/@RequestMapping\s*\(([^)]*)\)/);
  const basePath = classMappingMatch ? parseMappingPath(classMappingMatch[1]) : '';

  // 类级 Javadoc（类声明前最近的 /** ... */）
  let classDoc = '';
  const classDeclIdx = source.search(/public\s+class\s+\w+/);
  if (classDeclIdx > 0) {
    const before = source.slice(0, classDeclIdx);
    const docMatch = before.match(/\/\*\*([\s\S]*?)\*\/(?![\s\S]*\/\*\*)/);
    if (docMatch) classDoc = firstJavadocLine(docMatch[1]);
  }

  // 方法级映射注解：@GetMapping / @GetMapping("/x") / @GetMapping(value = "/x")
  const endpoints = [];
  const methodAnnoRe = /@(GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping)\s*(\(([^)]*)\))?/g;
  let m;
  while ((m = methodAnnoRe.exec(source)) !== null) {
    const httpMethod = MAPPING_HTTP[m[1]];
    const subPath = parseMappingPath(m[3]);
    const afterAnno = source.slice(m.index + m[0].length);

    // 注解后的方法签名（跳过其他注解行）
    const sigMatch = afterAnno.match(/public\s+[\w<>,.?\[\]\s]+?\s+(\w+)\s*\(/);
    const methodName = sigMatch ? sigMatch[1] : '';

    // 注解前最近的 Javadoc（中间不允许出现 } 或 ; ，保证是紧邻本方法的）
    let summary = '';
    const before = source.slice(0, m.index);
    const docRe = /\/\*\*([\s\S]*?)\*\//g;
    let dm;
    let lastDoc = null;
    let lastDocEnd = -1;
    while ((dm = docRe.exec(before)) !== null) {
      lastDoc = dm[1];
      lastDocEnd = dm.index + dm[0].length;
    }
    if (lastDoc !== null) {
      const between = before.slice(lastDocEnd);
      // 之间只允许出现注解与空白，说明该 Javadoc 属于当前方法
      if (/^[\s]*(@[\w]+(\s*\([^)]*\))?[\s]*)*$/.test(between)) {
        summary = firstJavadocLine(lastDoc);
      }
    }

    const fullPath = ('/api' + basePath + (subPath.startsWith('/') || subPath === '' ? subPath : '/' + subPath))
      .replace(/\/+/g, '/');
    endpoints.push({ httpMethod, path: fullPath, methodName, summary });
  }

  return { className, basePath, classDoc, endpoints };
}

function extractControllers() {
  const files = fs
    .readdirSync(CONTROLLER_DIR)
    .filter((f) => f.endsWith('Controller.java'))
    .sort();
  return files.map((f) => parseController(path.join(CONTROLLER_DIR, f)));
}

/* ============================================================
 * 二、schema.sql 表结构提取
 * ============================================================ */

function extractTables() {
  const sql = fs.readFileSync(SCHEMA_SQL, 'utf8');
  const tables = [];
  // 匹配 CREATE TABLE ... ( body ) ENGINE=... COMMENT='xxx';
  const tableRe = /CREATE TABLE IF NOT EXISTS\s+(\w+)\s*\(([\s\S]*?)\)\s*ENGINE=\w+[^;]*?COMMENT='([^']*)'\s*;/g;
  let m;
  while ((m = tableRe.exec(sql)) !== null) {
    const [, tableName, body, tableComment] = m;
    const columns = [];
    const indexes = [];
    for (const rawLine of body.split('\n')) {
      const line = rawLine.trim().replace(/,\s*$/, '');
      if (!line || line.startsWith('--')) continue;
      if (/^PRIMARY KEY/i.test(line)) continue;
      const idxMatch = line.match(/^(UNIQUE\s+)?INDEX\s+(\w+)\s*\(([^)]*)\)/i);
      if (idxMatch) {
        indexes.push({
          name: idxMatch[2],
          unique: !!idxMatch[1],
          columns: idxMatch[3].split(',').map((c) => c.trim()),
        });
        continue;
      }
      // 字段行：name TYPE(...) [约束...] COMMENT 'xxx'
      const colMatch = line.match(/^(\w+)\s+([A-Za-z]+(?:\([^)]*\))?)\s*(.*?)(?:COMMENT\s+'([^']*)')?$/);
      if (colMatch) {
        const [, colName, colType, rest, colComment] = colMatch;
        const constraints = [];
        if (/NOT NULL/i.test(rest)) constraints.push('NOT NULL');
        if (/AUTO_INCREMENT/i.test(rest)) constraints.push('AUTO_INCREMENT');
        const defMatch = rest.match(/DEFAULT\s+(CURRENT_TIMESTAMP|'[^']*'|\S+)/i);
        if (defMatch) constraints.push('DEFAULT ' + defMatch[1]);
        columns.push({
          name: colName,
          type: colType.toUpperCase(),
          constraints: constraints.join(' '),
          comment: colComment || '',
        });
      }
    }
    tables.push({ tableName, tableComment, columns, indexes });
  }
  return tables;
}

/* ============================================================
 * 三、schema-indexes.sql 复合索引提取
 * ============================================================ */

function extractCompositeIndexes() {
  const sql = fs.readFileSync(SCHEMA_INDEXES_SQL, 'utf8');
  const result = [];
  const lines = sql.split('\n');
  let lastComment = '';
  for (const raw of lines) {
    const line = raw.trim();
    if (line.startsWith('--')) {
      const text = line.replace(/^--\s*/, '');
      // 跳过分隔线与文件头说明块
      if (text && !/^=+$/.test(text) && !/^-+$/.test(text) && !/^ALTER TABLE/.test(text)) {
        lastComment = text;
      }
      continue;
    }
    const m = line.match(/^ALTER TABLE\s+(\w+)\s+ADD INDEX\s+(\w+)\s*\(([^)]*)\)/i);
    if (m) {
      result.push({
        tableName: m[1],
        indexName: m[2],
        columns: m[3].split(',').map((c) => c.trim()),
        purpose: lastComment,
      });
      lastComment = '';
    }
  }
  return result;
}

/* ============================================================
 * 四、前端页面提取
 * ============================================================ */

function extractPages() {
  const files = fs
    .readdirSync(BUHOUDUAN_DIR)
    .filter((f) => f.toLowerCase().endsWith('.html'))
    .sort();
  return files.map((f) => {
    const html = fs.readFileSync(path.join(BUHOUDUAN_DIR, f), 'utf8');
    const titleMatch = html.match(/<title>([\s\S]*?)<\/title>/i);
    return {
      fileName: f,
      title: titleMatch ? titleMatch[1].trim().replace(/\s+/g, ' ') : '',
    };
  });
}

/* ============================================================
 * 主流程
 * ============================================================ */

function main() {
  const controllers = extractControllers();
  const tables = extractTables();
  const compositeIndexes = extractCompositeIndexes();
  const pages = extractPages();

  const endpointCount = controllers.reduce((sum, c) => sum + c.endpoints.length, 0);

  const metadata = {
    generatedAt: new Date().toISOString(),
    system: {
      name: '监理现场管理系统',
      version: '1.0.0',
      backendPackage: 'com.supervision',
    },
    stats: {
      controllerCount: controllers.length,
      endpointCount,
      tableCount: tables.length,
      compositeIndexCount: compositeIndexes.length,
      pageCount: pages.length,
    },
    controllers,
    tables,
    compositeIndexes,
    pages,
  };

  fs.writeFileSync(OUTPUT_JSON, JSON.stringify(metadata, null, 2), 'utf8');

  console.log('========== 元数据提取完成 ==========');
  console.log('Controller 数量 : ' + controllers.length);
  console.log('REST 端点数量   : ' + endpointCount);
  console.log('数据库表数量    : ' + tables.length);
  console.log('复合索引数量    : ' + compositeIndexes.length);
  console.log('前端页面数量    : ' + pages.length);
  console.log('输出文件        : ' + OUTPUT_JSON);
}

main();
