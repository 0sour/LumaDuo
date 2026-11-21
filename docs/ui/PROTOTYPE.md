# HTML 原型说明

> 文件：[prototype/index.html](prototype/index.html)
> 这是设计与焦点参考，不是生产代码。

## 1. 打开

直接用浏览器打开 `docs/ui/prototype/index.html`。

- 推荐 1440×900 以上窗口。
- 页面中央是设备模拟：上屏 16:9 + 下屏 640×480。
- 所有数据是静态示例，不发网络请求。

### 直接打开某个状态

评审/截图时可用 URL 参数跳过点击：

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

支持 `page`、`view`、`overlay`、`mode`、`pane`、`tool`、`tab`、`singleTab`。完整列表见
[prototype/README.md](prototype/README.md)。

## 2. 页面

- **主机**：主机列表 + 详情 + 添加/配对入口；详情含网络测试/详情/更多/删除
- **应用**：应用网格 + 启动串流 + 应用操作 Sheet（启动/上次设置/退出/隐藏/详情/快捷方式）
- **远程**：EasyTier 状态/启动停止 + 配置/PC 配置/二维码 + 手动添加主机
- **设置**：7 类分类（画面/音频/输入/网络/主机/界面/关于）+ 设置行
- **串流控制台**：状态/画面/音频/输入/连接 5 tabs（状态默认）+ 工具态（触摸板/键盘/快捷键 + 动作条）
- **配对 overlay**：4 位 PIN + QR 占位
- **单屏预览**：视频全屏 + 控制台抽屉（5 tabs，状态默认）

## 3. 键盘映射

| 按键 | 行为 |
|---|---|
| 方向键 | 移动焦点 |
| Enter / Space | 确认/激活 |
| Esc / Backspace | 返回/关闭 overlay |
| Q / E | L1 / R1，切串流页签、工具页签、单屏页签或设置分类 |
| Y | 串流本地模式打开/关闭工具 |
| 1 / 2 | L2 / R2，占位提示（二级 tab 未建模） |
| Tab / Shift+Tab | 下一个/上一个焦点（辅助） |
| R | 串流页切换直通/本地（模拟 Return） |

> 浏览器不支持真实手柄直通；原型用键盘模拟焦点与模式。

## 4. 交互范围

- Rail 页面切换
- 主机选中 → 详情更新；详情可打开网络测试/更多/删除
- 应用选中 → 上屏预览；启动按钮进入串流；应用操作 Sheet 含上次设置/退出/隐藏/详情
- 串流 tab 切换，状态页置首并默认
- 状态页：帧率/延迟/码率、60s 趋势、2×2 信息矩阵（画面/链路/本机/主机）；无可点击设置行，主机实时负载标“未提供”
- 控制台/工具双态；工具三页签 + 快捷键动作条
- 触摸板触点、按钮、灵敏度；键盘粘滞修饰键；快捷键 Win+L 确认
- 直通/本地模式切换 + ModeBar 更新
- 配对 overlay 打开/关闭；应用操作 overlay
- 单屏预览开关
- 焦点环随方向键移动；Enter 激活
- 焦点移到页签/设置分类时立即切换内容；移到卡片时只选中
- 原型默认“本地”串流模式以便检查焦点；生产默认直通。R / Esc 切换
- 配对 → 二维码 → 返回配对，overlay 焦点陷阱
- 触摸/点击也可操作

## 5. 不覆盖

- 真实串流画面
- 真实主机性能数据（原型为明确标注的示例；主机实时负载不伪造）
- 真实鼠标/键盘 HID（工具只做视觉与状态模拟）
- 真实 EasyTier 状态
- 系统 IME
- 单屏工具态（双屏工具已建模）
- 真实手柄震动/体感
- 多语言
- 浅色主题

> 品牌标内联在 `index.html`，不请求外部图标文件；`design/icons/uuyc-a1/` 只作为源资产，不在原型运行时加载。

## 6. 文件

```text
prototype/
├── index.html   # 结构 + 页面
├── styles.css   # token / 布局 / 焦点环
├── app.js       # 路由、焦点、键盘、状态
└── README.md    # 使用说明 + URL 参数
```

## 7. 修改规则

- 颜色/间距/圆角只改 `styles.css` 的 CSS 变量。
- 页面结构改动同步 `SCREEN-SPEC.md`。
- 焦点/键位改动同步 `INPUT-AND-FOCUS.md`。
- 不把原型 CSS 直接搬进 Compose；按 token 重新实现。
