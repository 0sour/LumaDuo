# 上游功能落位矩阵

> 目的：把 Moonlight V+ `v12.15.17` 的全部用户功能与入口盘点清楚，再决定 LumaDuo 把每一项放在哪里。
> 原则：**不照搬 V+ 的多套重叠菜单**；每个功能只有一个主入口，串流内只保留可行动项。
> 状态：2026-11-20 · 设计基线 v0.3。实现状态以 SCREEN-SPEC / ROADMAP 为准。
> 2026-11-24：仅控制流的原生 SDP 崩溃已修；按键提示按 Xbox/Retro 风格切换。
> 本表仍包含“不做/延期”项，不能把全部行当作待实现清单。

## 1. 盘点范围

上游功能面分成四块：

| 区域 | 上游入口 | 主要源码 |
|---|---|---|
| 浏览/主机/应用 | `PcView` 顶栏、主机卡、长按 Sheet、`AppView` 下拉面板、应用 Sheet、快捷方式 | `PcView.kt`、`AppView.kt`、`grid/*`、`preferences/AddComputerManually.kt` |
| 设置树 | 单一 `StreamSettings`，约 15 个主分类、20 个子页、100+ 偏好项 | `res/xml/preferences.xml`、`preferences/*` |
| 串流内菜单 | V+ Game Menu 单面板 + 快捷行 + 卡片 + 子页；Start 轮盘、悬浮球 | `gamemenu/*`、`ui/FloatBallManager.kt`、`ui/StartHoldWheelOverlay.kt` |
| 串流内浮层/能力 | 性能 HUD、网络通知、麦克风、USB、PiP、剪贴板、帧生成、EasyTier | `perfhud/*`、`UsbForwardingController.kt`、`framegen/*`、`utils/easytier/*` |

上游的典型问题：设置页、Game Menu、Start 轮盘、悬浮球四套入口互相重叠；主机/应用/串流内各有一套网络与显示设置；同一功能在不同地方用不同粒度出现。LumaDuo 用下面的单一入口模型收敛。

## 2. LumaDuo 全局导航

```text
本地（上屏只读 + 下屏操作）
├─ 主机
│  ├─ 主机详情（右侧面板 / 窄屏 Sheet）
│  ├─ 添加主机（手动 / 扫码）
│  ├─ PIN 配对
│  ├─ 主机操作 Sheet（继续、应用、唤醒、网络测试、休眠、退出、删除、高级）
│  └─ 应用 ──→ 应用网格
│              ├─ 应用操作 Sheet（启动、复用上次设置、退出并启动、隐藏、详情、快捷方式）
│              └─ 启动串流
├─ 远程
│  ├─ EasyTier 状态/启停
│  ├─ 网络配置（网络名、密钥、虚拟 IP、Peer、监听、高级 flags）
│  ├─ PC 配置（复制 TOML / QR / .2 说明）
│  └─ 手动添加主机（预填 10.0.0.2）
├─ 设置
│  ├─ 画面
│  ├─ 音频
│  ├─ 输入
│  ├─ 网络
│  ├─ 主机
│  ├─ 界面
│  └─ 关于
└─ 串流（下屏变控制台，隐藏 Rail）
   ├─ 控制台
   │  ├─ 状态（默认，只读）
   │  ├─ 画面
   │  ├─ 音频
   │  ├─ 输入
   │  └─ 连接
   └─ 工具
      ├─ 触摸板
      ├─ 键盘
      └─ 快捷键
```

单屏：同一套页面，串流时视频全屏，下屏抽屉承载控制台/工具；本地页仍用列表 + 详情覆盖层。

## 3. 主机页

| 功能 | 上游位置 | LumaDuo 落位 | 层级 |
|---|---|---|---|
| 主机发现/在线状态 | PcView 网格 | 主机列表行：状态点 + 名称 + 地址 + LAN/远程/手动标签 | P0 |
| 主机选择 | 点卡片 | 单点选中，右侧详情；A/双击进入应用 | P0 |
| 进入应用列表 | 点已配对卡片 / 操作 Sheet | 详情“应用列表”按钮、AppBar ▶、A | P0 |
| 继续运行中的会话 | 封面单击 / 操作 Sheet | 详情主按钮“继续串流”；运行中徽章 | P0 |
| 手动添加 | 添加卡 | AppBar + → 添加主机 Sheet → 手动地址 | P0 |
| 扫码添加/配对 | 添加卡 | 同一 Sheet 的“扫码配对”（有摄像头时） | P1 |
| PIN 配对 | 未配对卡单击 | 配对页：4 位 PIN 大字 + 说明 + 重试/取消 | P0 |
| 多地址选择 | 多 LAN 地址弹窗 | 连接前地址选择 Sheet | P0 |
| 唤醒 WOL | 主机操作 Sheet | 详情“唤醒主机”（离线且有 MAC） | P0 |
| 网络测试 | 三个入口 | 详情“网络测试” → 统一结果页；去掉 iPerf3 独立入口 | P0/P1 |
| 主机详情 | 操作 Sheet | 右侧详情面板显示地址、状态、配对、最近连接、Sunshine 版本 | P0 |
| 删除主机 | 操作 Sheet | 详情“删除” → 二次确认 | P0 |
| 休眠主机 | 操作 Sheet | 详情“更多” → 休眠（确认） | P1 |
| 退出主机应用 | 操作 Sheet | 详情“更多” → 退出应用（确认） | P1 |
| 打开 Sunshine WebUI | 操作 Sheet | 详情“更多” → 在浏览器打开；提示需要 Web UI 登录 | P2 |
| IPv6 开关 | 操作 Sheet | 详情“高级” → IPv6 | P1 |
| 自动解锁 Windows | 操作 Sheet | 详情“高级” → 自动解锁（生物识别 + PIN） | P2 |
| 恢复上次会话 | PcView 溢出菜单 | 主机页 AppBar “继续上次”或最近会话条 | P1 |
| 显示未配对设备 | PcView 溢出菜单 | 主机页筛选 Chip：全部/已配对/未配对 | P1 |
| 主机排序/过滤 | 自动排序 | 在线优先 → 已配对 → 名称；筛选 Chip 可切换 | P1 |
| 壁纸/摇一摇/封面保存 | PcView 手势 | **不做**（V+ 品牌化功能） | 不做 |

主机操作 Sheet 分组：主操作（继续/应用/配对/唤醒）、网络（测试/详情）、更多（休眠/退出/WebUI/删除）。危险操作单独红色区 + 二次确认。

## 4. 应用页

| 功能 | 上游位置 | LumaDuo 落位 | 层级 |
|---|---|---|---|
| 应用网格 | AppView | 3 列（640）/2 列（538）；图标 + 名称 + 运行中角标 | P0 |
| 启动应用 | 点卡片 | A/单击启动；有其它应用运行时弹应用操作 Sheet | P0 |
| 应用操作 Sheet | 长按 / X/Y | 启动、复用上次设置、退出并启动、退出应用、隐藏/取消隐藏、详情、快捷方式 | P0 |
| 复用上次设置 | AppView 底部胶囊 | 应用操作 Sheet“使用上次设置启动”；详情显示上次分辨率/FPS/码率/HDR | P0 |
| 应用详情 | 操作 Sheet | 名称、App ID、HDR 支持、命令列表、最近启动 | P1 |
| 隐藏/取消隐藏 | 操作 Sheet | 应用页 AppBar 筛选“显示已隐藏”后可见并操作 | P1 |
| 创建桌面快捷方式 | 操作 Sheet | 应用操作 Sheet“创建快捷方式”（需已加载封面） | P1 |
| 应用搜索/排序 | 无 | **不新增**；主机返回顺序 + 首字母索引条（可选 P2） | P2 |
| 收藏/置顶 | 无 | **不做**（快捷方式已覆盖高频入口） | 不做 |
| AppSelectionActivity 小组件 | 桌面小组件 | 不搬独立 UI；桌面快捷方式走主应用应用页 | 不做 |

## 5. 远程页

| 功能 | 上游位置 | LumaDuo 落位 | 层级 |
|---|---|---|---|
| EasyTier 状态 | EasyTier 控制对话框状态页 | 远程页状态卡：未启动/等待 VPN/启动中/等待节点/已连接/错误 | P0 |
| 启动/停止 VPN | 对话框按钮 | 状态卡主按钮；首次触发系统 VPN 授权 | P0 |
| 网络名 | 配置页 | 配置卡可编辑行 | P0 |
| 网络密钥 | 配置页（明文输入） | 配置卡遮罩；A 长按/确认后显示；复制 PC 配置时提示包含密钥 | P0 |
| 虚拟 IPv4 | 配置页 | 配置卡可编辑（默认 `10.0.0.1/24`） | P0 |
| Peer 列表 | 配置页 | 配置卡多行编辑/添加/删除 | P0 |
| 监听地址 | 配置页 | 高级配置页 | P1 |
| flags（smoltcp/延迟优先/禁 P2P/私有/禁 IPv6/KCP/QUIC/系统代理/禁加密/禁打洞） | 配置页 | 高级配置页，分组开关；默认折叠 | P1 |
| 本机虚拟 IP/节点数/延迟 | 状态页 | 状态卡摘要 | P0 |
| 复制 PC 配置 | 对话框 | “复制 PC 配置”生成 `.2` TOML；Toast 提示已复制 | P0 |
| PC 配置 QR | 对话框 | QR Sheet：二维码 + “PC 使用 10.0.0.2”说明 + 复制文本 | P1 |
| 手动添加主机 | 无 | 远程页底部“添加虚拟 IP 主机”，预填 `10.0.0.2` | P0 |
| 重置为默认配置 | 配置页 | 高级配置页“恢复默认”（确认） | P1 |
| 密钥日志 | 上游打印完整 TOML | **禁止**：日志只记录 network/ipv4/peer 数/secret 是否设置 | 已修复 |

## 6. 设置

设置从 6 类扩为 **7 类**：画面 / 音频 / 输入 / 网络 / 主机 / 界面 / 关于。640×480 下左栏 104dp，7×44dp = 308dp，可完整显示。

### 6.1 画面

| 子页/行 | 上游来源 | 控件 | 层级 |
|---|---|---|---|
| 分辨率 | `list_resolution` | 列表 Sheet：原生/720p/1080p/1440p/2160p/自定义 | P0 |
| 帧率 | `list_fps` | 列表：30/60/90/120/设备刷新率/自定义 | P0 |
| 码率 | `seekbar_bitrate_kbps` | 滑杆 5–150 Mbps（高级可到 800） | P0 |
| 智能码率 / ABR 模式 | `checkbox_adaptive_bitrate`、`list_abr_mode` | 开关 + 三档 | P0 |
| 视频格式 | `video_format` | 自动/HEVC/H.264/AV1/PyroWave | P0 |
| HDR / HDR 模式 | `checkbox_enable_hdr`、`list_hdr_mode` | 开关 + HDR10/HDR10+/HLG/DV | P0 |
| 帧节奏 / Host Cadence | `frame_pacing`、host cadence | 列表 + 开关 | P1 |
| FEC | `list_fec_mode`、`seekbar_fec_percentage` | 列表 + 滑杆（高级） | P1 |
| 主机渲染缩放 | `seekbar_resolutions_scale` | 50–400% | P1 |
| 拉伸/画面位置/偏移/反向 | `settings_picture_layout` | 开关 + 九宫格 + 两个滑杆 | P1 |
| 外接显示器 / PiP | `use_external_display`、`checkbox_enable_pip` | 开关（能力门控） | P1 |
| 帧生成 | `category_framegen_settings` | 开关 + 质量/自适应；DLL 导入 | P1/P2 |
| 性能浮层 | `settings_performance_overlay` | 开关 + 样式/位置/透明度/指标多选 | P1 |
| 解码缓冲 / HEVC 低延迟 / 输出队列 / MTK | 高级设置 | 高级子页 | P2 |
| 能力诊断 / PyroWave 诊断 | `capability_diagnostic` | 诊断页 | P1 |

### 6.2 音频

| 子页/行 | 上游来源 | 控件 | 层级 |
|---|---|---|---|
| 声道 | `list_audio_config` | 立体声/5.1/7.1/7.1.4 | P0 |
| 音频直通 | `settings_audio_passthrough` | 开关 + 编码/缓冲/AC3 标记 | P1 |
| 空间音频/音效 | `checkbox_enable_spatializer`、audiofx | 开关 | P1 |
| 麦克风 | `category_microphone_settings` | 启用、初始状态、码率、增益/平衡/人声增强、按钮位置/颜色 | P1 |
| 音频振动 | `settings_audio_vibration` | 开关、强度、路由、场景 | P1 |
| 波形触觉 | Waveform 卡 | 通道测试 | P2 |
| 主机音频 | `checkbox_host_audio` | 放在“主机”类 | P1 |

### 6.3 输入

| 子页/行 | 上游来源 | 控件 | 层级 |
|---|---|---|---|
| 死区 | `seekbar_deadzone` | 滑杆 0–20% | P0 |
| 震动模式 | `list_game_rumble_mode` | 智能/仅本机/仅手柄 | P0 |
| 触摸模式 | `list_native_mouse_mode_preset` | 增强/经典/触控板/原生 | P0 |
| 指针速度 / 增强分区 | `settings_input` 触控组 | 滑杆/分区滑杆 | P1 |
| 双击拖拽 / 长按拖拽 | `pref_enable_double_click_drag` | 开关 + 阈值 | P1 |
| 触控板灵敏度/预设 | 串流内 Touch Sensitivity | 滑杆 + 预设保存/应用/管理 | P1 |
| 键盘：ESC 菜单键、远程 IME、触屏键盘 | `settings_input` 键盘组 | 列表/开关 | P0 |
| 鼠标：中键/滚轮修复、轴修正、硬件触控板 | `settings_input` 鼠标组 | 开关/列表/滑杆 | P1 |
| 虚拟手柄 | `category_onscreen_controls` | 显示、布局、L3/R3、Guide、半高、透明度、重置 | P1 |
| 手柄：多手柄/JoyCon/翻键/Guide | `settings_gamepad` 控制/玩家组 | 开关 | P1 |
| 体感/陀螺仪 | `settings_gamepad` 体感组 + Gyro 卡 | 开关、回退、灵敏度、反转、激活键 | P1 |
| 手柄鼠标模拟 | `controller_mouse_settings` | 速度/滚动/D-pad | P1 |
| USB 手柄驱动 | `checkbox_usb_driver` | 开关 + 绑定全部 + 诊断 | P2 |
| DS5 模拟/输出/实验触觉 | `settings_gamepad` 反馈/驱动组 | 列表/开关 | P2 |
| 摇杆校准/手柄诊断 | `stick_calibration`、`controller_diagnostic` | 子页 | P1 |
| Shizuku 键盘无障碍 | `checkbox_shizuku_keyboard_accessibility` | **不做**：LumaDuo 不做 root/Shizuku 系统注入 | 不做 |

### 6.4 网络

| 子页/行 | 上游来源 | 控件 | 层级 |
|---|---|---|---|
| EasyTier 状态/入口 | PcView EasyTier 按钮 | 摘要行 + “打开远程页” | P0 |
| STUN | `checkbox_enable_stun` | 开关 | P1 |
| 网络测试 | 三套诊断 | 选择主机 → 统一测试页（延迟/丢包/建议码率） | P1 |
| 手动添加主机 | 添加卡 | 跳转添加 Sheet | P0 |
| IPv6 | 主机菜单 | 主机详情高级项 | P1 |
| iPerf3 / WebUI | 主机菜单 | **合并进网络测试高级页**；不单独做入口 | P2 |

### 6.5 主机

| 子页/行 | 上游来源 | 控件 | 层级 |
|---|---|---|---|
| SOPS 主机优化 | `checkbox_enable_sops` | 开关 | P1 |
| 屏幕组合模式 | `list_screen_combination_mode` | 列表：主机默认/不操作/自动/主屏/副屏/独占 | P1 |
| 主机音频 | `checkbox_host_audio` | 开关 | P1 |
| 仅控制流 | `checkbox_control_only` | 开关 | P1 |
| 退出行为 | `list_quit_behavior` | 停止主机应用/仅断开 | P1 |
| 断开后锁屏 | `checkbox_lock_screen_after_disconnect` | 开关 | P1 |
| 后台串流 | `list_background_stream_behavior` | 断开/恢复/保持/保持+音频 | P1 |
| 剪贴板文本/图片 | `checkbox_clipboard_sync_text/image` | 开关；文件传输不做 | P0 |
| 自动解锁 | HostUnlockUi | 高级子页（生物识别 + PIN） | P2 |

### 6.6 界面

| 子页/行 | 上游来源 | 控件 | 层级 |
|---|---|---|---|
| 语言 | `list_languages` | MVP 锁定“简体中文”；预留列表 | P0 |
| 主题 | AppThemeSheet | 锁定“深色 · Lumen Gold”；不提供粉色/背景取色 | P0 |
| 控制台默认页 | 设计基线 | 锁定“状态”；只读说明 | P0 |
| 状态页指标 | Perf overlay items | 多选：帧率/延迟/码率/RTT/丢包/网络/电量/趋势 | P1 |
| 性能浮层样式 | `list_perf_overlay_style` | 迷你/标准/详细；位置/方向/透明度 | P1 |
| 控制台不透明度 | Game Menu opacity | 本地 100%/直通 60% 固定；不提供滑杆 | P1 |
| 动画/密度 | V+ 主题 | Luma 固定；不做 | P1 |
| 背景/壁纸/摇一摇/启动图标变体 | PcView | **不做** | 不做 |
| Game Menu 卡片显隐/排序 | 设置 + 运行内 | **不做**：控制台固定布局 | 不做 |
| 悬浮球 | `settings_float_ball` | **不做**：Return/Back 打开控制台 | 不做 |
| Start 轮盘 | 手柄长按 Start | **不做**：Return/Back 打开控制台 | 不做 |
| 分析/遥测开关 | `checkbox_enable_analytics` | **不做**：无遥测 | 不做 |

### 6.7 关于

| 行 | 上游来源 | 行为 | 层级 |
|---|---|---|---|
| 版本/构建号 | About | `BuildConfig` | P0 |
| 上游/许可证 | About | V+ 12.15.17 / Foundation Sunshine / GPL-3.0 | P0 |
| 开源声明 | OpenSourceNoticesActivity | 本地 notices 列表 | P1 |
| 帮助/文档 | Handbook/HelpActivity | 打开项目文档；不做 V+ 手册域名 | P1 |
| 诊断 | 能力/手柄/网络诊断 | 三个诊断入口 | P1 |
| 更新 | UpdateManager | “暂未配置更新源”；不访问 V+ 更新 | P0 |
| 开发者选项 | DeveloperUnlockSettings | 隐藏；不做 GitHub Star 解锁/Crown 发布 | P2 |
| 隐私 | 无 | 明示无遥测、无 Crashlytics | P0 |
| 赞助/QQ/B站/官网 | About/Sponsor | **不做** | 不做 |
| Crown 商店 | CrownStoreActivity | **不做** | 不做 |

## 7. 串流控制台

下屏变控制台，隐藏 Rail。Return/Back 从直通进入本地；A 在根层恢复直通。直通时控制台 60% 不透明度且无焦点环；本地 100%。

### 7.1 状态（默认，只读）

| 内容 | 上游来源 | 说明 |
|---|---|---|
| 2×2 矩阵：画面 / 网络 / 本机 / 主机 | Perf HUD 字段 | 只读；无按钮、无 focusable |
| 实际 FPS、帧时间、1% low | Perf HUD | 客户端实测 |
| 延迟链（网络/解码/渲染/主机） | Perf HUD | 主机缺失显示“未提供” |
| 码率、RTT、抖动、丢包 | Perf HUD/NetworkQuality | 客户端实测 |
| 电量/温度/Wi-Fi | 本机状态 | 温度可用时显示 |
| 60 秒趋势 | 设计新增 | 最近 60s 内存数据；无主机负载伪造 |
| 主机静态信息 | Foundation 可选 | CPU/GPU/内存缺省“未提供” |

### 7.2 画面

| 行 | 上游 | 行为 |
|---|---|---|
| 分辨率 | Display 卡 | 列表 Sheet；热切换失败回退重连 |
| 帧率 | Display 卡 | 列表 Sheet；含设备刷新率 |
| 屏幕模式/主机显示器 | Display 卡 | 主机默认/主屏/副屏/独占/VDD |
| 码率 / 智能码率 / ABR | Bitrate 卡 | 滑杆 + 开关 + 三档 |
| HDR | 快捷动作 | 只切 Windows HDR 的快捷动作放在“快捷键”动作条；真实 HDR 在设置/重连 |
| 显示缩放 | Display 卡 | 100% 滑杆 + 应用 |
| 拉伸/画面位置 | 设置 | 设置项；运行时只读摘要 |
| 性能浮层 | Perf HUD | 开关/样式；M3 接入 |

### 7.3 音频

| 行 | 上游 | 行为 |
|---|---|---|
| 本机音量/静音 | 设计新增 | `AudioManager` 控制下屏设备音量 |
| 声道 | Audio 卡 | 立体声/5.1/7.1/7.1.4；切换需重连 |
| 音频直通/编码 | 设置 | 开关 + 编码；需重连 |
| 麦克风 | 麦克风设备页 | 开关、设备、码率、增益 |
| 音频振动/触感 | Audio/Haptics 卡 | 开关、强度、路由、场景 |
| 主机音频 | 主机设置 | 开关 |

### 7.4 输入

| 行 | 上游 | 行为 |
|---|---|---|
| 输入模式 | 直通/本地 | 只读状态 + “进入本地”按钮 |
| 触摸模式 | 控制分段 | 增强/经典/触控板/原生 |
| 触控板灵敏度/滚动 | Touch Sensitivity | 滑杆 + 预设 |
| 陀螺仪 | Gyro 卡 | 开关、鼠标模式、激活键、灵敏度、反转 |
| 虚拟手柄 | OSC | 显示/隐藏、布局、透明度 |
| 手柄鼠标模拟 | 设备快捷 | 开关 + 速度 |
| 震动 | 手柄设置 | 模式/强度 |
| 键盘开关 | 键盘动作 | 本地/主机键盘 |
| USB 设备 | USB 面板 | 放在“连接”页 |

### 7.5 连接

| 行 | 上游 | 行为 |
|---|---|---|
| 主机名/地址/配对 | 状态摘要 | 只读 |
| RTT/丢包/带宽/延迟 | HUD | 只读 |
| EasyTier 状态/虚拟 IP | 远程页 | 只读 + 打开远程页（本地模式） |
| USB 转发 | USB 面板 | 设备列表、共享/释放、自动共享 |
| 网络测试 | NetworkQualitySheet | 重新测速 + 建议码率 |
| 重新连接 | 连接错误 | 重连 |
| 断开/退出串流 | Game Menu | 确认；退出回应用页 |
| 休眠主机 | 主机菜单 | 确认 |
| 指纹解锁 | HostUnlock | 快捷动作（P2） |

### 7.6 工具

| 工具 | 上游 | 行为 |
|---|---|---|
| 触摸板 | 触控板模式 + 灵敏度卡 | 单指移动/点击、双指滚动/右键、长按拖拽、左中右键、拖拽锁定 |
| 键盘 | 自定义键盘 + 本地/主机键盘 | ASCII 5 行、粘滞修饰键、符号页、系统 IME 补充 |
| 快捷键 | Shortcuts 卡 + QuickActionRegistry | 4×3 固定 12 键 + 动作条：本地键盘、主机键盘、HDR、休眠、退出、解锁 |

工具态顶部保留 [控制台][工具] 双态；A 返回控制台；Y 切换；离开工具/断流释放所有修饰键与鼠标键。

## 8. 上游重叠的合并决定

| 上游重叠 | LumaDuo 合并 |
|---|---|
| 设置页 + Game Menu 都改分辨率/帧率/码率/音频/触控 | 本地设置负责默认值；串流内同一组控件只改当前会话并标“应用并重连” |
| Display 卡 + HDR 快捷动作 + PiP | 画面 tab 负责显示；HDR 快捷动作只切 Windows HDR，标注清楚 |
| 触控模式散落 4 处 | 输入 tab + 工具/触摸板共用一套状态 |
| 麦克风散落快捷动作/悬浮按钮/设备页 | 音频 tab 一个入口；麦克风浮动按钮不做 |
| 键盘散落 5 处 | 工具/键盘 + 快捷键动作条 |
| 三套网络诊断 | 网络设置一个“网络测试”；连接 tab 一个“重新测速” |
| 性能 HUD + 网络通知 + 测速 Sheet | 状态 tab 常驻；连接 tab 只放测速动作；不做浮动通知卡 |
| 主机卡快速启动 + 应用列表 + 快捷方式 | 详情“继续串流” + 应用页；快捷方式保留为应用操作 |
| 场景预设 1–5 + 设置页 | 不做场景预设；避免第二套配置系统 |
| Game Menu 卡片显隐/透明度/顺序 | 不做；控制台固定 5 tab |
| Start 轮盘 + 悬浮球 + Return 菜单 | 只保留 Return/Back 打开控制台 |

## 9. 明确不做（LumaDuo 边界）

- 文件传输；只保留 V+ 现有剪贴板文本/图片同步。
- UUYC 协议、签名、RTC、文件传输代码；皎月连。
- root / Shizuku 系统级注入；Shizuku 键盘无障碍不做。
- V+ 壁纸系统、摇一摇、赞助、QQ/B站/官网生态、Crown 商店、开发者 GitHub Star 解锁。
- V+ 更新源、Firebase/Crashlytics、分析遥测。
- 多语言（MVP 简体中文）。
- 自定义宏/快捷键编辑（D-034 固定 12 个）。
- 主机自动解锁（P2，默认不做）。
- 双套应用选择 UI、桌面小组件专用 AppSelectionActivity。
- 场景预设 1–5、Game Menu 卡片布局编辑、Start 轮盘、悬浮球。

## 10. 实施分层

| 层级 | 内容 |
|---|---|
| P0 | 主机列表/详情/添加/PIN、应用网格/操作 Sheet、远程状态/配置/复制/手动添加、7 类设置的核心项、串流控制台 5 tab、工具态三页、单屏抽屉 |
| P1 | 主机高级操作、扫码、网络测试、EasyTier QR、音频直通/麦克风/振动、触控/手柄细项、虚拟手柄、性能浮层、USB、PiP、外接屏、后台串流、诊断 |
| P2 | 帧生成、PyroWave、自定义分辨率/帧率编辑器、DS5/实验触觉、USB 驱动、自动解锁、WebUI、iPerf3、开发者选项 |
| 不做 | 见第 9 节 |

## 11. 入口与焦点规则

- 每个功能只有一个主入口；串流内只放“现在能改变会话”的项。
- 所有一级页、二级页、Sheet、对话框可用 D-pad + A/B 完成；触摸是补充。
- Sheet/对话框打开时焦点陷阱；关闭后恢复到打开前控件。
- B/Back：二级页 → 上一级；Sheet → 关闭；根页 → 双击退出应用；串流本地 → 根层恢复直通。
- 上屏永不出现按钮、焦点环、Toast、HUD。
- 危险操作（删除、退出串流、Win+L、休眠、断开）必须二次确认；密钥显示/复制必须确认并遮罩默认。
