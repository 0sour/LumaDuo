# 最小可控版（duo edition）

> 状态（2026-11-21）：Phase A/B/C 完成，Phase D1 完成。`duo` 已接管正式包名 `com.sour.lumaduo`；RP Duo 覆盖安装升级路径、发现/配对/串流/直通/本地/退出/断开、真实网络探测与主机应用图标已通过。
> 代码：`67c95e90 feat(top): mirror page context on the read-only display`，前置 `c81d5d2d`（主机重新配对）、`f3e65804`（控制台入口/诊断）、`3d41c7f1`（下屏密度/远程页）、`4dfb5c2b`（设置页直属选项行）、`2a4330f8`（底栏/应用网格）、`ddca4f8d`（真实应用图标）、`eb23ebdb`（网络探测）、`a9d36b5d`（正式包名）、`bcfefa01`（Presentation 退出修复）、`83663e44`（Phase B/C）、`b1f94894`（Phase A）
>
> **duo release 16,455,976 B（16.46 MB）**，比 Phase A 的 20.24 MB 再减 3.78 MB，比原 V+ release 29.60 MB 减 44.4%。

## 1. 目标

把 LumaDuo 做成 bili-duo 式的简洁产品，而不是继续暴露 V+ 的完整产品面：

- 上游只保留串流引擎：`NvConnection`、标准 MediaCodec、音频、输入、配对、EasyTier、剪贴板。
- 产品层由 LumaDuo 自控：`DuoMainActivity`、双屏/单屏控制台、设置、焦点、发布面。
- 旧 V+ 页面、Crown、USB、PyroWave/framegen、触觉、遥测、更新、小组件不进入最小版 release。

这不是一次性重写，而是“先隔离产品面，再抽引擎边界，最后删可选模块”的绞杀路线。Phase A 隔离产品面，Phase B 建立边界，Phase C 删除可选模块；Phase D 才把最小版变成唯一产品。

## 2. Phase A：最小 `duo` edition

### 2.1 Gradle

- 在现有 `"root"` flavor dimension 新增 `duo`，不新增 dimension，现有 `nonRoot`/`root` 任务名不变。
- `duo`：
  - `applicationId "com.sour.lumaduo"`（Phase D1 已接管正式包名；`nonRoot` 改为 `com.sour.lumaduo.full`）
  - `ROOT_BUILD=false`
  - `MINIMAL_EDITION=true`
  - `PRODUCT_FLAVOR=duo`
  - 仅 `arm64-v8a`
- `nonRoot`/`root` 显式 `MINIMAL_EDITION=false`。
- `:pyrowave-runtime` 只挂到 `nonRoot`/`root`；duo 不打包 `libpyrowave-shared.so`。
- release 打开 `shrinkResources true`；duo 通过 `androidComponents` 只保留 `en` / `zh-rCN` 资源。
- `src/duo/` 新增 manifest overlay，不复制主源码。

### 2.2 最小 manifest（Phase A + C）

`app/src/duo/AndroidManifest.xml` 移除：

- legacy Activity：`PcView`、`AppView`、`StreamSettings`、`CrownStoreActivity`、Capability/Stick/Controller 诊断、`AddComputerManually`、`AppSelectionActivity`、`SunshineWebUiActivity`、`HandbookActivity`。
- 小组件与更新：`GameListWidgetProvider/Service`、`WidgetConfigurationActivity`、`UpdateDownloadReceiver`、update FileProvider。
- 可选输入/控制器：`UsbDriverService`、`KeyboardAccessibilityService`、`ShizukuProvider`、`PosterContentProvider`。
- 权限/feature：CAMERA、外部存储、MANAGE_EXTERNAL_STORAGE、READ_PRIVATE_SETTINGS、WRITE_SECURE_SETTINGS、INSTALL/REQUEST_INSTALL_PACKAGES、TV EPG、微信 query、Shizuku 权限、所有 camera feature。
- 保留：`DuoMainActivity`、launcher aliases、`Game`、Discovery/ComputerManager、EasyTierVpnService、StreamNotificationService、ShortcutTrampoline、HelpActivity、OpenSourceNoticesActivity、clipboard FileProvider、ConfigurationSyncReceiver。

合并后的 `duoRelease` manifest 已确认上述组件为 0；无 Firebase provider/permission。

### 2.3 代码收口

- `ServerHelper.createStartIntent` 默认 `duoConsole = BuildConfig.MINIMAL_EDITION`，最小版所有启动路径都进入新控制台。
- `Game` 在最小版强制 `duoConsoleEnabled=true`，避免通知/外部入口冷启动落回旧菜单。
- `DuoMainActivity.openLegacy()` 在最小版只提示，不启动 `PcView`。
- `DuoShellScreen` 在最小版隐藏“打开旧版主机管理”；`DuoSettingsPage` 隐藏“帮助/诊断”、PyroWave/实验性、虚拟手柄、性能浮层、外接显示器、原生触觉、USB 手柄驱动入口。
- 串流控制台在最小版隐藏音频振动、波形触觉、虚拟手柄、USB、性能浮层等无效行。
- “开源声明”改为直接启动 `OpenSourceNoticesActivity`，不再绕 `PcView`。
- `ShortcutTrampoline` 的 PC-level 分支在最小版改为启动 `DuoMainActivity`；app shortcut 继续带 `duoConsole=true`。
- `StreamNotificationService` 的通知在最小版指向 `DuoMainActivity`，标题为 `R.string.app_label`。

## 3. Phase B：引擎边界

### 3.1 `StreamSessionController`

新增 `app/src/main/java/com/sour/lumaduo/stream/`：

- `StreamSnapshot`：phase、主机/应用/地址、分辨率、配置 FPS/码率、编码、HDR、实际 FPS/RTT/主机/解码/渲染延迟、实际码率、时长、notice。
- `StreamMetrics`：RTT 高低 32 位解码与带宽字符串解析，供引擎和产品显示共用，避免两份逻辑漂移。
- `StreamSessionController`：`snapshot: StateFlow<StreamSnapshot>`、`sessionSeconds()`、`setConsoleMode()`、退出/重连/断开/解锁/性能浮层、鼠标、`releaseAllInputs()`。

`Game` 现在实现该接口：

- `onCreate` 建立初始 snapshot；
- `prepareConnection` → `CONNECTING`；
- `connectionStarted` → `CONNECTED`，刷新编码/HDR；
- `onPerfUpdateWG` 更新真实性能字段；
- `stopConnection` → `STOPPED`；
- `notifyDuoConnectionTerminated` → `STOPPED` 或 `ERROR + notice`。

不伪造主机 CPU/GPU/内存/丢包；缺失值保持 `null`。

### 3.2 产品动作只依赖接口

`GameDuoStreamActions` 构造函数从 `Game` 改为 `StreamSessionController`，映射：

| 产品动作 | 接口 |
|---|---|
| 直通/本地 | `setConsoleMode` |
| 退出 | `exitStream` |
| 重连 | `reconnectStream` |
| 断开 | `disconnectStream` |
| 解锁主机 | `unlockHost` |
| 鼠标 | `mouseMove` / `mouseButton`（1 左 / 2 中 / 3 右） |

`DuoSingleScreenConsoleHost` 的模式切换也改为走 actions，不再直接调 `Game.setDuoConsoleMode`。双屏 `Presentation` 与单屏 `ComposeView` 仍由 `Game` 持有，因为它必须是前台 Activity owner；这是产品宿主职责，不是引擎调用。

### 3.3 尚未抽出的部分

没有在本轮新建 `DuoStreamActivity` 替换 `Game`。原因：

- `Game` 约 4k 行，输入处理器、Surface 生命周期、`NvConnection` 全局槽、分辨率切换、音频和 PiP 都直接依赖 Activity；
- 没有真机回归的前提下重写入口，风险高于收益；
- 先建立接口边界，让产品层不再依赖 `Game` 类型，等 M3 真机验收后再做 Activity 抽取。

因此 Phase B 的“边界”已完成，“引擎搬出 Activity”留到 Phase D/真机验证后。

## 4. Phase C：删除可选模块

### 4.1 全局遥测移除

- 删除 Firebase BOM、Crashlytics Gradle 插件和依赖；删除 `google-services` 配置。
- `LimelightApplication` 不再初始化 Firebase。
- `AnalyticsManager` 改为纯本地 no-op 会话计时 facade；不发送事件、不写网络。
- `preferences.xml` 移除分析开关；manifest 移除 `FirebaseInitProvider` 和 AD_ID/install-referrer 注入。
- `RendererExceptionTest` 不再依赖 Crashlytics 内部类，改为验证异常本身保留诊断信息。

### 4.2 最小版关闭可选路径

`BuildConfig.MINIMAL_EDITION` 在 `Game` 中关闭：

- GameMenu 构造与入口（最小版 Back/A 只进 LumaDuo 控制台）；
- Crown / `ControllerManager` 初始化；
- 虚拟手柄、浮动球、start wheel；
- USB driver 绑定、USB 转发入口；
- audio-haptics / `AudioVibrationService`；
- 性能浮层 / jitter monitor；
- framegen 规划、surface 准备和 HDR 配置；
- host unlock 保留（控制台工具页需要），不属于本轮删除项。

R8 常量折叠后，duo dex 中 `GameMenu` 只剩 5 个元数据字符串，`AudioVibrationService`/`FloatBallHandler`/`PerformanceOverlayManager` 已不在 dex 中。

### 4.3 打包排除

`androidComponents` 对 duo 变体排除：

- `libmoonlight-framegen.so`
- `libmoonlight_usbip.so`
- `libmoonlight_haptics_android.so`
- `libiperf3.so`
- `libc++_shared.so`（仅被上述模块使用；已用 `llvm-readelf` 确认剩余 `.so` 不依赖它）

`assets/haptics/native` 从 `src/main` 移到 `src/full/assets`，只挂到 `nonRoot`/`root`。duo debug 仍会带入 haptics SDK AAR 的 4 个 `generated` 资源（约 0.45 MB），release 的 resource shrink 后不再包含。

### 4.4 当前仍保留

- EasyTier 两个 `.so` 压缩后约 9.1 MB，是最大单项；远程串流是 MVP 核心。
- `:usbip-backend`、`:framegen`、`:moonlight-haptics-android` 仍作为 Java 编译依赖存在，以便全量版共用 `Game`；duo 只缺 native/asset，且相关路径已关闭。
- `audioHapticsSdkDir` 仍是 `settings.gradle` 硬依赖，因为 `libmoonlight-core.so` 编译时需要 audio-haptics 头文件；真正移除需要 JNI 与 SDK 同步拆。
- `moonlight-core` 保留标准 MediaCodec、音频、输入、控制器、EasyTier JNI 桥、剪贴板。

## 5. 构建结果

| 产物 | 大小 | SHA-256 |
|---|---:|---|
| `duoDebug` | 37,343,599 B | `a378f072d3b3730c04d5dac79a7b0385dd75833affca2300f9f426cc1fbdc7e4` |
| `duoRelease` | **16,455,976 B** | `cce63defc7138c03f00bbb84633ae6f4a8c5b6b1493f624fc739ed7873e8884e` |
| `nonRootRelease`（full 回归） | 26,370,119 B | `1f9a5d9e039ba627154e4308e9dd7fe34ff092bc61f8aa4a1f6aa4efd0673316` |

duo release 组成：

- dex：`classes.dex` 6.56 MB + `classes2.dex` 0.30 MB；
- `resources.arsc`：0.39 MB；
- native：EasyTier 6.29 MB + 17.93 MB（未压缩），`libmoonlight-core.so` 2.93 MB；
- 无 framegen/USBIP/haptics/iperf3/libc++、无 haptics 音频 assets、无 Firebase。

对比：

- 原 V+ release 29,596,391 B → duo release 16,455,976 B，减少 13,140,415 B（44.4%）。
- Phase A 20,238,108 B → 当前 16,455,976 B，减少 3,782,132 B（18.7%）。
- nonRoot release 也从 26,969,384 B 降到 26,370,119 B（Firebase 移除）。

## 6. 验证

```bash
export JAVA_HOME="/path/to/jdk-17"
export PATH="$JAVA_HOME/bin:$PATH"

./gradlew :app:testDuoDebugUnitTest :app:lintDuoDebug
./gradlew :app:assembleDuoDebug :app:assembleDuoRelease
./gradlew :app:testNonRootDebugUnitTest :app:assembleNonRootRelease
```

已通过：

- duo unit tests；duo lint（新代码无未基线错误）；
- nonRoot unit tests（`DualSenseBluetoothHapticsPathTest` 在并行构建负载下有一次超时抖动，单独重跑通过）；
- duo debug/release、nonRoot release 构建；
- `duoRelease` merged manifest 无 legacy Activity/USB/无障碍/Shizuku/POSTER 组件；
- duo release native 依赖检查：剩余 `.so` 不需要 `libc++_shared`；
- full edition 仍包含 framegen/USBIP/haptics/iperf3 和 haptics assets。

### 6.1 RP Duo 首轮真机 smoke（2026-11-21）

环境：RP Duo（Android 16，product `sun`），`com.sour.lumaduo.debug`（Phase C/D1 debug；覆盖安装自旧 full debug 包，配对数据保留），Foundation Sunshine `GAMING-PC` 局域网。

已通过：

- [x] 冷启动 `DuoMainActivity`，无 `UnsatisfiedLinkError`/`NoClassDefFoundError`/crash；
- [x] Phase D1 覆盖安装：旧 `com.sour.lumaduo.debug`（full）升级到 duo debug 后，主机配对/地址/EasyTier 配置数据保留，壳层直接显示已配对主机；
- [x] 新包名下再次串流 Desktop 并退出：回到壳层应用页，主机物理显示器保持激活（`ensure_active`），无 lifecycle 崩溃；
- [x] 连接页真实网络测试：TCP 47984/47989/47990，返回 `3/3 端口 · 最低 3 ms · 平均 4 ms`，无伪造丢包；
- [x] 主机应用图标：Desktop / Steam Big Picture / Xbox Game 从 Sunshine `appasset` 拉取真实 box art，失败时保留字母占位；
- [x] 2026-11-24 Foundation 1123：应用列表显示 Desktop / Steam Big Picture / Xbox Game / 大屏 4 项，无图应用使用主机生成封面；应用详情显示 `FsSource`/`FsKind`/`FsDefault`；主机详情显示真实 `FsOsVersion`/`FsGpu`/`FsDisplayModes`；
- [x] 2026-11-24 上屏由方图升级为 Foundation `FsArt` hero（Steam Big Picture、大屏实机），无 hero 时回退 poster；
- [x] 上屏品牌页、下屏真实壳层（主机/主机详情），显示真实主机名/地址；
- [x] 发现并配对 `GAMING-PC`（PIN 验证，`/api/pin` 返回 `status:true`），进入应用页读取 3 个真实应用；
- [x] 启动 Desktop 串流：上屏 PC 桌面正常，下屏控制台显示 1920×1080 60Hz、59–60 FPS、3–4 ms RTT、Wi-Fi、电量/温度；主机负载显示“未提供”，实测码率为 `—`，无伪造；
- [x] 触摸下屏进入本地模式，A 返回直通；`连接` 页显示主机/已配对/RTT/EasyTier 未启动；
- [x] “退出串流”确认后回到壳层；主机保持 `运行中`，壳层显示“继续串流”；
- [x] “断开”确认后回到壳层，无残留按键/崩溃；
- [x] 首轮测试发现并修复 `SecondScreenPresentation` 退出时 lifecycle `DESTROYED -> STARTED` 崩溃：`Dialog.dismiss()` 先触发 `onStop`，原实现已把 registry 标为 DESTROYED；现在 `onStop` 有 guard，`dismiss()` 等 `super.dismiss()` 完成后再发 `ON_DESTROY`。

### 6.2 主机虚拟显示事件（2026-11-21）

这次 smoke 还暴露了主机端 Sunshine VDD 的风险：

- 主机配置为 `display_device_prep = ensure_only_display` + `output_name = ZakoHDR`。串流时 Sunshine 把 Windows 拓扑切换成“只剩虚拟显示器”，物理显示器变为 NOT AVAILABLE。
- 第一次退出时客户端 `SecondScreenPresentation` 崩溃，会话异常结束；Sunshine 没有恢复原先的双物理屏拓扑，导致主机所有物理屏黑屏。客户端崩溃已由 `bcfefa01` 修复，但主机端恢复失败是独立问题。
- 恢复方式：`Win+Ctrl+Shift+B` 重置显示驱动，或重新应用显示拓扑；本次已恢复。
- 为防再次发生，主机 `sunshine.conf` 已从 `ensure_only_display` 改为 `ensure_active`（备份 `sunshine.conf.bak-20261121`），让物理显示器在串流期间保持激活；即使会话异常结束，也不会只剩虚拟屏。
- 该改动会改变主机端的“串流时隐藏物理屏”行为。如果以后要恢复 `ensure_only_display`，必须先确认 Sunshine 的 VDD 恢复逻辑在该版本上可靠，并准备异常退出后的恢复脚本。

未验证：真实手柄（本轮用 ADB keyevent/touch 模拟）、键盘/快捷键到主机、EasyTier 实际隧道、538×469、显示热插拔、长时串流、视觉逐项验收。单屏/PiP 按 D-043 延期，不纳入当前验收。

## 7. Phase D

### D1 已完成

- `duo` applicationId 切回 `com.sour.lumaduo`，接管正式包名和升级路径。
- `nonRoot` full edition 改为 `com.sour.lumaduo.full`，避免与正式包冲突；`root` 保持 `com.sour.lumaduo.root`。
- Duo 设备上覆盖安装验证：旧 full debug 数据保留，已配对主机、地址和远程配置可直接使用。
- 新包名下重新串流/退出通过，主机物理显示器保持激活。
- 产物：duo release 16,430,120 B；full release 26,365,539 B。

### D2 待做（高风险，分步）

- 真正删除 `PcView`/`AppView`/`StreamSettings`/`GameMenu` 源码与旧资源，而不是只靠 R8 删除。
- 这些类被 `Game`、`DuoMainActivity.openLegacy`、`ShortcutTrampoline`、grid/adapter 和设置页交叉引用；需要先拆出独立数据模型/入口，再删除，不能直接 `rm`。
- 移除 full editions、`src/full/assets`、audio-haptics/pyrowave/framegen/usbip 编译依赖及对应 JNI。
- 更新 CI、BUILDING、ROADMAP、TESTING 和发布说明。

当前建议：先保持 full edition 作为调试对照，等实体手柄/EasyTier/长时稳定性验证完成后再做 D2，避免同时破坏产品和参考实现；单屏/PiP 按 D-043 延期。

## 8. 风险

- native 库移除的首轮启动/串流已通过，但未覆盖所有可选路径（USB/手柄热插拔、framegen 偏好残留）；继续用真机确认。
- `duo` 与正式包名并存，升级路径 Phase D 才统一。
- `audioHapticsSdkDir` 仍是全局构建依赖，CI 必须先准备 SDK。
- `shrinkResources` 可能误删动态引用资源；duo release 尚未逐项真机确认。
- GameMenu/旧输入代码仍以 Java 类形式参与全量编译；duo 只靠 R8 常量折叠删除，不是源码级隔离。
- 网络测试、WebUI、EasyTier 运行态、主机负载仍未接；控制台对应行显示提示/“—”，不伪造数据。
