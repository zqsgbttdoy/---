# 软著申请材料目录说明

- `监理现场管理系统-软件著作权申请文档.docx` — 正式 Word 申请文档（封面 + 五大章节 + 页码）。
- `软著申请材料.md` — 与 docx 对应的 Markdown 源稿，可人工编辑后重新生成 docx（说明见该文件头部注释）。
- `generator/` — 生成器小工程（extract-metadata.js 提取源码/建表/页面元数据，generate-docx.js 生成 Word）。

重新生成命令：

```powershell
cd generator; npm install; node extract-metadata.js; node generate-docx.js
```
