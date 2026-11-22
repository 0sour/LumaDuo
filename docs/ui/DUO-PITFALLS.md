# 双屏掌机开发踩坑与注意事项

> 来源：`bili-duo` 2026-11-13～11-18 开发会话、`UUYC-Duo` 2026-11-20 开发会话，以及两项目的
> `DUO_DUAL_SCREEN_GUIDE.md`、`INPUT_AND_FOCUS.md`、`BUILDING.md`、`feature-matrix.md`。
>
> 本文只记录可复用的技术结论，不包含账号 UID、设备地址、网络密钥、二维码内容或截图中的个人信息。
> 适用于 LumaDuo，也适用于后续任何 RP Duo / AYN Thor 双屏应用。
>
> **2026-11-22 复核补充**：新增 [RP-DUO-DEV.md](../RP-DUO-DEV.md)，包含更完整的显示/窗口/输入/
> ABXY 风格/音视频/调试资料与 LumaDuo 同机复核表。以下三点是本次最重要的修正：
>
> 1. **ABXY 风格会改原始 keycode**。物理位置固定为「下确认、右返回、左动作、上未绑定」，但
>    Xbox/Retro 两种风格的 `BUTTON_A/B/X/Y` 会互换。当前 LumaDuo `DuoKeys` 硬编码
>    `ButtonB` 确认 / `ButtonA` 返回，未做风格归一化；Xbox 风格下可能整体反向。外接 Xbox 手柄
>    已实测 B/A 正常，内置手柄仍需实体测试。
> 2. **实体手柄事件不一定先到 Presentation**。未 root 时事件可能先到上屏 Activity；必须有
>    Activity → Presentation 的转发/统一 router。**已修（2026-11-22）**：实体 D-pad/B/A 实测可用。
> 3. **找下屏要过滤 `STATE_OFF`**，并补下屏 launcher 启动时的 re-home（`singleTop` +
>    `setLaunchDisplayId(0)`）。**已修（2026-11-22）**：`am start --display 2` 已验证 re-home。

## 0. 先记住八条

1. **上屏只读，下屏是唯一操作面。** 不要做“上下屏都能操作、靠一个切屏键切焦点”的双焦点树。
2. **一个输入所有者，一棵焦点树。** 任意时刻只能有一个窗口/页面消费手柄和返回键。
3. **下屏不是 Activity，是 `Presentation`。** 它没有 Activity 的生命周期和各类 owner，必须自己补齐。
4. **不要硬编码 Display 0/2。** 用 `FLAG_PRESENTATION` 查找，并排除 Activity 自己所在的屏。
5. **实体键语义必须按设备丝印。** Duo 上 bili-duo / UUYC 都锁定为“实体 B 确认、实体 A 返回”，
   与 Android 常见的 A 确认/B 返回相反；LumaDuo 新壳层已按此实现，legacy V+ 页面仍是 A 确认，
   需真机确认丝印后决定是否替换/隔离 legacy 入口。
6. **焦点环是功能，不是装饰。** 页面一进入就必须有可见焦点环；滚动列表要预留透明余量，否则会被裁。
7. **`Presentation` 里所有模态都用 Compose overlay。** 不要用 `Dialog`/`AlertDialog`/`Popup`/`ModalBottomSheet`。
8. **改了 UI/协议后“代码没生效”，先 clean 重建并检查 APK。** Windows + 增量构建出现过旧 dex 被复用。

---

## 1. 显示宿主与双屏生命周期

### 1.1 下屏只能用 Presentation

RP Duo 下屏 `canHostTasks=false`，不能直接 `am start --display 2`，也不能把 Activity 搬过去。
正确路径：

- Activity 在默认屏启动；
- 通过 `DisplayManager` 找 `FLAG_PRESENTATION` 且非默认/非自身的 display；
- `Presentation.show()` 把 Compose 放到下屏。

**坑：** 用 `am start --display 2` 启动会失败或落错屏；`Presentation` 构造失败时不要反复重试同一个
display，先重新枚举。

### 1.2 从下屏 launcher 启动会落到 Display 2

RP Duo 下屏的 AOSP `SecondaryDisplayLauncher` 点图标/长按快捷方式时，会把 Activity 的 launch
display 设为下屏。此时 `dumpsys` 会看到 Task 在 `displayId=2`。

如果 `findSecondaryDisplay()` 不排除 Activity 自身所在屏，就会把 Display 2 当成“副屏”：

- Activity 在下屏渲染视频/预览层；
- Presentation 又试图在下屏叠 UI；
- 上屏停在桌面。

**修复模式（bili-duo 已验证）：**

1. `onCreate`/`onNewIntent` 检测 Activity 不在 `DEFAULT_DISPLAY`；
2. 复制原 Intent（保留 `display_control` 等 extra）；
3. `setLaunchDisplayId(0)` + `NEW_TASK | MULTIPLE_TASK` 重建；
4. `finish()` 掉副屏实例；
5. 加 `EXTRA_REHOMED` 防止设备忽略 launch display 时无限循环；
6. 在 `findSecondaryDisplay()` 里同时排除 `Activity.displayId`，双保险。

**关键坑：** `launchMode=singleTask` 会把 re-home 当成 `LAUNCH_SINGLE_TASK` 发回原实例，Display 0
不会出现新任务（日志常见 `result code=3`）。bili-duo 最终改为 `singleTop` 才成功。

### 1.3 Presentation 必须补齐五个 owner

`Presentation` 是 `Dialog` 子类，没有：

- `LifecycleOwner`
- `ViewModelStoreOwner`
- `SavedStateRegistryOwner`
- `ActivityResultRegistryOwner`
- `OnBackPressedDispatcherOwner`

不补的后果：Compose 组合能跑，但一用 `viewModel()`、`rememberSaveable`、`rememberLauncherForActivityResult`
或 Back 处理就崩。

**版本坑：** bili-duo 用的 `androidx.activity 1.8.2` 没有
`setViewTreeActivityResultRegistryOwner`。它通过
`CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner)` 提供；否则
`rememberLauncherForActivityResult` 会在下屏崩。

LumaDuo 当前把 `SecondScreenPresentation` 的 `ActivityResultRegistry` 委托给宿主 Activity，
因此下屏 Compose 里的 SAF/照片选择器/权限 launcher 会走 Activity registry。若改回 no-op stub，
这些 launcher 会在下屏静默失败；不要因为“编译通过”就认为 ActivityResult 可用。

### 1.4 Presentation 里不要放系统 Dialog

`Dialog`/`AlertDialog`/`Popup`/`ModalBottomSheet` 会创建新的 window。放在 Presentation 的
Context 下，常见结果是：

- `WindowManager.BadTokenException` / window type mismatch；
- 弹窗跑到默认屏而不是下屏；
- 焦点被系统窗口抢走，Presentation 收不到返回键。

统一用 Compose 全屏 scrim + 面板（LumaDuo 的 `DuoOverlay` 就是这个方向），并自己管理焦点和返回。

### 1.5 Presentation 的 Context 不是 Activity

- `LocalContext.current as Activity` 会崩；
- `startActivity` 必须加 `FLAG_ACTIVITY_NEW_TASK`；
- 不要在下屏直接调用只有 Activity 才有的 API；
- `Presentation` 没有 `onDestroy`，销毁时自己处理 lifecycle DESTROYED 和 ViewModelStore.clear；
- 持久状态（会话、导航、播放器）放 Activity 作用域，Presentation 只做 UI。

**下屏 ComposeView 的 Context（2026-11-21 实测）：** 必须用 `ComposeView(context)`（Presentation
的 display-scoped context），不要用 `ComposeView(activity)`。用 Activity context 时 Compose 会取上屏
的 369dpi，而不是下屏的 320dpi，导致 1280×960 屏只得到 555×416dp：`maxWidth < 600.dp` 的 compact
断点被误触发（Rail 52dp、AppBar 40dp、副标题隐藏），所有下屏布局比设计稿小约 15%。

改用 display context 后必须同时补 `decor.setViewTreeOnBackPressedDispatcherOwner(this)`；否则
`BackHandler` 会抛 `IllegalStateException: No OnBackPressedDispatcherOwner`。此前用 Activity context
时是恰好借到了 Activity 的 owner，不能作为正确行为保留。

bili-duo 在 `onStop` 里置 DESTROYED + clear store，但明确警告：**不要依赖 Presentation 自己保存
持久状态**。LumaDuo 目前把导航和 repository 放在 Activity，方向正确。

### 1.6 显示热插拔、异常与重试

需要处理的场景：

- 下屏启动时 `state OFF`：`show()` 会唤醒；
- 下屏被拔出：Presentation 收到 `InvalidDisplayException` 或系统直接 dismiss；
- 下屏重新插入：重新枚举并 show；
- 分辨率/密度改变：更新 metrics，重新布局；
- `BadTokenException`：旧 Presentation 已失效，必须丢弃重建。

建议：

- 注册 `DisplayManager.DisplayListener`；
- 每次变化重新 `findPresentationDisplay()`；
- displayId 变化时 dismiss 旧实例、创建新实例；
- 同 ID 只更新 metrics；
- 失败后延迟 300–500ms 重试一次，不要无限循环；
- 同一 display 只允许一个 Presentation；
- `hide()` 用于 Activity onStop，`dismiss()` 只用于真正销毁。

LumaDuo 已按这个模式加入 `refreshDisplays()`、`InvalidDisplayException`/`BadTokenException` 重试和
“排除 Activity 自身 display”。真机拔插仍需验收。

**2026-11-22 复核（已修）**：`findPresentationDisplay()` 已过滤 `STATE_OFF`。合盖/休眠的物理
场景仍需单独验收。另见 [RP-DUO-DEV.md](../RP-DUO-DEV.md) §2.4。

### 1.7 上屏与下屏的常亮、系统栏

- 串流/本地 UI 期间两屏都加 `FLAG_KEEP_SCREEN_ON`，不要用 wake lock；
- 下屏 Presentation 要全屏并隐藏系统栏；上屏只读页也隐藏系统栏；
- 不要假设下屏有/没有状态栏、导航栏、挖孔；
- 亮度按窗口设置，失败再回退全局；这是 P1，不要阻塞 M3。

### 1.8 截图与 display id

- `screencap -d` 用 SurfaceFlinger 的物理 display id，不是逻辑 `0/2`；
- 逻辑 display id 用 `dumpsys display` / `dumpsys window displays`；
- 不指定 `-d` 时多屏设备会提示 “Multiple displays were found”，默认截第一块，结果不稳定；
- 设备 Dozing 时截图全黑，先 `input keyevent 224` + `wm dismiss-keyguard`；
- `adb shell input -d 2 keyevent` 可能把副屏切为焦点并让应用退到后台，**不能用来验收手柄逻辑**，
  最终以真实手柄为准。

---

## 2. 输入与焦点

### 2.1 先锁定实体键语义

bili-duo 和 UUYC-Duo 在 Duo 上的最终约定都是：

| 实体键 | 语义 |
|---|---|
| D-pad / 左摇杆 | 移动焦点 |
| 实体 B（下方） | 确认/选择/打开 |
| 实体 A（右侧） | 返回/关闭 |
| X / Y | 页面动作（刷新、切换、删除等） |
| START / MENU | 设置/手柄说明/本地菜单 |
| L1/R1 | 一级页签/分类 |
| L2/R2 | 二级页签/筛选 |

LumaDuo 新壳层（Rail/主机/应用/远程/设置/控制台/工具态）已改为 **B 确认、A 返回**，与
bili-duo / UUYC-Duo 一致；`SecondScreenPresentation` 也改为拦截实体 A 作为返回。**legacy V+
页面（PcView、Game Menu 等）仍是 A 确认/B 返回**，这是当前已知的隔离例外。仍需真机确认 Duo
固件丝印和 Xbox/Retro 风格，再决定是否替换/隔离 legacy 入口；不要同时给用户两套提示。

**2026-11-22 复核（重要）**：上表说的是**物理位置**；RP Duo 的 Xbox/Retro 风格会改变原始
keycode。bili-duo 实测：

| 物理位置 | Xbox 原始 | Retro 原始 |
|---|---|---|
| 下 · 确认 | `BUTTON_A` (96) | `BUTTON_B` (97) |
| 右 · 返回 | `BUTTON_B` (97) | `BUTTON_A` (96) |
| 左 · 动作 | `BUTTON_X` (99) | `BUTTON_Y` (100) |
| 上 · 未绑定 | `BUTTON_Y` (100) | `BUTTON_X` (99) |

当前 `DuoKeys.Confirm = Key.ButtonB` / `Back = Key.ButtonA` 没有风格归一化，等价于 Retro 原始码；
Xbox 风格下物理确认/返回可能整体反向。必须先读取/确认风格，再做 A↔B、X↔Y 归一化，并让 UI 标签
跟随风格。完整表见 [RP-DUO-DEV.md](../RP-DUO-DEV.md) §4。

### 2.2 Compose 不认 BUTTON_A/B

Compose 的 click 默认只响应 `DPAD_CENTER`/`ENTER`，`BUTTON_A/B` 不会自动触发 `clickable`。

两种做法：

- 在输入入口把确认键合成为 `DPAD_CENTER` DOWN/UP；
- 或在焦点组件自己的 `onKeyEvent` 里处理 `BUTTON_A/B` 并调用动作。

**双触发坑：** 部分手柄会同时上报 `BUTTON_A` 和合成的 `DPAD_CENTER`。如果两者都处理，会出现
“刚切到下一页又被内部焦点控件点回去”。bili-duo / UUYC 的修复是：入口识别并吞掉合成的
`DPAD_CENTER`，业务层只认一个确认键。

### 2.3 返回键：Presentation 持有焦点时 Activity 收不到

- 上屏 Activity 有焦点：Activity 的 `dispatchKeyEvent` 能收到 BACK；
- 下屏 Presentation 有焦点（常态）：Activity 完全收不到 BACK。

必须在 Presentation 里接：

- `dispatchKeyEvent` 的 BACK/ESC/BUTTON_B；
- `onBackPressed`；
- API 33+ 的 `OnBackInvokedDispatcher`。

**坑：** `setCancelable(false)` 只是禁止默认 dismiss，不会把返回键送给 Activity，也不会自动关
Compose overlay。必须自己分发，并做约 250ms 去抖，避免系统同时走 `onBackPressed` 和
`OnBackInvokedCallback` 导致双 pop。

返回优先级统一为：

```
收系统 IME → 关 Compose overlay/页面内拦截器 → 页面返回 → 根页退出
```

LumaDuo 已把 BACK/ESC/BUTTON_B 路由到 `OnBackPressedDispatcher`，overlay 和设置子页优先关闭；
但 IME 状态和双 pop 仍需真机验证。

**2026-11-22 复核（已修）**：除了“Presentation 有焦点”这条路径，实体手柄事件还可能先到上屏
Activity。修复前实测 `input -d 2 keyevent 20` 可移动下屏焦点，注入实体 `BTN_DPAD_DOWN` 则不动。
现已补 Activity → Presentation 转发；实体 D-pad/B/A 实测通过。详见
[RP-DUO-DEV.md](../RP-DUO-DEV.md) §3。

### 2.4 不要无差别消费 generic motion

bili-duo 曾为了“右摇杆切 tab”在 Activity 层拦截 `dispatchGenericMotionEvent`，结果把 D-pad 的
HAT 事件也吞了，真机左侧方向键直接失灵。

规则：

- 不要在 Activity 层无差别消费 `dispatchGenericMotionEvent`；
- 只有当前 UI 明确需要摇杆（图片缩放、触摸板）时才消费 `AXIS_X/AXIS_Y`；
- `AXIS_HAT_X/AXIS_HAT_Y` 是 D-pad，必须放行；
- 消费条件精确到 `SOURCE_JOYSTICK`/`SOURCE_GAMEPAD` + 当前模式。

### 2.5 repeat 与重复 DOWN

掌机固件可能：

- 按住方向键持续发 repeat；
- 同一 keycode 在几毫秒内重复发 DOWN（无线/蓝牙尤其明显）。

处理：

- 方向、返回、肩键、X/Y 的 `repeatCount > 0` 默认吞掉；长按只走一格/一次；
- 同一 keycode 的 DOWN 做约 40ms 去重；
- X/Y 这类写操作必须去重，避免长按刷请求；
- 如果确实需要“长按连发”（如退格/方向），单独实现 500ms 延迟 + ~55ms 间隔，并在释放时停止。

### 2.6 焦点环：画在 clip 之前，并留透明余量

bili-duo 的外扩焦点环约为：

```
gap 4dp + ring 1.5dp + dark edge 3.5dp ≈ 外扩 5.75dp
```

踩坑：

- 焦点环若在 `clip` 之后绘制，会被圆角裁掉；
- `verticalScroll`/`horizontalScroll` 会按 viewport 裁切四个方向；
- **`contentPadding` 保护不了环**：bring-into-view 会把 item 顶到视口边缘，padding 被滚走；
- 最后一行聚焦时，环底边会被裁。

正确模式：

- 外层焦点容器包含 6–8dp 透明余量，`focusRequester`/clickable/焦点状态在外层；
- 内层负责背景、内容和 `duoFocusRing`；
- 最后一行聚焦后延迟约 80ms 再 `animateScrollTo(maxValue)`；
- 超视口长卡片用 `scrollToItem(index)` 对齐顶部；
- 任何列表/滚动容器里的可聚焦项都按这个模式检查。

bili-duo 的“个人页交换上下屏按钮”就是典型：按钮本身在滚动容器里，焦点环上/下沿被裁；改成
“外层 7dp padding 焦点容器 + 内层 58dp 绘制”后解决。

### 2.7 显式焦点图，不要全靠自动搜索

Compose 的二维空间搜索经常“猜错”，例如从右栏 tab 按 Left 跳到左栏某个按钮。关键路径用
`focusProperties` 显式指定：

```kotlin
Modifier.focusProperties {
    up = if (isFirst) FocusRequester.Cancel else previous
    down = if (isLast) FocusRequester.Cancel else next
    left = FocusRequester.Cancel
}
```

边界用 `Cancel`，否则焦点会漏到全局 Rail 或另一个区域。

bili-duo 新增“稍后再看/分享”行后，点赞/投币/收藏/下载的 Down 没有接上，焦点卡住；后来还出现
“稍后再看按 Up 直接跳到最右边下载”的不合常理路径。**每次新增一行/一个卡片，都要重新检查上下左右
焦点链，而不是假设默认搜索会按视觉列走。**

### 2.8 首焦点与抢焦点

常见 bug：页面内容一组合就自动 `requestFocus()`，用户还在左侧导航栏，焦点已经被拽进内容，
无法继续上下选择分类。

规则：

- 页面入口先聚焦 Rail/导航，不要因为内容被组合就抢焦点；
- 用户明确进入内容（Right/A/触摸）后再聚焦内容；
- 页面导航后的首次聚焦延迟约 120ms，等布局/焦点树稳定；
- 侧栏锁定期间跳过内容聚焦，避免“内容先抢、侧栏再收回”的双闪；
- 每一页首帧必须有一个可见焦点环。UUYC 用户明确反馈：“没有焦点环会让人以为手柄没适配。”

LumaDuo 已做“活动 Rail 按 A 进入内容”；Rail 仍是页面入口的默认焦点。

### 2.9 overlay 焦点陷阱与恢复

- overlay 打开时焦点必须进入 overlay，不能停留在背后的页面；
- 背景页面此时不能再响应确认键；
- overlay 内方向键只在 overlay 内移动；
- 关闭后焦点回到**触发它的控件**，延迟约 120–300ms；
- 所有关闭路径（B、取消、点 scrim、确认）走同一个 `closeXxx()`，否则某条路径会绕过焦点恢复；
- 输入框：第一次 B 只收键盘并保留焦点，第二次 B 才返回/关层。

LumaDuo 已做 overlay 打开即入焦、B 优先关闭 overlay、Up/Down/Tab 的焦点边界，以及关闭后
通过 `DuoFocusMemory` 回到触发控件。Left/Right 仍放行给文本框做光标编辑，因此横向焦点边界
尚未完全锁死；真机需要专门验证。

### 2.10 触摸与手柄共用一棵焦点树

- 一次触摸应直接生效，不要设计“第一次点击只切模式、第二次才操作”；
- 触摸某一行时，如果希望焦点跟随，主动 `requestFocus()` 到该行，避免焦点环停在上一项；
- 触摸和手柄不能各维护一套 selected 状态，否则详情面板和焦点环会不同步。

bili-duo 早期原型曾设计“TOP_PLAYER 下第一次点击只切模式”，最终文档改为“一次点击直接生效”；
LumaDuo 的直通/本地模式也应按这个原则检查，不要为了展示模式而牺牲第一次点击。

### 2.11 肩键与全局导航栏

焦点在左侧 Rail 时，页面根节点不在焦点树里，页面的 `onPreviewKeyEvent` 收不到 L1/R1。

解决模式：全局肩键分发器（`ShoulderDispatcher`）：

- 页面注册自己的 L1/R1/L2/R2 动作；
- 根节点在 Rail 聚焦时转发并吞掉按键；
- 页面离开时注销，避免旧 handler 残留。

### 2.12 选中预览与“空按 A”

- 列表/网格焦点移动时，右侧详情和上屏上下文应随焦点预览，不要等 A 才更新；
- 当前激活的 Rail 项按 A 应进入内容，而不是空按；
- 滑杆应是一个可聚焦行：有焦点环，Left/Right 调整，不要让内部 Slider 成为无环焦点；
- 应用卡片按 A 直接启动会带来误触风险，至少要让焦点先更新详情；有运行中应用时按设计改为打开
  操作 Sheet。

LumaDuo 已实现主机/应用焦点预览、Rail 确认键进内容、滑杆单焦点；应用确认键直接启动的行为仍需按设计复核。

---

## 3. IME 与键盘

### 3.1 不要信 `WindowInsets.isImeVisible`

在 `Presentation` 里，`WindowInsets.isImeVisible` 可能在冷启动/空闲时误报 `true`，导致第一次返回
被当成“收键盘”吞掉。

正确做法：自己维护 `ImeState`：

- 输入框 `show()` 成功后置 `shown=true`；
- 返回/B 第一次：如果 shown，只 `hide()`，保留焦点，不退出页面；
- 第二次：正常返回；
- 窗口隐藏时清状态；
- 首次 `show()` 可能被系统拒绝（Presentation 冷启动），可延迟 300/600ms 重试，但要有
  generation/取消机制，避免用户已收键盘又被旧重试弹出来。

### 3.2 下屏系统 IME 需要先获得焦点

UUYC 真机结论：系统 Gboard 可以在下屏 Presentation 弹出，但**需要先点一下输入框，让下屏获得焦点**。
从手柄唤起时可能只出现面板不出现键盘。

因此：

- 内置 ASCII/拼音键盘必须是默认可用的兜底；
- 系统 IME 作为可选入口，不要作为唯一输入路径；
- 系统 IME 出现时隐藏内置键盘，避免双键盘叠加；
- 中文用 `commitText`/`text_input` 整段发送，不要逐字发 VK；
- 英文和控制键走 VK；
- 组合中的拼音只在本机显示，不发送给远端；
- 退格先删组合串，再删已提交文本。

### 3.3 修饰键与断线

- 离开键盘/工具/断流时必须释放所有 held modifiers 和鼠标按键；
- 手柄直通切本地时先发零值 + `gpd_disconnect`；
- 切回直通时重新 `gpd_connect`；
- `release-all` 要覆盖键盘、鼠标、手柄三条路径，且只发一次。

---

## 4. 构建、调试与验收

### 4.1 Windows 中文路径

- Gradle/CMake/AIDL 对非 ASCII 路径不稳定；源码和 native SDK 放 ASCII 路径；
- UUYC 在中文路径下跑单测出现测试类 `ClassNotFoundException`，需要：
  `-Dorg.gradle.jvmargs="-Xmx3072m -Dfile.encoding=GBK"`；
- LumaDuo 源码已在 `luma-duo`，继续遵守 ASCII 路径。

### 4.2 增量构建旧 dex / 漏类（高频真坑）

UUYC 在 AGP 8.4 + Windows + 增量构建下遇到两类问题：

1. 源码已编译，但合并 dex 漏类，启动报 `NoClassDefFoundError`；
2. 中间产物已更新，但 `packageDebug` 复用旧 APK 的 `classes*.dex`。表现为旧文案/旧逻辑仍在，
   例如按钮点击仍调用空实现，看起来“点击没反应”。

处理：

```bash
./gradlew clean :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

安装前检查 APK 本身：

```bash
unzip -Z1 app/build/outputs/apk/debug/app-debug.apk | grep '^classes.*\.dex$'
# 对可疑类做 dexdump；对旧文案做 grep（先解出 classes*.dex）
```

**只要改了 Compose/UI 或协议类后出现“代码已改但真机行为没变”，先 clean 重建并校验 APK，
再怀疑业务代码。** 不要只删 `app/build/tmp`。

### 4.3 MuMu 不能替代 Duo 验收

MuMu 可以模拟 `1280×960 @320dpi` 的虚拟下屏，验证 Presentation 能创建、Compose 能组合、
按 Home 后窗口消失/回来只有一个窗口。但它不能验证：

- 真实下屏触摸/焦点；
- 实体手柄 keycode 与固件风格；
- 系统 IME 是否出现在下屏；
- 下屏拔插、合盖、re-home；
- 真实 Surface/刷新率/HDR。

MuMu 通过只能算页面级 smoke，不算双屏验收。

### 4.4 adb 注入按键不能验收手柄逻辑

`input -d 2 keyevent` 可能改变 WindowManager 焦点、把应用退到后台，和真实手柄路径不同。
可以用 adb 做“能启动/能截图”的辅助检查，但最终必须用实体手柄走一遍。

### 4.5 截图与路径

- Git Bash 下 `adb push/pull` 的 `/sdcard/...` 会被路径转换，使用
  `MSYS_NO_PATHCONV=1 adb push ... /sdcard/...`；
- 截图用物理 display id，不用逻辑 id；
- 设备 Dozing 时先唤醒再截；
- 不要把包含账号 UID、头像、设备地址、密钥的截图放进仓库或发布页。

### 4.6 发布权限与自动化边界

bili-duo 发生过一次越界：在用户测试前直接 commit/tag/push/发 Release，用户随后撤销并要求
“不要私自发布我没有测试的版本”。

后续规则：

- **只本地构建/安装给用户测试；用户确认后才 commit/push/tag/release；**
- 不在用户操作设备时自动安装、自动按键、自动点屏；
- 不在未确认远端电脑空闲时发起串流或发送输入；
- 测试产生的账号写操作（观看历史、稍后再看条目等）要清理或明确告知。

---

## 5. 网络与协议（UUYC 参考，不迁入 LumaDuo）

LumaDuo 已明确**不迁移 UUYC 协议、签名、RTC、文件传输代码**。以下只作为“如果以后做类似远程协议”
的踩坑记录。

### 5.1 签名 key

- UU 的唯一敏感材料是 24 字节请求签名 key，在官方 TV APK 的 `classes.dex` 里；
- 开源客户端都要求用户自备官方 APK，本地提取；
- 仓库/公开 APK 不能带 key；
- 提取时只校验 SHA-256 指纹，不打印 key；
- 日志不得包含 token、二维码内容、SDP、IP、手机号、签名 key。

### 5.2 Socket.IO 二进制附件

控制权请求的 `ConnectOptions` 二进制附件必须用 Socket.IO 的 type-5 事件：

```
451-<ackId>[...]
```

不能先发普通 `42` 再单独发二进制帧，否则服务端会断开，表现为“信令拒绝连接”。

### 5.3 信令端点校验

- 只允许 `wss://*.163.com`；
- 拒绝 http/ws、伪造域名、带用户信息的 URL；
- 加入房间 → 信令 → room_info → 申请控制权 → RTC → SOAC，顺序不能乱。

### 5.4 手柄直通

- XInput 位掩码与 Windows 一致；
- 轴值 `-32767..32767`，扳机 `0..255`；
- 死区约 0.16，Y 轴按协议反向；
- `gpd_connect` 后 16ms 循环 `gpd_update`，变化即发 + 500ms 保活；
- 切本地/菜单/设置/断线发零值 + `gpd_disconnect`；
- 解析 `gpd_vibration` 的左右马达；
- 不要在远端游戏运行中随意发输入。

### 5.5 文本输入

- `{"action":"text_input","content":"..."}` 直接走 CONTROL 通道；
- 中文整段发送，不逐字发 VK；
- 兜底是 `SendToRom{RomMsg_Text}` 或剪贴板 + Ctrl+V；
- 系统 IME 和内置拼音都最终走同一个提交函数。

---

## 6. 尺寸、视觉与内容

### 6.1 两档屏幕尺寸

- RP Duo 下屏：1280×960 @320dpi = **640×480dp**，4:3，非常矮；
- AYN Thor 下屏：约 1240×1080 @369dpi ≈ **538×469dp**；
- 上屏：约 1920×1080 ≈ **832×468dp**。

不要套手机/平板断点：

- 640dp：应用网格 3 列，主机列表 + 详情双栏；
- 538dp：应用网格 2 列，主机详情收窄或改为覆盖层；
- 下屏高度只有 480dp，顶栏 44 + 模式条 28 后内容约 408dp，任何页面都要按这个高度检查。

### 6.2 上屏

- 播放时纯视频，黑底 letterbox；
- 首帧前只读品牌/主机/应用/连接状态；
- 首帧后隐藏只读层；
- 不显示 OSD、按钮、焦点环、Toast、性能浮层；
- 上屏可以镜像下屏选中的主机/应用，但永远不可操作。

### 6.3 焦点环对比度

bili-duo 在粉色播放键上用粉色焦点环，结果不可辨；最终改成“白色内环 + 品牌色外发光”。
LumaDuo 的 Lumen Gold 按钮也要检查：焦点环不能只靠金色，必须有白色/墨色高对比内环。

### 6.4 不要相信聚合时长/进度

bili-duo 的稍后再看列表把 PGC 长合集的总时长（如 366:40:50）当单集时长显示，出现
“0:50 / 366:40:50”。规则：

- `duration > 24h` 的条目不要显示单集进度/时长角标；
- 不要用聚合字段推断单集；
- PGC/UGC 的 ep/aid 映射要单独确认，不能把 PGC 当 UGC 播放器打开。

### 6.5 空态/错误态/未登录

每个新入口至少检查：

- 未登录；
- 空列表；
- 接口失败/重试；
- 加载中；
- 长列表滚动；
- 亮色/暗色主题；
- 上下屏交换/单屏宿主。

bili-duo 的 rc57 补测里，亮色主题、交换布局、未登录空态、PGC 条目、直播历史边界都是最后才覆盖的，
很容易漏。

---

## 7. 对 LumaDuo 的直接检查清单

### P0（上真机前必须做）

- [ ] 真机锁定实体键语义：下确认 / 右返回 / 左动作 / 上未绑定；按 Xbox/Retro 风格归一化 keycode，
      再处理 legacy V+ 页面。
- [x] Activity → Presentation 按键转发；实体 D-pad/B/A 实测通过（2026-11-22）。
- [ ] `SecondScreenPresentation` 的 ActivityResultRegistry 不能再是 no-op；SAF/权限请求要路由到
      Activity。
- [x] overlay 焦点边界：Up/Down/Tab 被限制在 overlay；关闭后经 `DuoFocusMemory` 回到触发控件。
- [ ] 真机验证 overlay Left/Right 边界与文本框光标编辑不冲突。
- [ ] 所有 Lazy 列表/滚动页检查焦点环裁切：外层 6–8dp 透明余量，内层画环。
- [ ] 新增/修改行后重新检查上下左右焦点链，不用默认二维搜索赌。
- [ ] IME 状态自维护；B 第一次收键盘、第二次返回；系统 IME 与内置键盘互斥。
- [ ] 手柄直通切本地/断线时 release-all + 零值 + disconnect。
- [x] `findPresentationDisplay()` 过滤 `STATE_OFF`；下屏 launcher re-home 到 Display 0
      （`singleTop` + `setLaunchDisplayId(0)` + `EXTRA_REHOMED`），`am start --display 2` 实测通过。
- [ ] clean 构建并检查 APK 内类/文案，避免旧 dex。
- [ ] 538×469 与 640×480 两档布局检查。

### P1（体验完善）

- [ ] 首帧可见焦点环；页面切换 120ms 延迟聚焦。
- [ ] 活动 Rail 确认键进入内容；主机/应用焦点预览；滑杆单焦点。
- [ ] 触摸一次直接生效，不设计“第一次只切模式”。
- [ ] 肩键全局分发器。
- [ ] 上屏空闲态只读上下文；不显示技术 metrics（debug 除外）。
- [ ] 两屏 `FLAG_KEEP_SCREEN_ON`；下屏全屏/隐藏系统栏。
- [ ] 长按/重复键去重；X/Y 写操作防连发。
- [ ] 空态/错误态/未登录/长列表/亮暗主题/单屏宿主逐项验收。

---

## 8. 参考

- bili-duo：`docs/DUO_DUAL_SCREEN_GUIDE.md`、`docs/ui/INPUT_AND_FOCUS.md`、`docs/ARCHITECTURE.md`、
  `docs/FEATURE-GAP.md`。
- UUYC-Duo：`docs/BUILDING.md`、`docs/ui/focus.md`、`docs/feature-matrix.md`、
  `docs/implementation-plan.md`、`docs/research.md`。
- LumaDuo：`docs/ui/SCREEN-SPEC.md`、`docs/ui/DESIGN-SYSTEM.md`、`docs/INPUT-AND-FOCUS.md`、
  `docs/ui/TOP-SCREEN.md`、`docs/DECISIONS.md`。
