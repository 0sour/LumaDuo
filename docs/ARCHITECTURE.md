# 技术架构

> 状态：M0b/M0.5/M1/M2a/M2b-3 已验证；生产入口 `DuoMainActivity`、下屏 `SecondScreenPresentation`/`DuoShellScreen` 和主机/设置/远程数据仓库已落地。`StreamSessionController` 接口已建立并由 `Game` 实现，产品动作只依赖接口；InputRouter、独立 `DuoStreamActivity` 和剩余目标模块仍在实施。

## 1. 总览

```text
┌─────────────────────────────────────────────────────────────┐
│ PC: Foundation Sunshine + EasyTier                           │
│  游戏串流 / HDR / 音频 / 输入 / 虚拟屏                         │
└───────────────▲──────────────────────────────▲──────────────┘
                │ GameStream / Moonlight 协议   │ EasyTier 虚拟网络
┌───────────────┴──────────────────────────────┴──────────────┐
│ Android: LumaDuo                                             │
│                                                              │
│  ┌──────────────────────────────┐   ┌────────────────────┐  │
│  │ 上屏 Activity                 │   │ 下屏 Presentation  │  │
│  │ · StreamView（只读视频）       │   │ · Compose UI       │  │
│  │ · InputRouter 入口             │   │ · 唯一焦点树        │  │
│  │ · StreamSessionController     │   │ · 控制台/设置/配对  │  │
│  └──────────────┬───────────────┘   └─────────▲──────────┘  │
│                 │ 共享状态/命令               │ 单向转发       │
│  ┌──────────────▼──────────────────────────────┴──────────┐  │
│  │ InputRouter（进程级）                                    │  │
│  │ HOME_LOCAL / STREAM_PASSTHROUGH / STREAM_LOCAL / OVERLAY│  │
│  └──────────────┬──────────────────────────────┬──────────┘  │
│                 │ 直通                          │ 本地          │
│  ┌──────────────▼──────────────┐   ┌───────────▼──────────┐  │
│  │ V+ 输入栈                    │   │ Compose 焦点树        │  │
│  │ KeyboardInputHandler         │   │ duoFocusRing         │  │
│  │ TouchInputHandler            │   │ FocusRequester       │  │
│  │ ControllerHandler            │   │ BackDispatcher       │  │
│  └──────────────┬──────────────┘   └──────────────────────┘  │
│                 │                                            │
│  ┌──────────────▼──────────────┐   ┌──────────────────────┐  │
│  │ StreamSessionController      │   │ EasyTierController    │  │
│  │ NvConnection / decoder       │   │ VPN / TOML / 状态     │  │
│  └─────────────────────────────┘   └──────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
```

## 2. 技术选型

| 层 | 选择 | 说明 |
|---|---|---|
| 语言 | Kotlin + Java | 沿用 V+；新 UI 全部 Compose |
| UI | Jetpack Compose + Material 3 | 下屏 Presentation 内 ComposeView |
| 串流 | Moonlight V+ `nonRoot` | NvConnection / MoonBridge / StreamView |
| PC 端 | Foundation Sunshine | 不打包，只做兼容与文档 |
| 组网 | EasyTier（客户端内置 JNI） | PC 端单独装 EasyTier |
| 持久化 | DataStore / SharedPreferences | 沿用 V+ 的偏好体系，逐步迁移 |
| 构建 | Gradle + AGP（以 V+ 为准） | JDK 17 / SDK 36 / NDK 28.2 |

不要混用 UUYC-Duo 的 AGP/Kotlin 版本；以 V+ `v12.15.17` 为准。

## 3. 模块边界

目标包结构（全面改包名后）：

> 2026-11-20 进度：`duo/SecondScreenPresentation.kt` 已在 RP Duo 验证 5 owners + Compose 下屏；`duo/DuoMainActivity.kt` 已成为桌面启动入口，上屏只读、下屏 `DuoShellScreen` 承载主机/应用/远程/设置；`DuoHostRepository`、`DuoSettingsRepository`、`DuoRemoteRepository` 已接真实数据。`SecondScreenController`、`InputRouter`、`BottomUiBridge` 仍待 M3。

```text
com.sour.lumaduo/
├── LumaDuoApplication.kt
├── MainActivity.kt                 # 上屏宿主 / 会话 / 输入第一站
├── duo/
│   ├── DisplayCoordinator.kt       # display 查找、角色、能力探测
│   ├── SecondScreenController.kt   # Presentation 生命周期
│   ├── SecondScreenPresentation.kt # 5 owners + ComposeView（已实现）
│   ├── DuoMainActivity.kt          # 生产入口：上屏只读 + 下屏壳层（已实现）
│   ├── DuoHostRepository.kt        # ComputerManagerService 绑定/主机/应用（已实现）
│   ├── DuoSettingsRepository.kt    # PreferenceConfiguration 读改回写（已实现）
│   ├── DuoRemoteRepository.kt      # EasyTier TOML 只读摘要（已实现）
│   ├── InputRouter.kt              # 进程级输入状态机
│   ├── InputSource.kt              # Activity / Presentation 来源标记
│   ├── BottomUiBridge.kt           # 单向转发 / 焦点恢复
│   └── DuoSessionViewModel.kt      # 模式、overlay、返回拦截、焦点 epoch
├── stream/
│   ├── StreamSessionController.kt  # 接口 + snapshot/metrics（Game 当前实现）
│   ├── StreamSnapshot.kt           # 引擎状态/指标快照
│   ├── StreamSurfaceHost.kt        # StreamView attach/detach
│   ├── StreamInputSink.kt          # 直通：V+ Keyboard/Touch Handler
│   ├── StreamToolkitState.kt       # 工具子视图/键位/修饰键/灵敏度
│   ├── MouseSink.kt                # 相对移动/按钮/滚轮
│   ├── KeyboardSink.kt             # ASCII 直发/修饰键
│   ├── ShortcutSender.kt           # 组合键按下/反向释放
│   ├── StreamHealthRepository.kt   # 串流状态聚合/60s 缓冲/降级
│   └── StreamStats.kt              # 首帧/码率/延迟/丢包
├── ui/
│   ├── theme/                      # DuoColors / Lumen Gold / typography
│   ├── shell/                      # Rail / AppBar / ModeBar / DuoShellScreen（已实现）
│   ├── hosts/                      # 主机列表、详情、添加、配对
│   ├── apps/                       # 应用网格
│   ├── remote/                     # EasyTier / 虚拟 IP / 配置导出
│   ├── settings/                   # 画面/音频/输入/网络/主机/界面/关于
│   ├── stream/                     # 串流控制台 5 tabs + 工具态
│   ├── single/                     # 单屏宿主与抽屉
│   ├── components/                 # FocusRing / Card / Chip / Sheet / Keyboard
│   └── navigation/
├── network/
│   ├── EasyTierController.kt       # 配置、VPN、状态
│   ├── EasyTierConfigExporter.kt   # PC .2 TOML / QR payload
│   └── PairLink.kt                 # moonlight://pair 解析
├── data/
│   ├── settings/                   # StreamSettings / AppSettings
│   └── hosts/                      # 最近主机 / 收藏 / 地址
└── util/
```

### 所有权

| 对象 | 所有者 | 生命周期 | 说明 |
|---|---|---|---|
| `NvConnection` / decoder | `StreamSessionController`（当前 `Game` 实现） | Activity | 全应用唯一；Presentation 重建不重建；产品层只读 snapshot/actions |
| `StreamView` | 上屏 Activity | Activity | 只 attach/detach surface |
| 当前主机/应用 | `DuoSessionViewModel` | Activity | 跨 Presentation hide/show 保留 |
| 当前模式 | `InputRouter` / `DuoSessionViewModel` | Activity | HOME_LOCAL / PASSTHROUGH / STREAM_LOCAL / OVERLAY；STREAM_LOCAL 内含 CONSOLE/TOOLS 子视图 |
| 工具态 | `StreamToolkitState` | Activity | 触摸板/键盘/快捷键、修饰键、灵敏度；Presentation 重建不丢 |
| 串流状态 | `StreamHealthRepository` | Activity | 聚合 StreamStats/解码/Android/EasyTier；主机静态可选；主机负载不可达显示“未提供” |
| 下屏导航栈 | `NavController`（Presentation 内） | Presentation | hide 不 dismiss，跨页面保留 |
| 下屏焦点 | Compose 焦点树 | Presentation | 只一棵树；overlay 独占 |
| EasyTier 实例 | `EasyTierController` | Application/Activity | VPN 权限 + TOML 配置 |
| 本地设置 | DataStore/SharedPreferences | Application | 不依赖 Presentation |

**原则**：Presentation 的 ViewModelStore 会随 dismiss 销毁；任何要跨重建的状态放 Activity 作用域或 repository。

## 4. 启动流程

```text
Application
  └─ MainActivity.onCreate
      ├─ rehomeToDefaultDisplay()  // 从下屏 launcher 启动时
      ├─ DisplayCoordinator.detect()
      ├─ StreamSessionController.create()
      ├─ InputRouter.create()
      ├─ SecondScreenController.start()
      └─ setContent { TopVideoHost() }   // 上屏
          └─ Presentation.show()
              └─ setContent { LumaDuoApp() } // 下屏
```

- 上屏 Activity 只渲染视频/只读状态；不承载业务页面。
- 下屏 Presentation 承载 `LumaDuoApp`（Rail + NavHost + ModeBar）。
- 单屏时，`LumaDuoApp` 直接进 Activity，视频作为背景/上层；控制台为抽屉。
- 显示热插拔：下屏消失 → 回退单屏；恢复 → 重建 Presentation。

## 5. 输入架构

### 5.1 入口

```text
Activity.dispatchKeyEvent ─┐
Presentation.dispatchKeyEvent ─┼─> InputRouter.dispatch(source, event)
Activity.onGenericMotionEvent ─┤
Presentation.onGenericMotionEvent ┘
```

- 两个窗口都可以是事件来源；用 `(keyCode, action, eventTime)` 40ms 去重。
- 只有 `InputRouter` 决定消费者；窗口本身不直接处理业务。
- 上屏视频窗口不可聚焦；下屏视频/UI 窗口按模式设置 `FLAG_NOT_FOCUSABLE`。

### 5.2 状态机

```text
HOME_LOCAL ──进入串流──> STREAM_PASSTHROUGH
     ▲                         │
     │                    Return/Back/菜单
     │                         ▼
     └──退出串流──── STREAM_LOCAL ──A/root──> STREAM_PASSTHROUGH
                              │
                          打开 panel
                              ▼
                           OVERLAY
```

- `STREAM_PASSTHROUGH`：事件交给 V+ `KeyboardInputHandler` / `TouchInputHandler`。
- `STREAM_LOCAL`：事件交给下屏 Compose；V+ 侧调用 `ControllerHandler.onExternalGameMenuOpened()`。
- 退出本地：`ControllerHandler.onExternalGameMenuDismissed()`；它进入 WAIT_FOR_RELEASE，等按键释放后恢复。
- `OVERLAY`：本地 Compose overlay 独占焦点；stream 继续但输入暂停。
- `HOME_LOCAL`：下屏导航；无远端输入。

### 5.3 V+ 接缝

- `KeyboardInputHandler.handleKeyDown/Up` → `ControllerHandler.handleButtonDown/Up`。
- `TouchInputHandler.handleMotionEvent` → `ControllerHandler.handleMotionEvent`。
- `ControllerHandler.onExternalGameMenuOpened()`：发零值、设置 MENU_ACTIVE、consume all。
- `ControllerHandler.onExternalGameMenuDismissed()`：等待释放、恢复。
- **不要**自己重写 release-all/零值逻辑。

### 5.4 工具态接缝

```text
Compose Toolkit
  ├─ Touchpad ──> MouseSink.relative(dx, dy) / button / scroll
  ├─ Keyboard ──> KeyboardSink.press/release(vk) + sticky mods
  └─ Shortcuts ─> ShortcutSender.send(keys)（按下 + 反向释放）
```

- `StreamToolkitState` 放 Activity 作用域；工具子页、键盘符号页、修饰键、触摸板灵敏度跨 Presentation 重建保留。
- `TOOLS` 是 `STREAM_LOCAL` 的子视图，不新增输入所有者；直通时不可聚焦。
- 触摸板 MVP 发送相对鼠标增量，不依赖绝对坐标；本地触点只做视觉反馈。
- `MouseSink` 必须提供 `releaseAllButtons()`；`KeyboardSink` 提供 `releaseAllKeys()`。
- 离开工具/键盘、切直通、断流、Activity pause：清空修饰键并释放所有鼠标/键盘按键。
- 快捷键：按下顺序发送，反向释放；`Win+L` 走 UI 二次确认/长按，不直接触发。
- 键盘为 ASCII HID 直发；拼音/候选词不进入该路径；系统 IME 是独立补充路径。
- 单屏复用同一 Toolkit/State；MVP 只承诺双屏工具。

### 5.5 串流状态只读页（默认）

```text
StreamStats / decoder / renderer / AudioTrack
Android battery/thermal/Wi-Fi / EasyTierController
        │
        ▼
StreamHealthRepository（1s 聚合、60s 环形缓冲、状态合并）
        │
        ├─ 客户端实测：FPS/码率/RTT/抖动/丢包/解码/渲染
        ├─ 本机：电量/温度/热状态/解码器
        ├─ 链路：Wi-Fi/EasyTier 直连或中继
        └─ 主机：静态信息可选（Foundation system info，需认证）
        ▼
StatusTab（只读 Compose）
```

- 默认页：串流控制台最左“状态”，进入串流即选中；不依赖任何主机写操作。
- 客户端实测：帧率、目标/实测码率、RTT、抖动、丢包、解码耗时、渲染耗时、丢帧原因；来自 `StreamStats`、解码器/渲染器与 Moonlight 统计。
- 本机：电量、充电、电池温度、热状态、屏幕刷新率、可用内存；来自 Android API。
- 链路：Wi-Fi 信号/频段/链路速率、EasyTier 直连/中继与虚拟 IP；`EasyTierController` 不可用时显示“未提供”。
- 主机静态：`GET /api/system/info` 可提供 CPU 型号/核心、GPU 型号/VRAM、NIC、OS、hostname；需要 Web UI 认证，Moonlight 配对证书不等于 Web UI 会话。MVP 不要求用户把 Foundation 管理员密码存到掌机；没有凭据就不显示，不能伪造。
- 主机实时：`GET /api/runtime/system` 提供 `cpu_percent`、`memory_*`、`gpu_percent`、`gpu_name`；当前 Foundation 实现要求 localhost，远程客户端不可直接调用。需要时由独立 Host Bridge 或 Foundation 只读端点补充，默认状态页不依赖。
- 状态：客户端段 `LIVE`；主机段 `STATIC`（只有静态）/ `UNAVAILABLE`（未认证或不可达）；会话未建立时 `LOADING`。不可达不伪造 CPU/GPU/内存数值。

#### 5.5.1 状态字段契约

| 区块 | 主值 | 次值 | 来源 | 刷新 |
|---|---|---|---|---|
| 画面 | 分辨率 / 帧率 | 编码 / HDR / 丢帧 | `StreamStats` / 解码器 | 1s |
| 链路 | Wi-Fi / 直连或中继 | RTT / 抖动 / 丢包 | Android / `EasyTierController` / Moonlight | 1s |
| 本机 | 温度 / 电量 | 解码耗时 / 热状态 | Android / 解码器 | 5s 或 1s |
| 主机 | CPU 型号 | GPU / OS | `system/info` 可选 | 一次或 30s |
| 主机负载 | CPU / GPU / 内存占用 | 频率 / 显存（若可用） | Host Bridge 可选 | 1s（若有） |

- 页面只读：不调用 Foundation 的 `POST` / `DELETE`，不修改分辨率、码率、HDR 或主机设置。
- 不读取/存储 Foundation 管理员密码、EasyTier 密钥、证书或 token；主机静态信息没有凭据就不显示。
- 主机实时负载不是 MVP 硬依赖；缺失时主机段写“未提供”，不能用占位数值补足。
- 状态页数据只在本机内存/轻量缓存中保留最近 60s，用于趋势图；不写入日志。
- 页面没有写操作，没有 focusable 内容；唯一焦点是“状态”tab。自动刷新，不提供重试按钮。
- 单屏 P1 复用同一 repository，抽屉内用紧凑布局；双屏是 P0。

### 5.6 面键语义

- 入口归一化：Xbox / Retro（ABXY 互换）。
- 本地：B 确认、A 返回。
- 远端：canonical Xbox A/B/X/Y。
- UI 提示按实体丝印写，不显示“canonical”。

## 6. 串流会话

```text
Host/App 选择
  └─ StreamSessionController.connect(host, app, settings)
      ├─ NvConnection 建立
      ├─ 上屏 StreamView attach
      ├─ 下屏 console 订阅状态
      ├─ 首帧 -> 隐藏上屏只读层
      └─ 输入默认 STREAM_PASSTHROUGH
```

- 串流会话 Activity 作用域；下屏 Presentation 重建不影响连接。
- 切后台：MVP 先暂停/保持策略按 V+ 能力；目标是不因 Presentation hide 断流。
- 断线：状态机 `CONNECTING / STREAMING / RECONNECTING / FAILED`；下屏显示重试。
- 流设置应用：分辨率/帧率/HDR/码率变化按 V+ 能力处理；需要重连的项在 UI 标记“应用并重连”。

## 7. EasyTier

- 客户端：V+ `EasyTierJNI` / `EasyTierManager` / `EasyTierVpnService`。
- 配置：TOML，`network_name` / `network_secret` / `ipv4` / `listeners` / `peer`。
- Android 默认 `.1`；PC 生成 `.2`，同网络名/密钥。
- 状态：从 `collectNetworkInfos()` 解析虚拟 IP、peer、延迟。
- 安全：密钥默认掩码；不写日志；QR/复制前提示。
- PC 端不自动部署（首发）；用户装 EasyTier、导入配置或手填。

## 8. 线程与生命周期

- 串流/解码：V+ 现有线程模型，不引入主线程阻塞。
- Compose：主线程；状态用 `StateFlow`。
- EasyTier：JNI 调用放后台线程；VPN 权限在主线程。
- 输入事件：主线程；ControllerHandler 内部已有节流。
- 不要在 Composition 中创建 `NvConnection`/EasyTier 实例。
- Activity destroy 时释放 session、EasyTier、display listener。

## 9. 失败与降级

| 失败 | 行为 |
|---|---|
| 下屏不存在/OFF | 单屏回退；上屏显示完整 UI |
| Presentation show 失败 | catch + 重试；回退单屏；不崩溃 |
| 下屏被其他 app 占用 | 提示“下屏被占用”，提供重试/单屏 |
| 手柄未连接 | ModeBar 显示“手柄未连接 · 触屏操作”；触摸可用 |
| EasyTier VPN 权限拒绝 | 提示；本地局域网仍可用 |
| 主机离线 | 主机卡状态 + 重试；保留地址 |
| 串流断线 | 下屏重连面板；上屏只读错误状态 |
| IME 不显示 | 自绘键盘兜底 |

## 10. 测试缝

- `InputRouter` 纯状态机，可 JVM 单测。
- `DisplayCoordinator` 的角色解析纯函数，可单测。
- `EasyTierConfigExporter` 纯字符串生成，可单测。
- `Presentation` 生命周期用 instrumentation/真机。
- 串流用 RP Duo + Foundation；Thor 第二。
- 不把 Compose UI 逻辑塞进 `StreamSessionController`。

## 11. 明确不复用

- V+ `ExternalDisplayManager`（外接视频，不是双屏 UI）
- V+ `GameMenu`（Activity Dialog，不是下屏 Presentation 控制台）
- V+ `PcView` / 应用列表 UI
- UUYC 协议 / 签名 / RTC / 文件传输
- bili-duo 的 B 站页面/素材
- bili-duo 的上下屏交换（LumaDuo 控件固定下屏）

## 12. 迁移顺序

1. 未修改 V+ baseline 构建/串流（M0）
2. 双屏探针（M0.5）
3. 全面改包名 + 品牌化（M1）
4. 新壳：Rail + 主机/应用/远程/设置（M2）
5. 上屏视频 + 下屏串流控制台 + 输入状态机（M3）
6. 配对/EasyTier/单屏/PiP（M4–M5）
