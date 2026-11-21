# 输入与焦点契约

> 状态：设计稿。任何页面、overlay、键盘都必须遵守本文。
> 参考：bili-duo `INPUT_AND_FOCUS.md`、UUYC-Duo `focus.md`、V+ `ControllerHandler`。
>
> **2026-11-20 注意**：新 Compose 壳层已按本契约改为“B 确认、A 返回”，
> `SecondScreenPresentation` 也改为实体 A 返回；legacy V+ 页面（PcView/Game Menu）仍是
> A 确认，作为已知隔离例外。以 Q-28 真机锁定丝印后处理 legacy 入口。
> 详见 [ui/DUO-PITFALLS.md](ui/DUO-PITFALLS.md) 第 2.1 节。

## 1. 核心原则

1. **任意时刻只有一个输入所有者**。
2. **任意时刻只有一棵可见焦点树**。
3. **本地 UI：B 确认、A 返回**（按实体丝印，不按 Android 习惯）。
4. **串流默认直通**；本地模式是显式进入的状态。
5. **不用组合键打开本地菜单**；用系统 Return/Back 或下屏触摸按钮。
6. **方向/返回/肩键吞 repeat**；同 keycode 40ms 去重。
7. **不全局拦截 generic motion**；D-pad HAT 必须放行。

## 2. 输入状态

| 状态 | 输入去向 | 焦点环 | 说明 |
|---|---|---|---|
| `HOME_LOCAL` | 下屏 Compose | 显示 | 主机/应用/远程/设置 |
| `STREAM_PASSTHROUGH` | 远端 PC | 隐藏 | 串流默认；全部手柄键直通 |
| `STREAM_LOCAL` | 下屏 Compose | 显示 | 本地控制台；暂停远端输入；内含 `CONSOLE / TOOLS` 子视图 |
| `OVERLAY` | 下屏 overlay | 显示 | 模态独占；B 只关最上层 |

- `STREAM_LOCAL` 的 `CONSOLE / TOOLS` 不改变输入所有者，不是新的顶层状态。
- `TOOLS` 只允许在本地模式出现；直通时工具被遮罩，触摸/Return 先进入本地。

### 状态转换

```text
HOME_LOCAL ──选择应用/连接──> STREAM_PASSTHROUGH
     ▲                              │
     │                     Return/Back/菜单键
     │                              ▼
     └────退出串流──── STREAM_LOCAL ──A/根层──> STREAM_PASSTHROUGH
                           │    ▲
                     Y/工具 │    │ A/Y
                           ▼    │
                       STREAM_TOOLS（子视图）
                           │
                       打开 overlay
                           ▼
                        OVERLAY
```

- 进入串流：首帧后默认 `STREAM_PASSTHROUGH`。
- `STREAM_LOCAL` 进入：`ControllerHandler.onExternalGameMenuOpened()`。
- 退出本地：`ControllerHandler.onExternalGameMenuDismissed()`；等待所有按键释放。
- 退出串流：发零值 + release-all，回 `HOME_LOCAL`。

## 3. 事件来源与去重

```text
Activity.dispatchKeyEvent
Presentation.dispatchKeyEvent
Activity.dispatchGenericMotionEvent
Presentation.dispatchGenericMotionEvent
        │
        ▼
   InputRouter(source, event)
        │
        ├─ 本地 -> Compose 焦点树 / BottomUiBridge
        └─ 直通 -> V+ KeyboardInputHandler / TouchInputHandler
```

- `Activity` 和 `Presentation` 都可能收到事件；不能假设“一定先到上屏”。
- 去重键：`(keyCode, action, eventTime, source)`；同键 40ms 窗口。
- DOWN 被转发过才转发 UP；避免长按/按键状态错乱。
- 转发目标是目标窗口的 `decorView`，不是 ComposeView。
- 同一个按键只能有一个消费者；不能 Activity 和 Presentation 同时响应。

## 4. 面键语义

| 实体位置 | 丝印 | 本地 UI | 远端 Xbox |
|---|---|---|---|
| 下方 | B | 确认 / 打开 / 连接 | Xbox A (4096) |
| 右方 | A | 返回 / 关闭 | Xbox B (8192) |
| 左方 | Y | 页面动作（刷新/上下文） | Xbox X (16384) |
| 上方 | X | 页面动作（更多/账号） | Xbox Y (32768) |
| D-pad | — | 移动焦点 | D-pad |
| START | — | 本地：帮助；直通：发给 PC | Start |
| Return/Back | — | 打开本地菜单 / 返回 | 不发送（由本地消费） |

- 入口归一化：RP Duo 的“手柄风格”会交换 ABXY keycode；统一转 canonical Xbox。
- 本地 B/A 与远端 A/B 是两套语义，不能混用。
- 所有 UI 提示写实体丝印：`B 确认 · A 返回`，不写“canonical A”。

## 5. 键位

### 5.1 本地浏览

| 输入 | 行为 |
|---|---|
| D-pad / 左摇杆 | 移动焦点；列表/网格滚动 |
| B | 确认 / 打开 / 连接 |
| A | 返回上一页 / 关闭页面内确认层 |
| L1 / R1 | 一级页签 / 分类 |
| L2 / R2 | 二级页签 / 筛选 |
| X / Y | 页面动作：刷新、添加、删除、更多 |
| START | 帮助/键位表 |
| 触摸 | 点按即聚焦并执行 |

### 5.2 串流控制台（本地）

| 输入 | 行为 |
|---|---|
| D-pad / 左摇杆 | 移动焦点 |
| B | 确认 / 切换 |
| A | 关闭 overlay → 根层恢复直通 |
| L1 / R1 | 状态 / 画面 / 音频 / 输入 / 连接 五个 tab；状态默认 |
| L2 / R2 | 当前 tab 的子页/筛选 |
| START | 帮助/键位表 |
| Y | 打开工具（触摸板/键盘/快捷键） |
| X | 状态页/更多 |
| 触摸 | 进入本地模式并操作 |

### 5.3 串流工具（本地）

| 输入 | 行为 |
|---|---|
| D-pad / 左摇杆 | 工具 tab / 键帽 / 快捷键卡片间移动焦点 |
| B | 发送按键、点击鼠标按钮、执行快捷键 |
| A | 关闭 overlay → 工具态返回控制台 |
| L1 / R1 | 触摸板 / 键盘 / 快捷键 |
| Y | 返回控制台（与 A 等效，保留工具子页） |
| 触摸 | 触摸板手势、键帽点按、快捷键点按 |
| START | 帮助/键位表 |

### 5.4 Overlay

| 输入 | 行为 |
|---|---|
| D-pad | overlay 内移动 |
| B | 选中/切换 |
| A | 关闭 overlay，焦点回触发控件 |
| START | 不穿透；可显示帮助 |

## 6. 本地菜单触发

- **推荐**：系统 Return/Back，或下屏触摸“菜单”按钮。
- 不使用 `L1+R1+START` 等组合键；组合键会短暂改变远端手柄状态。
- 直通时 START 发给 PC；只有进入本地模式后 START 才打开帮助。
- 下屏 Presentation 持有焦点时，Activity 收不到 Return；Presentation 自己接：

```kotlin
override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    if (event.keyCode == KeyEvent.KEYCODE_BACK ||
        event.keyCode == KeyEvent.KEYCODE_ESCAPE) {
        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) dispatchBack()
        return true
    }
    return super.dispatchKeyEvent(event)
}
```

- API 33+ 注册 `OnBackInvokedCallback`；250ms 去抖。
- 返回优先级：收 IME → 关 overlay → 页面 interceptor → 返回根层/恢复直通。

## 7. 焦点模型

### 7.1 单棵树

- 双屏：只有下屏 Presentation 的 Compose 树参与焦点。
- 单屏：唯一宿主内一棵树。
- 上屏视频/状态层不注册 focusable。
- overlay 打开时，焦点限制在 overlay 内；关闭后回到触发控件。

### 7.2 焦点环

- 规格：内容外 4dp 空隙 + 1.5dp 白环 + 3.5dp 深色分隔边。
- 深色：白环 + 黑边；浅色：墨环 + 白边。
- 环画在 `clip` 之前；滚动容器内容留 7dp 透明余量。
- 失焦立即消失；进入 120ms 淡入；不做呼吸灯。
- 焦点环是唯一焦点指示，不用粉色/金色光晕替代。

### 7.3 导航

- 关键路径显式 `focusProperties`；不要完全依赖 2D 自动搜索。
- 首尾用 `FocusRequester.Cancel`，防止焦点漏到 rail/其他区域。
- rail 导航后焦点留在 rail；按方向/触摸才进入内容。
- 页面首次聚焦延迟 ~120ms，等布局稳定。
- 方向键聚焦 tab 时预切换内容并保留 tab 焦点；L1/R1 切 tab 后聚焦内容首行；B/点击 tab 保留 tab 焦点。
- 状态 tab 为只读且默认：内容无 focusable；从 tab 向下保持在 tab，不进入内容；Q/E 可继续切 tab。
- L1/R1 在设置页切换分类，并聚焦该分类首行。
- overlay 关闭：焦点回触发控件，延迟 120–300ms。
- 工具态：工具 tab → 内容第一项；A 回控制台时焦点回“工具”切换按钮。
- 键盘：方向键在键帽网格移动；所有键必须可达；B 发送后保留键帽焦点。
- 触摸板：触摸面可聚焦，B 发左键；向下进入鼠标按钮行；方向键不移动远端指针。
- 快捷键：4×3 网格；Win+L 是危险项，焦点进入时仍显示红色描边。

### 7.4 Repeat 与去重

- 方向、返回、肩键、X/Y：`repeatCount > 0` 吞掉。
- 同 keycode DOWN 40ms 去重。
- 进度条/音量等需要连续调节的控件自己处理 repeat，不依赖系统。
- 直通时 V+ 按自身 60Hz 状态发送，不套用本地 repeat 逻辑。

## 8. 文本输入

MVP 范围：

- **数字键盘**：PIN、IP、端口、虚拟 IP。
- **ASCII 全键盘**：网络名、密钥、主机名；支持 Shift/符号/退格/清空/完成。
- 中文不要求；需要时用系统 IME/触摸。
- 所有键位可 D-pad 移动、B 输入、A 关闭、X 退格、Y 清空、START 完成。

交互：

- 输入框聚焦只显示选中态；B/触摸进入编辑并打开自绘键盘。
- A 第一次收键盘，保留焦点；第二次返回。
- 系统 IME 只做补充；不依赖 `WindowInsets.isImeVisible`。
- 输入内容不写日志；密钥默认掩码。

### 8.1 串流远端键盘（区别于本地输入）

- 串流工具里的键盘是 **ASCII 直发 HID**：按键直接发送到 PC，不做本地拼音、候选词、组词。
- 修饰键 Ctrl / Win / Alt / Shift 为粘滞状态；离开键盘/工具/断流时清空。
- `?123` 切符号页；Esc/Tab/Enter/Backspace/方向键直接发送。
- “系统”按钮只作触摸补充，交给 Android IME；M0.5 验证下屏 Presentation 的表现。
- 系统 IME 不参与 LumaDuo 的远端快捷键序列；不要混用两种输入栈。

## 9. ModeBar

常驻 28dp，不可聚焦、不可点击。

| 状态 | 左 | 中 | 右 |
|---|---|---|---|
| 浏览 | 金点「手柄：下屏」 | 当前页面提示 | `B 确认 · A 返回` |
| 直通 | 青点「手柄：直通电脑」 | 主机名/质量 | `RETURN 本地菜单` |
| 本地控制台 | 金点「手柄：本地」 | 当前 tab（含状态） | `Y 工具 · A 直通` |
| 本地工具 | 金点「手柄：本地」 | 当前工具 | `Q/E 工具 · A 控制台` |
| Overlay | 橙点「菜单」 | overlay 名称 | `B 关闭` |
| 无手柄 | 灰点「手柄未连接」 | 触屏操作 | — |

文本最长 18 个汉字，超出省略，不换行；切换 120ms 淡入，不做位移动画。

## 10. 验收

- 双屏：上屏无焦点环、无按钮；下屏首屏有一个可见焦点。
- 直通：手柄不驱动下屏焦点；ModeBar 显示直通。
- Return/Back：在 Presentation 持有焦点时仍能打开本地菜单。
- 本地：所有页面/页签/overlay 可纯手柄到达；无双响应。
- 工具：触摸板按钮、键盘每个键、快捷键卡片都可纯手柄到达。
- 状态页：没有任何可点击控件；触摸/确认不会修改设置；主机负载不可用时显示“未提供”。
- 工具：离开键盘/工具/断流时修饰键与鼠标键全部释放，无卡键。
- 快捷键：按下/释放顺序正确；Win+L 需确认；断流时不残留组合键。
- IME/自绘键盘：A 收键盘、B 返回、焦点不丢。
- 长按方向/返回/肩键只走一格/一次。
- 640×480 与 538×469 两档布局焦点环不裁切。
