# LumaDuo HTML Prototype

可点击的设计稿，用来确认布局、焦点顺序和串流控制台行为，不是生产代码。

## 打开

直接双击 [index.html](index.html)，或用浏览器打开：

```text
docs/ui/prototype/index.html
```

- 推荐 1440×900 以上窗口。
- 无构建、无依赖、无网络请求。
- 深色主题；1dp = 1px；下屏严格 640×480。

## 页面

| 页面 | 内容 |
|---|---|
| 主机 | 主机列表、详情、连接/配对/应用/网络测试/详情/更多/删除 |
| 应用 | 应用网格、选中详情、启动串流、应用操作 Sheet |
| 远程 | EasyTier 状态、启动/停止、配置、PC 配置、二维码、手动添加 |
| 设置 | 画面/音频/输入/网络/主机/界面/关于 7 类 |
| 串流 | 控制台/工具双态：5 个控制台 tab（状态默认）+ 触摸板/键盘/快捷键动作条 |
| 配对 | 4 位 PIN + 配对二维码占位 |
| 单屏 | 视频全屏 + 42% 控制台抽屉（状态默认） |

## 键盘（手柄模拟）

| 按键 | 行为 |
|---|---|
| 方向键 | 移动焦点 |
| Enter / Space | B：确认/激活 |
| Esc / Backspace | A：返回；关 overlay；本地↔直通（直通时进入本地） |
| Q / E | L1 / R1：切串流页签、工具页签、单屏页签或设置分类 |
| Y | 串流本地模式打开/关闭工具 |
| R | 串流页切换直通/本地 |
| Tab / Shift+Tab | 下一个/上一个焦点（辅助） |
| 1 / 2 | L2 / R2：占位提示 |

焦点移到页签或设置分类时会立即切换内容；移到主机/应用卡片时只选中，B 再执行。

工具态里：A 返回控制台；控制台再 A 回直通。触摸板按钮、键盘每个键、快捷键卡片都可键盘/手柄焦点到达。

> 原型从“本地”串流模式开始，方便检查焦点环；生产版按输入契约默认直通。按 R 或 Esc 切换。

## 直接打开某个状态

可以在 URL 后加参数，便于截图和评审：

```text
index.html?page=apps
index.html?page=settings
index.html?page=stream&tab=input
index.html?page=stream&tab=status
index.html?page=stream&mode=passthrough
index.html?page=stream&pane=tools&tool=touchpad
index.html?page=stream&pane=tools&tool=keyboard
index.html?page=stream&pane=tools&tool=shortcuts
index.html?page=hosts&overlay=pair
index.html?page=apps&overlay=app-actions
index.html?page=remote&overlay=add
index.html?page=stream&view=single
```

支持的参数：

- `page=hosts|apps|remote|settings|stream`
- `view=single`
- `overlay=pair|add|qr|exit|delete|lock|app-actions`
- `mode=local|passthrough`
- `pane=console|tools`
- `tool=touchpad|keyboard|shortcuts`
- `tab=status|video|audio|input|connection`（旧 `performance` 自动映射到 `status`）
- `singleTab=status|video|audio|input|connection`

## 交互范围

- 点击/键盘都能操作。
- 主机选中会更新详情和上屏只读预览。
- 应用选中会更新上屏；启动进入串流控制台，默认选中“状态”；应用操作 Sheet 含上次设置/退出/隐藏/详情。
- 状态页只读：帧率/延迟/码率、60s 趋势、2×2 信息矩阵（画面/链路/本机/主机）；无可点击设置行，主机实时负载标“未提供”。
- 添加主机有数字键盘；IP 支持多点号，提交时做基础格式校验。
- 配对 PIN → 二维码 → 返回配对，overlay 有焦点陷阱。
- 直通时控制台降到 58%，只保留本地入口；本地时焦点环可见。
- 工具态触摸板全宽、点阵面、触点跟手；左/中/右键、拖拽锁定、灵敏度 0.8–1.5×。
- 键盘 ASCII 直发，无拼音/候选词；Ctrl/Win/Alt/Shift 粘滞，`?123` 切符号页。
- 快捷键 4×3 + 顶部动作条（本地键盘/主机键盘/Windows HDR/休眠/退出/解锁）；Win+L 走确认层。
- 单屏抽屉可收起为悬浮“控制台”按钮。
- Toast 只用于原型反馈，生产版由真实状态驱动。

## 不覆盖

- 真实串流画面、真实 EasyTier 状态、真实证书/PIN
- 真实鼠标/键盘 HID、真实系统 IME
- 手柄震动/体感、多语言
- 浅色主题
- 538dp（Thor）窄版和横竖屏重排
- 单屏工具态（P1；双屏工具已建模）
- 文件传输（MVP 明确不做）

## 设计约束

- 上屏永远只读，无 OSD/按钮/焦点。
- 控件只在下屏；单屏模式是同一套控件的抽屉化。
- Lumen Gold `#F2C14E` 是主强调色；焦点环用白色，不用金色光晕。
- 品牌标使用最终 A1 全金 SVG 的内联 symbol 缩放，不重绘小尺寸版本；原型不请求外部图标文件。
- 粉色不进入 LumaDuo。
- 所有数据是静态示例，状态页也明确标注“示例数据”；主机实时负载不伪造。
- 不要从原型复制 Compose 实现，按 `DESIGN-SYSTEM.md` 的 token 重做。
