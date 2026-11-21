# 设备矩阵：RP Duo / AYN Thor

> 结论来自 bili-duo 真机文档、Wayfinder/Heimdall 源码与 2026-11 的 Thor 设备报告。
> 2026-11-20 已在 RP Duo 上用 LumaDuo 探针复核显示/焦点/截图路径；Thor 仍未在 LumaDuo 代码上验证，不把 Thor 行为当成保证。

## 1. 显示与宿主

| | Retroid Pocket Duo | AYN Thor（stock Android 13） |
|---|---|---|
| 上屏 display | 0 | 0 |
| 下屏 display | 2（不可写死） | 4（测试固件；不可写死） |
| 上屏分辨率 | 1920×1080 | 1920×1080 |
| 下屏分辨率 | 1280×960 | 1240×1080 |
| 下屏 dp | 640×480（4:3） | 约 538×469（31:27） |
| 下屏 density | 320dpi | 369dpi |
| 下屏 flags | `FLAG_PRESENTATION` | `FLAG_PRESENTATION`（部分 ROM 上屏也有） |
| `canHostTasks` | **false** | 未捕获字面值；**可承载 Activity/task** |
| Activity 放上屏 | 正常 | 正常 |
| Activity 放下屏 | 失败/落错屏 | `setLaunchDisplayId` 可用 |
| Presentation | 必须 | 可用，但不是唯一选择 |
| 下屏 launcher | AOSP SecondaryDisplayLauncher | AYN CocoonShell / SECONDARY_HOME |
| 触摸 | INTERNAL（Android 16 实测；旧资料写 EXTERNAL） | 未验证 |
| 下屏系统栏 | 无 | 可能无；部分应用量到 1240×969 可用区 |

> 2026-11-20 LumaDuo 在 RP Duo（Android 16，product `sun`）实测：
> `DisplayDeviceInfo` 显示上屏 `local:<上屏 unique id>`、下屏
> `local:<下屏 unique id>`；下屏 1280×960 @320dpi、`FLAG_PRESENTATION`、
> `canHostTasks=false`；空载时下屏 `state OFF`，`Presentation.show()` 后恢复并取得焦点。
> 两屏的 `touch` 字段均为 `INTERNAL`，不再沿用旧文档的 `EXTERNAL` 结论。

### 结论

- **Presentation 是共同分母**：Duo 必须用，Thor 也可用。LumaDuo 默认上屏 Activity + 下屏 Presentation。
- Thor 上可做第二 Activity fallback，但不作为 MVP 默认；只有 Presentation 焦点/IME 实测失败时再启用。
- 不写死 displayId；用 `DEFAULT_DISPLAY` 排除 + `FLAG_PRESENTATION` + 非 OFF 查找。
- LineageOS 24 可能两块屏都带 `FLAG_PRESENTATION`；必须排除默认屏。

## 2. 下屏查找

```kotlin
fun findSecondaryDisplay(activity: Activity): Display? {
    val dm = activity.getSystemService(DisplayManager::class.java)
    val own = activity.display?.displayId ?: Display.DEFAULT_DISPLAY
    return dm.displays.firstOrNull { d ->
        d.displayId != Display.DEFAULT_DISPLAY &&
        d.displayId != own &&
        d.state != Display.STATE_OFF &&
        (d.flags and Display.FLAG_PRESENTATION) != 0
    }
}
```

Thor 可加 `d.name.contains("Screen-2")` 或 1240×1080 作为 tie-breaker，但不能作为唯一条件。

## 3. 从下屏 launcher 启动：re-home

Duo 的 SecondaryDisplayLauncher 和 Thor 的 CocoonShell 都可能把 Activity 启动到第二屏。双屏布局要求 Activity 在默认屏。

```kotlin
if (currentDisplayId() != Display.DEFAULT_DISPLAY) {
    val options = ActivityOptions.makeBasic()
        .setLaunchDisplayId(Display.DEFAULT_DISPLAY)
        .toBundle()
    startActivity(
        Intent(intent).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            putExtra(EXTRA_REHOMED, true)
        },
        options,
    )
    finish()
    return
}
```

配套：

- `launchMode="singleTop"`；不要 `singleTask`（会把 re-home 发回原实例）。
- 用 `EXTRA_REHOMED` 防循环。
- `onStart/onStop/onDestroy` 保护未初始化的 `secondScreen`。
- `SecondScreenController.findSecondaryDisplay()` 排除 Activity 自身所在屏。
- 如果设备忽略 launch display，回退单屏，不无限重启。

## 4. Presentation 生命周期

- `Activity.onStart` → `show()`
- `Activity.onStop` → `hide()`，不 `dismiss()`
- `Activity.onDestroy` → `dismiss()` + 注销 DisplayListener
- Display add/remove/change → 重试 show；300ms debounce
- 同一 display 只允许一个 Presentation
- catch `WindowManager.InvalidDisplayException` / `BadTokenException`

`Presentation` 必须提供：

- `LifecycleOwner`
- `ViewModelStoreOwner`
- `SavedStateRegistryOwner`
- `ActivityResultRegistryOwner`
- `OnBackPressedDispatcherOwner`

禁止在里面用 `Dialog` / `AlertDialog` / `ModalBottomSheet`；全部用 Compose overlay。

## 5. 焦点与输入

- Android 焦点按 display 走。
- Duo：手柄默认先到上屏 Activity；需要转发给下屏。
- Thor：触摸下屏可能把焦点移到下屏；Presentation 若可聚焦会拿到 key focus。
- 因此 LumaDuo 用**进程级 InputRouter**，Activity 和 Presentation 都可以喂事件；按来源 + 时间戳去重。
- 上屏视频窗口 `focusable=false`、`clickable=false`，绝不抢焦点。
- 下屏需要手柄导航时，Presentation 可聚焦；直通时由 V+ ControllerHandler 消费。
- Return/Back：下屏 Presentation 持有焦点时，Activity 收不到；由 Presentation 自己接 `dispatchKeyEvent` + `OnBackInvokedCallback`，250ms 去抖。

## 6. IME

- RP Duo 系统 IME 可在下屏显示，但冷启动第一次可能被拒绝；bili-duo 用 300/600ms 重试。
- MuMu 虚拟下屏不弹系统 IME。
- Thor 未验证；跨屏 IME 有已知绑定错屏报告。
- `WindowInsets.isImeVisible` 在 Presentation 会误报；必须自维护 `ImeState.shown`。
- LumaDuo 以自绘数字/ASCII 键盘为主，系统 IME 只做触摸补充。

## 7. 布局断点

| 断点 | 用途 |
|---|---|
| 640×480dp | RP Duo 下屏基准；4:3 |
| ~538×469dp | Thor 下屏；更窄，rail 仍 64dp，内容区约 450dp |
| 832×468dp | 单屏上屏 |
| ~538×469dp 单屏 | Thor 下屏作为唯一宿主 |

规则：

- 内容宽度 < 560dp：主机列表从 2 列改单列；应用网格 3 列改 2 列；详情改为覆盖层。
- 下屏 rail 64dp 固定；ModeBar 28dp 固定；AppBar 44dp 固定。
- 不用 1280×960 像素写死；全部 dp。
- 焦点环外扩 5.75dp，列表留 7dp 透明余量。

## 8. 亮度、常亮与画中画

### 8.1 亮度

- 优先使用 `WindowManager.LayoutParams.screenBrightness`：
  - 上屏视频窗口与下屏 UI 窗口分别设置；
  - `0` 表示系统默认，业务值限制在 `0.05–1.0`；
  - 不申请 `WRITE_SETTINGS`。
- 若真机证明窗口级亮度不能按 display 独立生效，回退到全局
  `Settings.System.SCREEN_BRIGHTNESS`，并在设置里标注“全局亮度”。
- 上/下屏亮度分别持久化；浏览态只主动控制下屏，上屏 idle 不抢系统亮度。
- 不写死百分比到像素；亮度值只做窗口参数。

### 8.2 串流常亮

- 串流期间给上屏视频窗口和下屏 UI 窗口加 `FLAG_KEEP_SCREEN_ON`。
- 暂停、断流、退出、Activity 销毁时清除；不持有 `PARTIAL_WAKE_LOCK`。
- Presentation 重建后重新加 flag；不要依赖 Activity 单窗口。
- 验收：串流 10 分钟不熄屏；退出后恢复系统息屏策略。

### 8.3 画中画（P1）

- 单屏模式允许 Stream Activity 进入 PiP：只显示视频，无 OSD/按钮/焦点。
- 音频继续；返回应用恢复原 Surface，不重建连接。
- 双屏 PiP 行为未验证：系统可能只保留一个 display 的窗口。
- 首发只承诺单屏 PiP；双屏 PiP 在 M5 真机确认后再承诺。

## 9. 真机验证命令

```bash
adb shell dumpsys display | grep -iE "displayId|mDisplayId|FLAG_PRESENTATION|canHostTasks|Screen-2"
adb shell dumpsys window displays | grep -iE "displayId|canHostTasks|mDisplayId"
adb shell wm size
adb shell wm density
adb shell input -d 0 keyevent 4   # 上屏 Back
adb shell input -d 2 keyevent 4   # Duo 下屏 Back（Thor 用 4）
```

M0.5 必须记录：

- 下屏 displayId、flags、`canHostTasks`
- Presentation 是否拿到焦点
- 手柄按键落到哪个窗口
- Return/Back 是否被正确接收
- 系统 IME 是否在下屏显示
- 下屏可用区是否有系统栏

## 10. 未确认

- Thor `canHostTasks` 的字面值（行为已证明可承载 task）。
- Thor 各固件/LineageOS 的 Presentation 行为。
- Thor 系统 IME 在 Presentation 的实际表现。
- Duo/Thor 是否有摄像头（影响 QR 配对入口）。
- 外接 HDMI/底座时 display id 变化。
- 窗口级亮度能否在 Duo/Thor 两个 display 上独立生效。
- 双屏 PiP 是否只保留一个 display，以及返回后的焦点/窗口恢复。
