# 测试与验收

> 状态：2026-11-21 RP Duo 双屏主链路已通过（启动/配对/串流/直通/本地/退出/断开/网络测试/应用图标/覆盖升级）；最小 duo Phase A/B/C/D1 完成，duo release 16.46 MB；设置页直属选项行、远程页卡片布局、下屏 640×480dp 密度修复、控制台 5 页签/工具态与上屏浏览页已在真机核对。单屏/PiP 按 D-043 延期，不作为当前验收门槛。
> 未验证项在各节明确标出，不用局部通过替代完整验收；当前重点是实体手柄/键盘/EasyTier/长时稳定性。

## 0. 设备保护（OLED）

RP Duo 是 OLED 屏幕，烧屏不可逆。测试时必须：

- 不要把主机/应用/设置/远程页或串流控制台静态停留数分钟以上。
- 需要长时间看同一页面时先降低亮度；测试间歇直接熄屏。
- 长时串流测试优先让上屏视频动态播放，下屏控制台降低亮度，不要高亮静态 HUD 长挂。
- 截图/日志采集完成后立即熄屏，不把设备当常亮监视器使用。
- 任何“常亮调试页/诊断页”都必须限时，不能作为长期展示。

## 1. 测试层级

| 层级 | 内容 | 工具 |
|---|---|---|
| JVM 单测 | InputRouter、DisplayCoordinator 角色解析、EasyTier 配置生成、ModeBar 文案 | JUnit |
| Compose UI 测试 | 焦点顺序、overlay 陷阱、页签切换、键盘 | Compose UI Test |
| Instrumentation | Presentation 5 owners、BackDispatcher、IME 状态、显示热插拔 | AndroidX Test |
| 真机 | Duo / Thor 双屏、手柄、串流、EasyTier | adb + 手动 |
| 视觉验收 | 640×480 / 538×469、焦点环、无裁切 | 截图 + 对照 |

## 2. 必测纯逻辑

### InputRouter

- 四态转换：HOME_LOCAL / PASSTHROUGH / STREAM_LOCAL / OVERLAY
- 40ms 去重
- DOWN/UP 成对转发
- 方向/返回/肩键 repeat 吞掉
- B/A 本地语义 vs 远端 canonical
- Return/Back 在 Presentation 来源时仍能打开本地菜单
- 切直通时 release-all/零值只发一次

### DisplayCoordinator

- 排除 DEFAULT_DISPLAY 和 Activity 自身 display
- FLAG_PRESENTATION + 非 OFF
- 640×480 / 538×469 断点
- `canHostTasks` 差异
- 显示热插拔 debounce
- 无下屏回退单屏

### EasyTierConfigExporter

- Android `.1` / PC `.2`
- 同 network_name/secret
- peer 行生成
- 特殊字符转义
- 密钥不进入日志

### ModeBar

- 直通/本地/菜单/无手柄
- 长文本省略
- 不接收焦点

## 3. M0 验收

- [x] 未修改 V+ 构建成功（M0a，nonRoot debug）
- [x] 安装到 RP Duo（Android 16，product `sun`）
- [x] Foundation Sunshine 配对（PIN 1205，`/api/pin` 返回 `status:true`）
- [x] 1080p60 串流（实际协商 HEVC，非 H.264）
- [x] 首帧/码率/延迟记录（见下方实测）
- [x] 双屏外接行为记录（上屏视频；下屏未由 V+ 接管，仍是系统设置）
- [x] 无崩溃/ANR（`logcat -b crash` 为空）

2026-11-20 实测：

| 项 | 值 |
|---|---|
| 上屏 | 1920×1080，display 0，120Hz 面板/60Hz 串流 |
| 下屏 | 1280×960 @320dpi，display 2，`FLAG_PRESENTATION`，`canHostTasks=false` |
| 编码 | HEVC |
| 帧率 | 60 fps（Rd/Rx 均 60） |
| 配置码率 | 20 Mbps |
| 实测网络 | 254 KB/s（静态桌面样本，不代表高动态场景） |
| 延迟 / RTT / 丢包 | ≈18 ms / 4 ms / 0.0% |
| 电量 | 89% |
| 首帧 | 未精确计时；Game Activity 394 ms，视频约 2–3 s 内出现（人工观察） |
| 本地菜单 | 上屏 Return 打开 V+ Game Menu；首次多一层教学层，第二次 Back 关闭 |

> 串流时 V+ 仍把视频放在上屏，下屏没有被接管。M3 必须由 LumaDuo 自己把控制台放进下屏 Presentation。

## 4. M0.5 双屏探针

- [x] 枚举 display：id/name/size/density/flags/canHostTasks
- [x] 上屏 Activity + 下屏 Presentation 显示 Compose
- [x] 下屏 Presentation 是否拿到 key focus（`mCurrentFocus` 为 LumaDuo Presentation）
- [x] 手柄按键落到哪个窗口（`input -d 2` 进入 Presentation；D-pad 移动焦点）
- [x] Return/Back 是否被 Presentation 接收（未 dismiss，回调路径生效）
- [ ] 系统 IME 是否显示在下屏（探针尚无输入框）
- [ ] 自绘数字/ASCII 键盘可用（M3）
- [x] 640×480 布局（640×480dp 无裁切，rail/appbar/modebar 尺寸正确）
- [ ] 538×469 布局（无 Thor）
- [ ] 单屏回退（未移除下屏）
- [ ] 下屏被占用/热插拔

探针结论：

- `Presentation` 的 5 个 owner 正常工作，Compose 在下屏可组合、可聚焦。
- 空载时下屏 `state OFF`，`Presentation.show()` 后获得焦点；截图前若设备 Dozing 会全黑，先 `KEYCODE_WAKEUP` + `wm dismiss-keyguard`。
- 焦点环在 rail 和主机行完整显示，未裁切；触摸切换 rail 正常。
- 下屏 Return 被 Presentation 截获，Activity 未收到；250 ms 去抖和 IME 状态仍待 M3。
- 从下屏 launcher 启动 Activity 的 re-home 尚未在 LumaDuo 接入。

### adb

```bash
export ADB="${ANDROID_HOME}/platform-tools/adb"
export DUO=<adb devices 输出中的 serial>
export TOP_DISPLAY_ID=<上屏 SurfaceFlinger unique id>
export BOTTOM_DISPLAY_ID=<下屏 SurfaceFlinger unique id>

"$ADB" -s "$DUO" shell "dumpsys display | grep -E 'DisplayDeviceInfo|mDisplayId=|FLAG_PRESENTATION|canHostTasks'"
"$ADB" -s "$DUO" shell "dumpsys window displays | grep -E 'Display: mDisplayId|mCurrentFocus'"
"$ADB" -s "$DUO" shell input -d 2 keyevent 22      # 下屏 D-pad 右
"$ADB" -s "$DUO" shell input -d 2 keyevent 4       # 下屏 Return
"$ADB" -s "$DUO" exec-out "screencap -p -d $TOP_DISPLAY_ID" > top.png
"$ADB" -s "$DUO" exec-out "screencap -p -d $BOTTOM_DISPLAY_ID" > bottom.png
```

- 截图用 SurfaceFlinger unique id，不是逻辑 0/2。
- 设备 Dozing 时截图全黑；先 `input keyevent 224` + `wm dismiss-keyguard`。
- 下屏触摸注入用逻辑 display 2；验证 Activity/InputRouter 用 `-d 0`。

## 5. M1 改包名验收

- [x] 启动无 UnsatisfiedLinkError
- [x] 配对成功（Foundation PIN）
- [x] 串流视频（上屏 1080p60 HEVC）
- [ ] 串流音频（未单独验证）
- [ ] 手柄直通（未验证实体手柄）
- [ ] EasyTier 启动
- [ ] Shizuku 服务（如启用）
- [x] release 构建 + R8 构建通过（unsigned；签名/安装未测）
- [x] 应用名/图标/包名正确（`com.sour.lumaduo.debug`、LumaDuo）

## 6. M2 UI 验收

- [ ] Rail：主机/应用/远程/设置全部可手柄到达（四页触摸直达已验证；设置分类/行 D-pad 已验证，远程页逐项未测）
- [x] AppBar 44dp、ModeBar 28dp、rail 64dp 不裁切
- [x] 640×480 无横向滚动
- [ ] 538×469 无裁切；主机列表单列、应用网格 2 列（无 Thor）
- [x] 焦点环外扩不裁切（rail、主机行、设置分类、设置行）
- [ ] overlay 焦点陷阱/恢复
- [x] 首屏只有一个焦点环
- [ ] 所有关键操作可纯手柄完成（主机/应用/设置已接真实数据；远程启停仍未接）
- [x] 触摸一次点击直接生效

### M2b-1 真实主机（2026-11-20）

- [x] `ComputerManagerService` 暴露 `getComputers()` 快照
- [x] `DuoHostRepository` 绑定服务并订阅更新
- [x] 下屏主机页显示真实 `GAMING-PC / 192.168.1.20 / 已配对 / LAN`
- [x] 点击已配对主机进入现有 `AppView`
- [x] 未配对/添加主机回退旧版 `PcView`
### M2b-2 真实应用（2026-11-20）

- [x] 选中真实主机后读取真实应用列表
- [x] 下屏 3 列应用网格：Desktop / Steam Big Picture / Xbox Game
- [x] 点击 Desktop 从下屏直接启动现有 `Game`/串流
- [x] 串流性能层显示 1920×1080 HEVC 60、≈17 ms、0.0% 丢包
- [ ] 应用图标（当前为首字母色块）

### M2b-3 真实设置/远程 + 生产入口（2026-11-20）

- [x] 桌面 alias 启动 `DuoMainActivity`，上屏只读、下屏生产壳层
- [x] 设置页读取真实 `PreferenceConfiguration`：1920×1080 / 60 Hz / 20 Mbps / 自动 / HDR 关
- [x] A 或 → 增加、← 减少分辨率；写入后立即从偏好回读并显示“已保存”
- [x] 音频声道立体声 → 5.1 → 立体声；震动模式可循环（测试后已恢复立体声）
- [x] 码率按 Kbps 存储、UI 显示 Mbps；20 Mbps 显示正确
- [x] 设置分类获得焦点即切换；分类 → 内容行 → 值调整的 D-pad 路径可用
- [x] 远程页读取真实 EasyTier TOML：网络名 easytier、10.0.0.1/24、Peer、3 个监听
- [x] 密钥只显示“未设置/已设置 · 已遮罩”，不显示也不写日志
- [x] EasyTier 初始化日志只记录 network/ipv4/peer 数/secret 是否设置，不再输出整段 TOML
- [x] 下屏 ComposeView 使用 Presentation display context：1280×960 屏得到 640×480dp（非 compact），AppBar 标题/副标题同行
- [x] 远程页按设计稿重排：EasyTier 配置卡 + 连接状态卡 + 手动添加条；节点/延迟真实，丢包“—”
- [x] 密度修复后再次串流：控制台 640dp、状态页 22–23 FPS（静态桌面）/ RTT 3–4 ms、连接页与退出确认正常
- [x] 控制台画面/音频/输入页签真实读写：屏幕模式、显示缩放、HDR、静音、声道、触摸模式、震动
- [x] 工具态三页：触摸板（单指/双击/长按拖拽、双指滚动/右键）、ASCII 键盘、12 快捷键；Win+L/休眠/退出二次确认
- [x] 主机页网络测试：TCP 3/3 端口、最低 2 ms/平均 10 ms，结果覆盖层与重测
- [x] 设置帮助/诊断：设备、系统、640×480dp、29 解码器、11 输入设备，网络诊断/帮助文档入口
- [x] 设置高级项补全：增强触摸分区/边界、麦克风音量处理/固定增益、陀螺仪鼠标/灵敏度/反转
- [x] 上屏浏览态：主机大标题/地址/状态 tags/显示器示意；设置分类说明 + 5 条当前值；远程网络图与虚拟 IP；切换分类后上屏同步
- [x] HDR 开启时若 `hdrMode=0` 自动补 `HDR_MODE_HDR10`，避免被 SDR 模式覆盖
- [x] 运行状态如实显示“未启动”，不伪造“已连接”
- [x] 从生产壳层启动 Desktop 串流；退出 Game Menu 后回到原应用页
- [x] `PcView` 回退仍可打开；返回时 top Back 回调不再误关双屏壳层
- [x] `KEYCODE_BUTTON_B` 与 BACK/ESCAPE 均由 Presentation 截获
- [ ] 设置页 538×469 / 字号放大
- [ ] EasyTier 运行状态与下屏启停（M4）

> 单元测试：2026-11-20 M2b-3 `:app:testNonRootDebugUnitTest` 通过；此前 `DualSenseBluetoothHapticsPathTest.sequenceGapPreservesPartialPacketAndFilterHistory` 的时序 flaky 本轮未复现，后续仍按偶发处理。

### UI v0.3 代码交付（2026-11-20，未真机）

本轮只做功能盘点、设计补全、Compose 实现与构建验证；设备安装、截图、焦点、IME、EasyTier 运行和单元测试按用户要求留待其回来执行。

- [x] `docs/ui/FEATURE-MAP.md` 覆盖 V+ 浏览/设置/Game Menu/输入/网络入口的保留/合并/延后/不做
- [x] 7 类设置、主机详情/添加/PIN、应用操作 Sheet、远程配置/PC 配置/QR、五页签控制台、工具态和确认层代码完成
- [x] `:app:assembleNonRootDebug` 通过（input/focus 修复后 49,646,178 B，`93cad755…19a7`）
- [x] `:app:assembleNonRootRelease` 通过（unsigned，29,553,975 B，`87f86eb3…ea03`）
- [x] HTML 原型 `app.js` 通过 `node --check`
- [x] 手柄选择优化：主机/应用焦点即预览详情；活动 Rail 确认键进入内容；滑杆单焦点 + 左右调整；窄屏应用网格 2 列
- [x] 上下屏优化：DisplayListener 热插拔/re-home、上下屏常亮、下屏全屏、上屏镜像主机/应用选择（代码/构建完成）
- [ ] 浏览器交互/视觉检查
- [ ] 真机安装与 640×480 视觉、焦点、overlay 陷阱
- [ ] 下屏系统 IME / 自绘键盘
- [ ] 538×469（Thor）
- [ ] EasyTier 真实启停、VPN 授权、PC `.2` 配置
- [ ] PIN 配对、应用隐藏/快捷键、WOL/睡眠/退出
- [ ] 串流控制台接真实 `Game` 数据；InputRouter 与鼠标/键盘/快捷键 Sink
- [ ] `:app:testNonRootDebugUnitTest`（本轮按用户要求未跑）

### 输入/焦点/双屏修复（2026-11-20，代码完成，未真机）

- [x] 新壳层统一为实体 B 确认、实体 A 返回；Presentation 拦截实体 A 作为返回
- [x] B 与合成 `DPAD_CENTER` 40ms 去重，避免一次按键触发两次
- [x] Presentation 的 `ActivityResultRegistry` 委托给 Activity，不再使用 no-op stub
- [x] overlay 获得焦点、Up/Down 焦点边界、关闭后回到触发控件
- [x] 主机/应用/设置列表行增加透明焦点余量，避免滚动时环被裁
- [ ] 真机确认 Duo 丝印与 Xbox/Retro 固件；处理 legacy PcView 的 A 确认例外
- [ ] 下屏 SAF/权限 launcher 回调验证
- [ ] overlay Left/Right 边界、IME 下 B/A 行为验证

### 串流主链路（双屏；单屏延期）

- [x] `DuoStreamBridge` 接 `Game` 的 `ConnectionStarted/Terminated/onPerfUpdateWG` 与时长/电量/热状态
- [x] `GameDuoConsoleHost` 在 `Game` 内创建下屏 `SecondScreenPresentation`
- [x] 直通 `FLAG_NOT_FOCUSABLE` / 本地请求焦点；触摸下屏进入本地；A/Back 返回直通
- [x] `GameDuoStreamActions` + `DuoStreamKeySender`：鼠标、键盘、修饰键、12 个固定快捷键
- [x] 断流/退出 `releaseForwardedKeys()`；单屏 `Game` 根布局 60% 控制台面板（代码保留）
- [x] 无下屏时 `DuoMainActivity` 自渲染 `DuoShellScreen`；<600dp 紧凑 Rail/AppBar（代码保留）
- [x] 工具页符号页 Shift、修饰键 mask、触摸板点击不再先移动光标
- [x] `:app:testNonRootDebugUnitTest` 通过（含 8 个新增 stream 单测）
- [x] 真机：上屏视频 + 下屏真实控制台，状态页真实值
- [x] 真机：触摸进入本地、A 返回直通
- [ ] 真机：B 确认、HAT/实体手柄全键位
- [ ] 真机：触摸板、ASCII 键盘、快捷键到主机；断流无卡键
- [~] 单屏壳、单屏 60% 控制台、PiP：按 D-043 延期，不纳入当前验收
- [ ] 真机：`recreate()` 重连黑屏、断流重连

### 最小可控版 Phase A/B/C（代码/构建完成；2026-11-21 RP Duo 首轮 smoke）

Phase A：

- [x] 新增 `duo` flavor（现有 root dimension），`MINIMAL_EDITION=true`，仅 arm64
- [x] `src/duo/AndroidManifest.xml` 移除 PcView/AppView/StreamSettings/Crown/诊断/小组件/更新/相机/外部存储等
- [x] `ServerHelper` 默认 duo console；壳层隐藏 legacy 入口；“开源声明”直连
- [x] 快捷方式/通知在最小版指向 `DuoMainActivity`
- [x] `shrinkResources` + `en/zh-rCN` locale filter

Phase B：

- [x] `StreamSnapshot` / `StreamMetrics` / `StreamSessionController`
- [x] `Game` 实现 controller，真实阶段/性能/时长写入 `StateFlow`
- [x] `GameDuoStreamActions` 只依赖 controller，不再接收 `Game`
- [ ] 最小 `DuoStreamActivity`（有意留到真机验证后；本轮未做）

Phase C：

- [x] Firebase/Crashlytics/Analytics 网络路径移除，`AnalyticsManager` 改为本地 no-op
- [x] 最小版关闭 GameMenu/Crown/虚拟手柄/浮动球/USB/audio-haptics/性能浮层/framegen
- [x] duo 打包排除 framegen/USBIP/haptics/iperf3/libc++ native，haptics 音频 assets 移到 full source set
- [x] duo manifest 移除 USB/无障碍/Shizuku/Poster 组件与 Shizuku 权限
- [x] duo debug 36,093,355 B；duo release 16,430,156 B（原 29,596,391 B，-44.5%）
- [x] nonRoot release 26,370,119 B；full edition 仍含 optional native/assets
- [x] duo/nonRoot unit tests 与 duo lint 通过；nonRoot haptics 测试在并行负载下抖动一次，单独重跑通过
- [x] duo release merged manifest 无 legacy Activity/USB/无障碍/Shizuku/Poster 组件
- [x] `llvm-readelf` 确认剩余 native 不依赖 `libc++_shared`
- [x] 真机冷启动、发现、配对、进入应用页
- [x] 旧 full debug 覆盖安装到正式包名 `com.sour.lumaduo.debug`，配对/远程配置保留
- [x] 真机串流：Desktop，上屏 PC 桌面，下屏状态页 59–60 FPS / 3–4 ms RTT / 1920×1080 60Hz
- [x] 真机触摸进入本地、A 返回直通
- [x] 真机退出串流、断开连接均回到壳层
- [x] 连接页真实网络测试：TCP 3/3 端口，最低 3 ms / 平均 4 ms
- [x] 主机应用图标：Desktop / Steam Big Picture / Xbox Game 真实 box art
- [x] 真机确认移除 native 后无 `UnsatisfiedLinkError`/类初始化异常
- [x] 首轮发现并修复 `SecondScreenPresentation` 退出时 lifecycle `DESTROYED -> STARTED` 崩溃（`bcfefa01`）
- [x] 记录主机 Sunshine VDD 风险：`ensure_only_display` 在异常会话后可能留下物理屏全黑；本机已改为 `ensure_active`（备份 `sunshine.conf.bak-20261121`）
- [ ] 真机实体手柄/键盘/快捷键到主机
- [ ] 真机 EasyTier 隧道、单屏回退、显示热插拔、长时串流
- [ ] Phase D2：legacy 源码/资源/full flavor 删除

## 7. M3 串流验收

- [ ] 上屏纯视频，无 OSD/焦点
- [ ] 首帧前只读状态，首帧后隐藏
- [ ] 下屏控制台 5 tabs（状态/画面/音频/输入/连接）；状态置首并默认
- [ ] 直通：手柄控制 PC，UI 无焦点
- [ ] Return/Back 打开本地菜单
- [ ] 本地：手柄操作 UI，PC 输入暂停
- [ ] 切回直通无卡键
- [ ] 调整分辨率/码率/HDR 不崩
- [ ] 控制台/工具双态；Y 切换，A 工具→控制台→直通
- [ ] 直通时工具不可聚焦；触摸/Return 进入本地
- [ ] 触摸板单指移动/点击、双指滚动/右键、长按拖拽
- [ ] 触摸板左/中/右键、拖拽锁定、灵敏度
- [ ] 触摸板离开/断流释放鼠标键
- [ ] ASCII 键盘所有键可手柄到达；B 发送；A 返回
- [ ] Ctrl/Win/Alt/Shift 粘滞；?123 符号页；Esc/Tab/Enter/方向键
- [ ] 键盘无拼音/候选词；系统 IME 仅触摸补充
- [ ] 离开键盘/工具/断流清空修饰键，无卡键
- [ ] 12 个快捷键发送顺序与释放正确
- [ ] Win+L 需确认，不直接发送
- [ ] 状态页无任何可点击设置行；触摸/确认不改变设置；从 tab 向下保持在 tab
- [ ] 状态页显示客户端帧率/延迟/码率/丢帧/解码，60s 趋势可读
- [ ] 状态页 2×2 信息矩阵四格完整：画面/链路/本机/主机；无裁切、无错位
- [ ] 状态页主机静态/实时不可用时显示“未提供”，不显示假 CPU/GPU/内存
- [ ] 状态页 1s 聚合不阻塞串流；主机接口不可达不影响串流
- [~] 单屏抽屉 5 tabs：按 D-043 延期
- [ ] 断线重连
- [ ] 切后台/Presentation hide/show 不断流

## 8. M4 网络验收

- [ ] PIN 配对
- [ ] QR（有摄像头）
- [ ] EasyTier Android `.1` + PC `.2`
- [ ] Foundation 识别 `mesh: easytier`
- [ ] 手动虚拟 IP 添加
- [ ] 错误密钥/错误网络名/PC 离线文案
- [ ] 密钥不出现在 logcat/截图
- [~] 单屏所有页面可达：按 D-043 延期

## 9. 性能指标（目标）

| 指标 | 目标 |
|---|---|
| 1080p60 H.264 首帧 | < 5s（局域网） |
| 串流延迟 | 按 V+ baseline 记录，不劣化 |
| 下屏 UI 帧率 | 60fps；不阻塞解码 |
| 焦点移动 | 无掉帧/残影 |
| 冷启动到主机列表 | < 3s |
| Presentation 重建 | < 1s，不丢会话 |

## 10. 发布门

- 无 P0/P1 缺陷
- GPL/NOTICE/源码可获取
- 正式包无遥测/密钥
- Duo 必过；Thor 记录已知问题
- 文档与实现一致

## 11. 原型自检（非生产门）

用于设计评审，不代表 App 已验证：

- [ ] `index.html?page=stream&tab=status` 默认 `tab-status` 为 active + focused。
- [ ] 控制台 5 tabs 顺序为状态/画面/音频/输入/连接；状态在最左。
- [ ] 状态块内 0 个 focusable/button；方向键从 tab 向下仍停在 tab。
- [ ] 状态明细为 2×2 矩阵，四格内容完整，主机负载显示“未提供”。
- [ ] 品牌标使用内联 A1 SVG 缩放；无外部图标请求，zcode-html 下无破图。
- [ ] 640×480 下无裁切；538dp（Thor）窄版尚未在原型中建模。
