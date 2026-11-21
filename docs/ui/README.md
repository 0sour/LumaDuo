# UI 文档

- [DESIGN-SYSTEM.md](DESIGN-SYSTEM.md) — 颜色、字体、间距、组件、动效、验收
- [SCREEN-SPEC.md](SCREEN-SPEC.md) — 主机/应用/远程/设置/串流/配对/单屏
- [TOP-SCREEN.md](TOP-SCREEN.md) — 上屏视频与只读状态
- [DUO-PITFALLS.md](DUO-PITFALLS.md) — bili-duo / UUYC-Duo 双屏与手柄踩坑复盘
- [PROTOTYPE.md](PROTOTYPE.md) — HTML 原型使用说明
- [prototype/index.html](prototype/index.html) — 可点击原型
- [prototype/README.md](prototype/README.md) — 原型页面、键位与 URL 参数
- [../INPUT-AND-FOCUS.md](../INPUT-AND-FOCUS.md) — 输入状态机与焦点契约（工程侧）
- [../STREAM-CONSOLE.md](../STREAM-CONSOLE.md) — 串流控制台宿主、状态桥与单屏回退（工程侧）

视觉基线：

- 深色表面 + Lumen Gold `#F2C14E`
- 白色焦点环，不用金色光晕
- 640×480dp 为主，538×469dp 窄版
- 控件只在下屏
- 串流控制台 5 tabs（状态/画面/音频/输入/连接），状态置首并默认
- 状态页明细为 2×2 信息矩阵；无 focusable 内容
- 品牌标直接缩放 UUYC A1 全金矢量，不重绘小尺寸版本
- 同系列识别来自几何/排版/焦点/布局，不来自粉色
