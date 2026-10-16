/*
 * 移动端响应式适配 - 批量为 HTML 注入两行 <link>
 * 范围: 补后端/ 下 25 个文件(24 业务页 + login.html),明确跳过 welcome.html
 * 幂等: 若文件已包含 responsive.css 引用,跳过该文件不重复插入
 * 编码: 读写均按 UTF-8(原文件 charset="UTF-8")
 * 用法: node 注入响应式link.cjs
 */
const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..', '补后端');
const SKIP = new Set(['welcome.html']);
const LINKS = '    <link rel="stylesheet" href="assets/style.css">\n    <link rel="stylesheet" href="assets/responsive.css">\n';

const files = fs.readdirSync(ROOT).filter(function (f) {
    return f.endsWith('.html') && !SKIP.has(f);
});

let injected = 0, skipped = 0, missing = 0;
const report = [];

files.forEach(function (file) {
    const filePath = path.join(ROOT, file);
    let content = fs.readFileSync(filePath, 'utf8');

    // 幂等检查
    if (content.indexOf('assets/responsive.css') !== -1) {
        skipped++;
        report.push({ file: file, status: 'SKIP_ALREADY_INJECTED' });
        return;
    }

    // 在最后一个 </head> 之前插入两行 link
    // 用 lastIndexOf 防止 <head> 出现在示例代码/注释中造成错位
    const headClose = content.lastIndexOf('</head>');
    if (headClose === -1) {
        missing++;
        report.push({ file: file, status: 'ERROR_NO_HEAD_CLOSE' });
        return;
    }

    const before = content.slice(0, headClose);
    const after = content.slice(headClose);
    // 确保插入点前有换行
    const sep = before.endsWith('\n') ? '' : '\n';
    content = before + sep + LINKS + after;

    fs.writeFileSync(filePath, content, 'utf8');
    injected++;
    report.push({ file: file, status: 'OK' });
});

console.log('===== 注入完成 =====');
console.log('成功注入: ' + injected);
console.log('已存在跳过: ' + skipped);
console.log('缺失 </head> 报错: ' + missing);
console.log('总处理: ' + files.length + ' / 应处理 25');
report.forEach(function (r) { console.log('  [' + r.status + '] ' + r.file); });
