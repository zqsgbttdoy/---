---
kind: frontend_style
name: 纯静态 HTML + 内联 CSS 风格（无前端框架）
category: frontend_style
scope:
    - '**'
source_files:
    - 补后端/assets/app.js
    - 补后端/index.html
---

本仓库为 Spring Boot REST API 后端，前端仅以根目录下的若干 `.html` 文件作为演示页面，未使用任何现代前端样式系统。整体风格特征如下：

1. **样式组织方式**
   - 每个页面在 `<head>` 中通过 `<style>` 标签内联编写 CSS，不存在独立的 `.css`/`.scss`/`.less` 文件或外部样式库引用。
   - 全局通用逻辑集中在 `assets/app.js`，提供登录态、统一 fetch、Toast、分页等能力，但样式仍由各页面自行定义。

2. **视觉与主题约定**
   - 字体：`"Microsoft YaHei", "PingFang SC", sans-serif`。
   - 主色：`#2563EB`（蓝），侧边栏渐变 `#1E3A5F → #0F2440`，背景 `#F0F5FF`，正文 `#1F2937`。
   - 语义色：成功 `#10B981`、警告 `#F59E0B`、危险 `#EF4444`、中性文本 `#6B7280`/`#9CA3AF`。
   - 布局：固定左侧 240px 深色侧边栏 + 右侧弹性主体区；顶部 sticky header；卡片圆角 8–12px，阴影轻量。

3. **组件与交互模式**
   - 按钮类名：`.btn-primary` / `.btn-success` / `.btn-danger` / `.btn-outline`，颜色与上述语义色一致。
   - 状态标签：`.status-draft` / `.status-recorded` / `.status-fixed` 等，用浅色背景+深色文字区分。
   - 弹窗/面板：通过 `.show` 类切换 `display: flex/block`，配合 `@keyframes npFadeIn` / `modalIn` 淡入动画。
   - Toast：由 `assets/app.js` 的 `toast()` 动态创建 `.app-toast` 元素，支持 info/success/warning/error 四种类型。

4. **响应式策略**
   - 仅在关键位置使用 `@media (max-width: 768px)` 做简单适配（如通知面板宽度、用户面板定位、设置弹窗布局），未采用栅格或断点体系。

5. **开发约束与建议**
   - 新增页面应遵循现有 `<style>` 内联 + `assets/app.js` 基座的模式，复用统一的 color/token 与布局结构。
   - 如需引入第三方 UI 库（Ant Design Vue、Element Plus、Tailwind 等），应在仓库顶层建立独立的前端工程，而非继续在内联 CSS 上扩展。

由于该仓库本质是后端项目，前端仅为静态演示页，不存在设计令牌、CSS-in-JS、组件库或构建工具链，因此 `frontend_style` 在此处体现为“无系统的内联 CSS 风格”。