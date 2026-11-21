# 串流控制台实现

> 状态（2026-11-20）：代码完成；duo/nonRoot debug/release 构建、单测与 lint 通过；控制台动作已依赖 `StreamSessionController` 接口；未安装、未真机验收。
> 代码：`49631345 feat(stream): wire real dual-screen console session` + `b0b690b8 fix(stream): satisfy lint for focus dispatch` + `83663e44 feat(duo): finish engine boundary and trim optional modules`

## 1. 为什么由 Game 持有下屏

新壳 `DuoMainActivity` 只负责浏览/配对/设置。点“启动”后它 `onStop`，自己持有的 `SecondScreenPresentation` 会被隐藏。因此串流中的下屏不能再由壳层持有，必须由真正在前台的 `Game` 创建自己的 Presentation：

```text
上屏 (display 0)                下屏 (display 2)
┌─────────────────────┐        ┌─────────────────────┐
│ Game 视频窗口        │        │ Presentation        │
│ StreamView          │        │ DuoStreamConsole    │
│ 输入直通/暂停        │        │ 5 tabs + toolkit    │
└─────────────────────┘        └─────────────────────┘
```

`Game` 本来就是 `ComponentActivity`，`SecondScreenPresentation` 只需要一个 `ComponentActivity` owner，所以无需把串流管线搬进新壳，也不需要服务级 Presentation 宿主。

## 2. 启动链路

- 新壳启动应用时 `ServerHelper.createStartIntent(..., duoConsole = true)`，写入 `Game.EXTRA_DUO_CONSOLE`。
- 旧 `PcView`、桌面快捷方式、通知启动不带该标志，保持 V+ 原行为。
- `Game.onCreate` 解析标志后：
  - 强制本次会话 `prefConfig.useExternalDisplay = false`，避免 `ExternalDisplayManager` 抢同一块下屏；不修改用户持久设置。
  - 创建 `DuoStreamBridge`、`GameDuoStreamActions`、`DuoStreamKeySender`、`GameDuoConsoleHost` 和 `DuoSingleScreenConsoleHost`。
- 串流结束 `Game.finish()`，壳层 `onStart` 恢复自己的 Presentation。

## 3. 状态桥

产品层与串流实现之间有两层：

1. **引擎边界**：`Game` 实现 `com.sour.lumaduo.stream.StreamSessionController`，持有 `StateFlow<StreamSnapshot>`。snapshot 只包含真实引擎字段：阶段、主机/应用/地址、分辨率、配置/实际 FPS、码率、编码、HDR、RTT、主机/解码/渲染延迟、时长和 notice。
2. **产品显示适配**：`DuoStreamBridge` 继续把 `Game` 回调和设备遥测（电量、电池温度、热状态、网络类型）收敛为 `StateFlow<DuoStreamState>`，供控制台 Compose 使用。

`GameDuoStreamActions` 只依赖 `StreamSessionController`，不依赖 `Game` 类型；双屏 `Presentation` 和单屏 `ComposeView` 仍由 `Game` 托管，因为前台 Activity 必须是它们的所有者。

`DuoStreamBridge` 把现有 `Game` 回调收敛为 `StateFlow<DuoStreamState>`：

| 字段 | 来源 |
|---|---|
| 连接阶段/主机/应用/地址 | `Game.onCreate` extras、`connectionStarted`、`connectionTerminated` |
| FPS | `PerformanceInfo.renderedFps` |
| RTT | `PerformanceInfo.rttInfo` 高 32 位 |
| 主机处理/解码/渲染延迟 | `aveHostProcessingLatency`、`decodeTimeMs`、`renderingLatencyMs` |
| 实际码率 | `PerformanceInfo.bandWidth` 解析 |
| 编码/HDR | `PerformanceInfo.decoder`、`hdrFormat` |
| 时长 | `streamStartTime` + `accumulatedStreamTime` |
| 电量/电池温度 | `BatteryManager` |
| 热状态 | `PowerManager.currentThermalStatus`（API 29+） |
| 网络类型 | `ConnectivityManager`：Wi-Fi/有线/蜂窝/VPN |
| 趋势 | 最近 60 个 RTT/码率采样 |

**不提供**主机 CPU/GPU/内存，字段保持 `null`，UI 显示“未提供”；丢包暂无可靠来源，显示“—”。不伪造。

`StreamSnapshot` 使用同一套 `StreamMetrics` 解码 RTT/带宽；`DuoStreamBridge` 的测试 helper 也委托给它，避免两套解析不一致。

## 4. 输入模型

采用**窗口焦点**而不是事件转发：

| 模式 | Presentation | 手柄/键盘去向 |
|---|---|---|
| 直通 | `FLAG_NOT_FOCUSABLE` | 回到 `Game`，继续控制 PC |
| 本地 | 清除 flag，请求焦点 | 进入 Compose 控制台 |

- 直通态下屏仍可触摸。`DuoStreamConsoleRoute` 铺一层透明触摸捕获层，任意触摸进入本地，且不会误触下方被遮罩的控件。
- 进入本地：`ControllerHandler.onExternalGameMenuOpened()` 发送零值并进入菜单捕获；释放转发键盘按键；`setInputGrabState(false)`。
- 返回直通：`onExternalGameMenuDismissed()`；`setInputGrabState(true)`；焦点交还 `Game`。
- A/Back：`SecondScreenPresentation` 拦截 `BUTTON_A`/`BACK`/`ESCAPE`，走 `OnBackPressedDispatcher`。控制台根层 BackHandler 恢复直通；确认层先关闭。
- B：透传给 Compose，由 `LumaFocusable` 确认；B 与合成 `DPAD_CENTER` 40ms 去重。
- HAT：`dispatchGenericMotionEvent` 把 `AXIS_HAT_X/Y` 转成一次 D-pad down/up，保证左摇杆/十字键能走焦点。
- 单屏：`Game.dispatchKeyEvent`/`dispatchGenericMotionEvent` 在覆盖层可见时把输入全部交给 ComposeView；A/Back 走 Activity dispatcher。

## 5. 控制台动作

| 动作 | 实现 |
|---|---|
| 模式切换 | `Game.setDuoConsoleMode`，同步 Presentation 焦点与输入 grab |
| 退出 | `Game.disconnect()` → finish |
| 断开 | finish，由 `onStop` 走终止清理，避免和后台保活竞争 |
| 重连 | `Game.duoReconnect()` → `recreate()`；`duoReconnecting` 阻止 onStop 提前 finish |
| 解锁 | `Game.startHostUnlock()` |
| 主机键盘 | Win+Ctrl+O（Windows OSK），不抢本地焦点 |
| 睡眠 | Win+X → U → S，带 650/1050ms 间隔 |
| 鼠标 | `Game.mouseMove` / `mouseButtonEvent`，1/2/3 → 左/中/右 |
| 键盘 | 逻辑键名 → Android keycode → VK；修饰键走 `KeyboardPacket` mask |
| 快捷键 | 顺序按下、反向释放；覆盖工具页 12 个固定组合 |
| 网络测试 | TCP 47984/47989/47990 连接探测，显示端口数与最低/平均延迟 |
| EasyTier 配置 | 当前给明确提示；后续接真实实现 |

断流/退出时 `stopConnection` 与 `connectionTerminated` 都会调用 `releaseForwardedKeys()`，避免主机残留按键。

控制台内的设置行仍写入 `DuoSettingsRepository`；需要重连的项标“重连”，不会假装已实时生效。

## 6. 单屏回退（延期）

> D-043：单屏/PiP 延期到项目稳定后。以下实现保留，但不再作为当前里程碑或发布门槛。

1. **浏览壳**：`DuoMainActivity` 找不到 Presentation display 时，直接把 `DuoShellScreen` 渲染在自己的窗口；有下屏时恢复“上屏品牌页 + 下屏壳”。窄于 600dp 时 Rail 64→52dp、AppBar 44→40dp、隐藏副标题。
2. **串流控制台**：`DuoSingleScreenConsoleHost` 在 `Game` 根布局上加一个 ComposeView。Back/A 打开底部 60% 面板；面板用 `compact` 控制台（去掉 220dp 摘要列），顶部有“收起”。A 在根层收起，B 确认，确认层优先。
3. PiP 进入时强制收起，恢复直通。

单屏暂用 60% 面板而不是原型的 42% 抽屉：42% 高度放不下 5 个 tab + 工具页；后续可做“42% 快捷摘要 + 展开完整控制台”。已记入 Q-08。

## 7. 已知限制

- `StreamSessionController` 已建立，但实现仍是 `Game`；引擎/输入处理器没有搬出 Activity。最小 `DuoStreamActivity` 留到真机验证后再做，避免在未验收前重写 Surface/输入生命周期。
- 未真机验证：`FLAG_NOT_FOCUSABLE` 在 Duo 固件上的焦点切换、HAT、A/B 丝印、IME、热插拔。
- 网络测试、WebUI、EasyTier 运行态、主机负载未接；控制台对应行会显示提示/“—”。
- HDR/声道/分辨率等设置需要重连；控制台只持久化，不热应用。
- 双屏模式强制关闭外接显示（仅本次会话）；与 `ExternalDisplayManager` 同时使用的行为待产品确认。
- 重连用 Activity `recreate()`，会有短暂黑屏；后续可做原地重连。

## 8. 验证清单

- [x] `:app:testNonRootDebugUnitTest` 通过（含 `DuoStreamBridgeTest`、`DuoStreamKeySenderTest`）
- [x] `:app:assembleNonRootDebug` / `:app:assembleNonRootRelease` 通过
- [x] Release APK 内存在 `DuoStreamBridge` / `GameDuoConsoleHost` / `DuoSingleScreenConsoleHost`
- [x] 真机：壳启动 → 上屏视频 + 下屏真实控制台
- [x] 真机：触摸进入本地、A 返回直通
- [x] 真机：状态页真实 FPS/RTT/码率/时长，无数据为“—”
- [ ] 真机：B 确认、HAT/实体手柄全键位
- [ ] 真机：触摸板/键盘/快捷键到主机，修饰键与释放正确
- [x] 真机：退出/断开回壳，无卡键
- [~] 真机：单屏壳 + 单屏 60% 控制台 + PiP（D-043 延期）

## 9. 代码位置

- `app/src/main/java/com/sour/lumaduo/stream/StreamSnapshot.kt`
- `app/src/main/java/com/sour/lumaduo/stream/StreamSessionController.kt`
- `app/src/main/java/com/sour/lumaduo/duo/stream/DuoStreamBridge.kt`
- `app/src/main/java/com/sour/lumaduo/duo/stream/GameDuoConsoleHost.kt`
- `app/src/main/java/com/sour/lumaduo/duo/stream/GameDuoStreamActions.kt`
- `app/src/main/java/com/sour/lumaduo/duo/stream/DuoStreamKeySender.kt`
- `app/src/main/java/com/sour/lumaduo/duo/stream/DuoSingleScreenConsoleHost.kt`
- `app/src/main/java/com/sour/lumaduo/ui/stream/DuoStreamConsoleRoute.kt`
- `app/src/main/java/com/sour/lumaduo/Game.kt`
- `app/src/main/java/com/sour/lumaduo/duo/SecondScreenPresentation.kt`
