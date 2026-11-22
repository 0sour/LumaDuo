# RP Duo 平台开发参考

> 适用：Retroid Pocket Duo（Android 16）上的 LumaDuo 双屏、输入与串流开发。
>
> 来源：bili-duo 在 RP Duo 的实测资料（2026-11-13～11-22）＋ LumaDuo 在本机的复核
> （2026-11-22）。本文不包含账号、主机地址、密钥、二维码内容或设备截图。
>
> 标记：
>
> - **✅ 已复核**：LumaDuo 已在同一台 RP Duo 上验证。
> - **📋 待复核**：来自 bili-duo 实测，LumaDuo 尚未单独验证。
> - **⚠️ 现状不符**：LumaDuo 当前实现与该结论不一致，需要修。
> - **🚫 不适用**：LumaDuo 明确不做或已有不同方案。

## 1. 显示事实

| 项目 | 上屏 | 下屏 |
|---|---|---|
| 逻辑 displayId | `Display.DEFAULT_DISPLAY` = 0 | 通常 2，但**不要硬编码** |
| 原生面板 | 1080×1920 竖屏 | 1280×960 |
| 系统横屏尺寸 | 1920×1080 | 1280×960 |
| 密度 | 369 dpi（约 2.30625） | 320 dpi（2.0） |
| dp 尺寸 | 约 832×468dp | 640×480dp |
| flags | `canHostTasks=true` | `FLAG_PRESENTATION`、`canHostTasks=false` |
| 刷新率 | 120Hz / 60Hz | 120Hz / 60Hz |
| HDR | HDR10/HLG（type 2/3），max luminance 约 420 | 同上 |
| 触摸 | 不作为主操作面 | 有触摸，主要操作面 |

**✅ 已复核（2026-11-22）**

```text
Display 0: 1920x1080, density 369, canHostTasks=true
Display 2: 1280x960, density 320, FLAG_PRESENTATION, canHostTasks=false
两块屏都有 120Hz/60Hz mode，HDR type 2/3
```

注意：

- `getRealMetrics()` / `WindowMetrics` 拿到的是旋转后的 1920×1080，不要按 1080×1920 算布局。
- 下屏是 `FLAG_PRESENTATION`，普通 Activity 不适合常驻；用 `Presentation` 承载 UI。
- 两块屏都可能进入 `STATE_OFF`。查找下屏时**必须过滤 `state == STATE_OFF`**，否则合盖/休眠
  时会选中一块不可用的屏，`show()` 失败。
- 下屏 `DisplayDeviceInfo.touch` 在不同固件可能是 `INTERNAL` 或 `EXTERNAL`，不要用它判断触摸归属；
  以 `input -d 2 tap` 和实际触摸为准。

### 1.1 取屏尺寸

```kotlin
val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
val metrics = DisplayMetrics()
@Suppress("DEPRECATION")
display.getRealMetrics(metrics)
// 上屏：1920x1080，density 2.30625
// 下屏：1280x960，density 2.0
```

`WindowMetrics`（API 30+）更推荐；但 Presentation 的窗口尺寸可能比 display real size 小
（系统栏/挖孔），布局要以实际窗口为准。

### 1.2 截图

Duo 上 `screencap -d 0` / `-d 2` 可能报 `Display Id '0' is not valid`。必须用
SurfaceFlinger 的物理 display id：

```bash
adb shell dumpsys SurfaceFlinger --display-id
# Display <SF_TOP_ID> ... 上屏
# Display <SF_BOTTOM_ID> ... 下屏

adb exec-out screencap -p -d <SF_TOP_ID> > top.png
adb exec-out screencap -p -d <SF_BOTTOM_ID> > bottom.png
```

- 这些 id 每次开机可能变化，不要硬编码。
- 不指定 `-d` 时多屏设备会打印 `[Warning] Multiple displays...`，可能污染 PNG 头。
- 设备休眠很快；截图前先 `adb shell input keyevent 224`，必要时 `wm dismiss-keyguard`。

## 2. 窗口架构

### 2.1 Activity + Presentation

推荐：

- **Activity 在 Display 0**：网络、解码、音频、输入路由、`SurfaceView`。
- **下屏用 `Presentation`**：触控 UI、状态面板、工具栏。
- 视频-only 的 Presentation 加 `FLAG_NOT_FOCUSABLE`（必要时 `FLAG_NOT_TOUCHABLE`），避免抢手柄焦点。

`Presentation` 没有 Activity 的生命周期和各类 owner，必须补齐：

- `LifecycleOwner`
- `ViewModelStoreOwner`
- `SavedStateRegistryOwner`
- `ActivityResultRegistryOwner`（复用 Activity registry）
- `OnBackPressedDispatcherOwner`

**✅ LumaDuo 已实现**：`SecondScreenPresentation` 补齐上述 owner，`ActivityResultRegistry` 委托给
宿主 Activity；Compose 使用 Presentation 的 display-scoped context（320dpi → 640×480dp）。

### 2.2 不要在 Presentation 里用系统 Dialog

`Dialog` / `AlertDialog` / `Popup` / `ModalBottomSheet` 会创建新 window，常见结果：

- `BadTokenException` / window type mismatch；
- 弹窗跑到默认屏；
- 焦点被系统窗口抢走，Presentation 收不到返回键。

**✅ LumaDuo 已遵守**：下屏 overlay 全部用同窗口 Compose scrim + 面板，没有使用
`Dialog`/`AlertDialog`/`ModalBottomSheet`。

### 2.3 从下屏 launcher 启动：re-home 到 Display 0

RP Duo 的下屏 launcher 会把 Activity 直接启动在 Display 2。双屏应用需要把 Activity 送回
Display 0，否则会：

- Activity 在下屏渲染；
- `findPresentationDisplay()` 找不到可用副屏；
- 上屏停在桌面。

```kotlin
val relaunch = Intent(intent).apply {
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
    putExtra(EXTRA_REHOMED, true)
}
val options = ActivityOptions.makeBasic()
    .setLaunchDisplayId(Display.DEFAULT_DISPLAY)
    .toBundle()
startActivity(relaunch, options)
finish()
```

坑：

- `launchMode` 用 **`singleTop`**，不要 `singleTask`；`singleTask` 会把 re-home intent 发回原实例，
  Display 0 不会出现新任务。
- 复制原 Intent，保留启动参数（如 `display_control`）。
- 加 `EXTRA_REHOMED` 防止设备忽略 launch display 时无限循环。
- `findPresentationDisplay()` 同时排除 Activity 自身 display，作为双保险。
- re-home 提前 `return` 后，不要碰尚未初始化的 Presentation 字段。

**✅ 已修（2026-11-22）**：`DuoMainActivity` 已加 `singleTop` 和 re-home；用
`am start --display 2` 启动后实测会回到 Display 0，上屏显示品牌页、下屏显示 Presentation。

### 2.4 找下屏

```kotlin
fun findPresentationDisplay(context: Context, excludeDisplayId: Int): Display? {
    val manager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        ?: return null
    return manager.displays
        .asSequence()
        .filter { display ->
            display.displayId != excludeDisplayId &&
                display.isValid &&
                display.state != Display.STATE_OFF &&
                (display.flags and Display.FLAG_PRESENTATION) != 0
        }
        .sortedBy { it.displayId }
        .firstOrNull()
}
```

**✅ 已修（2026-11-22）**：`findPresentationDisplay()` 已加 `display.state != Display.STATE_OFF`。
合盖/休眠的物理场景仍需单独验收。

### 2.5 Display 监听与常亮

- 注册 `DisplayManager.DisplayListener`：added / removed / changed。
- added：前台且没有 Presentation 时尝试 `show()`。
- removed：如果移除的是 Presentation 的 display，`dismiss()` 并回退。
- changed：从 OFF 恢复时重建/显示。
- `show()` 捕获 `InvalidDisplayException` / `BadTokenException`，丢弃旧实例并延迟重试，不要无限循环。
- 两屏都加 `FLAG_KEEP_SCREEN_ON`，不要用 wake lock。

**✅ LumaDuo 已实现** DisplayListener、`refreshDisplays()`、异常重试和两窗口
`FLAG_KEEP_SCREEN_ON`；**⚠️ 仍需真机验证合盖/开盖、下屏 OFF→ON、拔插。**

## 3. 输入所有权

### 3.1 系统事实

未 root 的 Duo **不能像 Wayfinder 那样把系统手柄焦点切到下屏**。手柄事件先到上屏
Activity；下屏 Presentation 只有在获得焦点时才收到自己的按键。

因此不能假设“下屏 UI 一定能收到手柄”。正确做法是**统一输入路由**：

```text
Activity.dispatchKeyEvent
        ↓
   InputRouter（去重 / 风格归一化 / 模式判断）
        ├─ 串流输入 → 发送到主机
        └─ 下屏 UI → presentation.dispatchKeyEvent(event) / decorView.dispatchKeyEvent(event)

Presentation.dispatchKeyEvent
        ├─ BACK / ESC → 同一个 back 流程
        └─ 其他键（下屏有焦点时）→ 同一个 InputRouter
```

要点：

- Activity 和 Presentation 两条路径都进同一个 router。
- 约 40ms 去重，消除双路径和固件重复 DOWN。
- UI 导航吞 `repeatCount > 0`；串流可按需转发 repeat。
- `KEYCODE_BUTTON_A/B` 不会自动触发 Compose `clickable`；需要合成 `DPAD_CENTER` 或在焦点组件
  的 `onKeyEvent` 里处理。
- Presentation 自己处理 `BACK`/`ESCAPE`，并与 `OnBackInvokedCallback` 走同一逻辑 + 约 250ms 去抖。
- 返回优先级：系统 IME → Compose overlay/页内拦截 → 页面返回 → 根页退出。

**✅ 已修（2026-11-22）**：`DuoMainActivity.dispatchKeyEvent()` 现在先把按键转发给
`SecondScreenPresentation`，由 Presentation 的 Compose 焦点树/back dispatcher 消费；未消费时再走
Activity 的单屏 fallback。实测（外接 Xbox 手柄）：

- 实体 D-pad Down：焦点从「主机」移动到「应用」。
- 实体 B：进入应用页（确认）。
- 实体 A：从应用页返回主机页（返回）。

修复前的现象（记录用于回归）：Activity 不转发时，实体 D-pad 不动下屏焦点，实体 B 直接退出到
桌面；虚拟 `input -d 2 keyevent 20` 可移动焦点。**这不是系统重映射，而是缺少输入转发。**

### 3.2 不要无差别消费 HAT 轴

- 不要在 Activity 层全局拦截 `dispatchGenericMotionEvent`；否则 D-pad 的
  `AXIS_HAT_X/AXIS_HAT_Y` 会被吞，左侧方向键失灵。
- 只有当前 UI 明确需要摇杆时才消费 `AXIS_X/AXIS_Y/Z/RZ`。
- HAT 轴放行，或者只在明确需要“摇杆模拟 D-pad”的窗口里转换。

**✅ LumaDuo Activity 没有拦截 generic motion**；`SecondScreenPresentation` 会在下屏把 HAT
转换成 DPAD 键，供 Compose 焦点树使用。后者需要在真机上确认不会与实体 D-pad 冲突。

### 3.3 外接 Xbox 手柄实测记录

2026-11-22 在转发修复前观察到：注入 `BTN_EAST`（B）后应用退到桌面，注入
`BTN_DPAD_DOWN` 后下屏焦点不移动；`dumpsys input` 的 `KeyGestureController` 还出现过设备 10 的
`keycodes=[4]`（BACK）/`[3]`（HOME）。

修复 Activity → Presentation 转发后，同一个 Xbox 手柄的 D-pad、下确认、右返回都能正常操作下屏。
因此**不能再把之前的退桌面归因于“系统重映射”**；真正原因是事件落在 Activity、而应用没有转发。
`KeyGestureController` 的记录可能来自其他按键，不能作为 A/B 被系统消费的证据。

随后在系统「手柄测试」中捕获到 Xbox 风格原始键码：下=304/BUTTON_A、右=305/BUTTON_B、
左=307/BUTTON_X、上=308/BUTTON_Y；LumaDuo 已按此修正 A/B 拦截。Retro 风格仍需单独捕获。

## 4. ABXY / 手柄风格

RP Duo 系统设置里有“手柄风格”（Xbox / Retro），会改变同一物理按键发出的 keycode。
**物理位置功能固定**：

- 下 = 确认
- 右 = 返回
- 左 = 刷新/动作
- 上 = 不绑定

bili-duo 实测映射：

| 物理位置 | Xbox 印字 | Retro 印字 | canonical | Xbox 原始 keycode | Retro 原始 keycode |
|---|---|---|---|---|---|
| 下 · 确认 | B | A | `BUTTON_A` (96) | 96 | 97 |
| 右 · 返回 | A | B | `BUTTON_B` (97) | 97 | 96 |
| 左 · 动作 | Y | X | `BUTTON_X` (99) | 99 | 100 |
| 上 · 未绑定 | X | Y | `BUTTON_Y` (100) | 100 | 99 |

规则：

- **Xbox 风格**：固件已经翻转过，原始 keycode 就是 canonical，**不要再做 A↔B / X↔Y**。
- **Retro 风格**：需要归一化 A↔B、X↔Y；其余键不动。
- 串流时最好按物理位置映射到 host 的 XInput 布局，而不是直接转发当前风格 keycode。
- 屏幕提示不要写死 A/B/X/Y，按当前风格显示。
- `KEYCODE_BUTTON_X` (99) ≠ 键盘 `KEYCODE_X` (52)；`BUTTON_A` (96) ≠ `KEYCODE_A` (29)。

**本机设置记录（2026-11-22）**：`settings get system flip_button_layout` = `1`；
`temp_abxy_layout_mode` = `0`。RPSettings 的 `array_controller_style` 为 `Xbox / Retro / None`；
RPSettings 的 `ControllerStylePreference` 映射为：

- style 0 → `flip_button_layout=1`、`temp_abxy_layout_mode=0`（Xbox）
- style 1 → `flip_button_layout=0`、`temp_abxy_layout_mode=1`（Retro）
- style 2 → `temp_abxy_layout_mode=2`、`no_create_gamepad_button_layout=1`（None）

因此本机当前是 **Xbox 风格**。

**本机原始捕获（2026-11-22，event8）**：

| 物理按键 | Linux 事件 | Android keycode |
|---|---|---|
| 下 · 确认 | `BTN_GAMEPAD` | `BUTTON_A` (96) |
| 右 · 返回 | `BTN_EAST` | `BUTTON_B` (97) |
| 左 · 动作 | `BTN_NORTH` | `BUTTON_X` (99) |
| 上 · 未绑定 | `BTN_WEST` | `BUTTON_Y` (100) |

**✅ 已修（Xbox 风格）**：`DuoKeys.Confirm = Key.ButtonA` (96)，`Back = Key.ButtonB` (97)；
Activity、Presentation、单屏控制台的 A/B 拦截和 40ms 确认去重也同步交换。实测下键确认、
右键返回、D-pad 移动焦点均正常。UI 标签保持“B 选择 / A 退出”，与 Xbox 风格印字一致。

**📋 Retro 风格待处理**：Retro 的原始码应为下=97、右=96、左=100、上=99。当前代码未做
风格自动归一化；切到 Retro 后 A/B/X/Y 可能反向。下一步需要在 Retro 风格下用同一捕获流程确认，
再决定读取 `flip_button_layout` 做自动交换，还是提供手动布局设置。

### 4.1 常用 keycode

```text
BUTTON_A=96   BUTTON_B=97   BUTTON_X=99   BUTTON_Y=100
BUTTON_L1=102 BUTTON_R1=103 BUTTON_L2=104 BUTTON_R2=105
BUTTON_THUMBL=106 BUTTON_THUMBR=107
BUTTON_START=108 BUTTON_SELECT=109
DPAD_UP=19 DOWN=20 LEFT=21 RIGHT=22 CENTER=23
BACK=4 ESCAPE=111 MENU=82
```

### 4.2 测试方式

```bash
# 走默认 Display 0 / Activity 路径
adb shell input -d 0 keyevent 96
adb shell input -d 0 keyevent 97
adb shell input -d 0 keyevent 99

# 直接测下屏 Presentation（绕过 Activity 路由）
adb shell input -d 2 tap 640 480
adb shell input -d 2 keyevent 20
adb shell input -d 2 keyevent 4
```

`input keyevent` 不能可靠模拟长按 repeat；真实长按请用实体手柄。`input -d 2` 可能改变焦点或
让应用退到后台，不能单独作为手柄验收。

## 5. 摇杆、扳机、D-pad

- 左摇杆通常 `AXIS_X/AXIS_Y`；右摇杆 `AXIS_Z/AXIS_RZ`；扳机可能是
  `AXIS_LTRIGGER/AXIS_RTRIGGER`，也可能是 L2/R2 按键。
- 用 `InputDevice.getMotionRange(axis, source)` 取 `min/max/flat/resolution`，做死区和归一化，
  不要假设 -1..1。
- D-pad 可能以 `KEYCODE_DPAD_*` 或 `AXIS_HAT_X/Y` 出现，两种都要处理。
- 手柄按键默认会 repeat；UI 导航吞 repeat，串流按需转发。
- 摇杆按 `onGenericMotionEvent` 变化采样，不要主线程高频轮询。

## 6. 音频 / 视频

### 6.1 音频

**✅ 已复核（2026-11-22）**：本机 vendor codec XML 中没有 AC3 / E-AC3；存在
AAC（`audio/mp4a-latm`）、Opus、FLAC、raw。也就是说 Duo 不能依赖 AC3/EAC3 硬解。

建议：

- 串流音频优先 AAC / Opus / PCM；杜比在 host 或软件层转码。
- 低延迟用 `AudioTrack`：

```kotlin
AudioTrack.Builder()
    .setAudioAttributes(
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
            .build()
    )
    .setAudioFormat(
        AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(48000)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()
    )
    .setBufferSizeInBytes(minBuffer)
    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
    .build()
```

- 蓝牙耳机/扬声器切换、音画同步、麦克风实际听感仍需真机测试。

### 6.2 视频

- 上屏物理 1080p；4K 只会缩放，收益主要是高码率，代价是解码/带宽。默认 **1080p60 /
  H.264 或 HEVC**，先保证延迟。
- HDR10/HLG 面板支持不等于应用通路支持；4K/HDR、Dolby Vision、tone-map 要单独验证。
- 用 `SurfaceView` + `MediaCodec` 解到 Surface，不要用 ExoPlayer 默认缓冲。
- `Surface.setFrameRate(fps, FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)`（API 30+）匹配流帧率。
- 60fps 优先 60Hz；120Hz 可用于高帧率/降低运动模糊。
- 低延迟不要自己 sleep，用 `releaseOutputBuffer(index, presentationTimeUs, true)`。

**✅ LumaDuo 使用 V+ 的 MediaCodec + SurfaceView 视频链路；音频实际解码/延迟尚未逐项验收。**

### 6.3 电源与亮度

- 两屏都 `FLAG_KEEP_SCREEN_ON`。
- 合盖/休眠：`DisplayManager` + `Activity.onStop` 暂停解码/音频，恢复时重建 Surface。
- 按窗口设置亮度（`WindowManager.LayoutParams.screenBrightness`）是 P1，未实现前不要假装支持。

## 7. 双屏串流拓扑

默认（LumaDuo 当前方向）：

- **上屏视频**：Activity Display 0，`SurfaceView` + `MediaCodec`。
- **下屏 UI**：Presentation Display 2，控制台/工具/状态。
- 手柄在 Activity 侧统一捕获，一份发 host，一份给下屏 UI（需要转发）。

备选：

- 上屏 UI + 下屏视频：Activity 承载 Compose，Presentation 只放 SurfaceView，加
  `FLAG_NOT_FOCUSABLE`（必要时 `FLAG_NOT_TOUCHABLE`），输入仍由 Activity router 统一处理。

延迟测量：

```bash
adb shell dumpsys SurfaceFlinger --latency <layer>
adb shell dumpsys gfxinfo <package>
adb shell dumpsys media.codec
adb logcat -s MediaCodec MediaCodecRenderer
```

端到端延迟以高速相机拍“手柄按下 → 屏幕反应”为准，不要只信代码时间戳。

## 8. 调试命令

```bash
# 设备
adb devices -l
adb mdns services
adb connect <ip>:<port>

# 无线 serial 可能带空格/括号，必须引号
adb -s "<duo-serial>" shell ...

# 显示
adb shell dumpsys display | grep -E "DisplayDeviceInfo|mDisplayId|canHostTasks|FLAG_PRESENTATION|state|rotation|density"
adb shell dumpsys window displays | grep -E "Display:|mCurrentFocus|mFocusedApp"
adb shell dumpsys activity activities | grep -E "Display|ResumedActivity"

# 截图（物理 id）
adb shell dumpsys SurfaceFlinger --display-id
adb exec-out screencap -p -d <SF_ID> > screen.png

# 输入
adb shell dumpsys input | grep -E "Device|Sources|Keyboard|Gamepad"
adb shell getevent -pl
adb shell getevent -l
adb shell input -d 2 tap 640 480
adb shell input -d 2 keyevent 20

# 日志
adb logcat -d -t 300 | grep -E "FATAL EXCEPTION|ANR|AndroidRuntime"
adb logcat -d -t 500 | grep -iE "MediaCodec|ACodec|CCodec|Surface|Display"

# 网络
adb shell ping -c 1 <host>
adb shell curl -sS -o /dev/null -w "%{http_code} %{http_version}\n" https://<host>/
adb shell dumpsys connectivity | grep -iE "VPN|Proxy"
```

## 9. LumaDuo 复核结果（2026-11-22）

| 项目 | 结果 | 证据 / 动作 |
|---|---|---|
| 上/下屏尺寸、密度、flags | ✅ | `dumpsys display`：1920×1080@369 / 1280×960@320，下屏 `FLAG_PRESENTATION`、`canHostTasks=false` |
| 截图物理 display id | ✅ | `screencap -d <SF_TOP_ID>` / `<SF_BOTTOM_ID>` 可用 |
| Presentation owner 补齐 | ✅ | `SecondScreenPresentation` 实现五个 owner，Compose 用 display context |
| Presentation 内禁用系统 Dialog | ✅ | 壳层/控制台无 `Dialog`/`AlertDialog`/`ModalBottomSheet` |
| 不依赖 `WindowInsets.isImeVisible` | ✅ | 壳层未使用该 API，自行维护输入面板状态 |
| 下屏查找排除自身 display | ✅ | `findPresentationDisplay(context, ownDisplayId())` |
| 下屏查找过滤 `STATE_OFF` | ✅（代码） | 已加 `display.state != Display.STATE_OFF`；合盖物理场景待验收 |
| 从下屏 launcher re-home | ✅ | `singleTop` + `setLaunchDisplayId(0)`；`am start --display 2` 实测回到 Display 0 |
| Activity → Presentation 输入转发 | ✅ | 实体 D-pad、下确认、右返回均实测通过 |
| ABXY 风格归一化 | ✅ Xbox / 📋 Retro | Xbox：下=96 确认、右=97 返回已实测；Retro 需 A↔B、X↔Y 归一化 |
| HAT 轴处理 | ✅/📋 | Activity 不拦截；Presentation 将 HAT 转 DPAD，需真机确认不与实体 D-pad 冲突 |
| AC3/E-AC3 解码 | ✅（不存在） | vendor codec XML 无 AC3/EAC3；AAC/Opus/FLAC/raw 存在 |
| 两屏 `KEEP_SCREEN_ON` | ✅ | Activity 与 Presentation 都设置 |
| 窗口亮度独立控制 | ⚠️ | 未实现，P1 |
| 实体内置手柄 A/B/X/Y | ✅ Xbox / 📋 Retro | 2026-11-22 event8 捕获：下=304、右=305、左=307、上=308；Xbox 已通过 |
| 键盘/快捷键到 host | 📋 | 代码路径已接，未在主机侧验证 |
| EasyTier 端到端 | 📋 | UI/状态已接，未做真实隧道串流 |
| 30 分钟长时/断流重连/音频听感 | 📋 | 未验收 |

## 10. 相关文档

- [DEVICE-MATRIX.md](DEVICE-MATRIX.md) — RP Duo / Thor 差异
- [INPUT-AND-FOCUS.md](INPUT-AND-FOCUS.md) — 输入状态机与焦点
- [ui/DUO-PITFALLS.md](ui/DUO-PITFALLS.md) — 双屏/焦点/IME 踩坑
- [STREAM-CONSOLE.md](STREAM-CONSOLE.md) — 控制台与输入模式
- [TESTING.md](TESTING.md) — 真机验收用例
