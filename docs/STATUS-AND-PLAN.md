# 现状与近期开发规划

> 更新：2026-11-24 · 源码工作区（基于 `795debc9`，Foundation Fs* 适配未提交） ·
> 产品包名 `com.sour.lumaduo`（debug `.debug`）
> 当前阶段：双屏 RP Duo 主链路、Foundation 1123 客户端能力、应用英雄图和输入释放已跑通；
> 单屏/PiP 按 D-043 延期，不作为当前验收门槛。

## 1. 一句话现状

LumaDuo 已经是一个可安装、可配对、可串流、可退出/断开的双屏掌机客户端：

- 最小可控版 `duo` 接管正式包名 `com.sour.lumaduo`；
- 旧 full debug 覆盖升级后配对/远程配置保留；
- 上屏视频 + 下屏 LumaDuo 控制台真实工作；
- release 16.46 MB，比原 V+ release 29.60 MB 减 44.4%；
- 无 Firebase/分析遥测，无 root/Shizuku 注入。

## 2. 当前产物

| 产物 | 包名 | 大小 | 状态 |
|---|---|---:|---|
| `duoRelease` | `com.sour.lumaduo` | 16,498,792 B | 最新构建（11-24 22:41，含 control-only 修复），未签名；SHA-256 `138716d3…d8e062` |
| `duoDebug` | `com.sour.lumaduo.debug` | 37,329,790 B | 最新构建（11-24 22:38，debug R8 dex 较多），已在 RP Duo 安装验证；SHA-256 `cf71cf68…c1ebec8` |
| `nonRootRelease` | `com.sour.lumaduo.full` | 26,370,119 B | full 对照/调试用 |

源码：本仓库，`main` 最新 `b1ef5dea`。
文档：`docs`；RP Duo 平台复核见 `docs/RP-DUO-DEV.md`。

## 3. 已真机验证（RP Duo / Android 16 / Sunshine）

- 冷启动 `DuoMainActivity`，无缺失 native 库/类初始化崩溃；
- 上屏品牌页、下屏真实壳层（主机/应用/远程/设置）；
- 发现并配对 `GAMING-PC`，配对数据持久化；
- 应用列表读取 3 个真实应用，并加载真实 box art；
- Desktop 串流：上屏 PC 桌面，下屏控制台显示 59–60 FPS、3–4 ms RTT、1920×1080 60Hz、Wi-Fi、电量/温度；
- 主机负载显示“未提供”，实测码率无样本时显示“—”，不伪造；
- 触摸下屏进入本地，A 返回直通；
- `退出串流` / `断开` 均返回壳层，无崩溃、无卡键残留；
- 连接页 TCP 网络测试：3/3 端口，最低 3 ms、平均 4 ms；
- 设置页按 HTML 设计稿改为直属选项行：画面/音频/输入/网络/主机/界面/关于逐类截屏核对，“音频 · 高级”子页与声道选项浮层可用；“帮助 / 诊断”打开内置 Setup Guide；
- 下屏 Compose 改用 display-scoped context，恢复 1280×960 屏的 640×480dp 布局（原先误用上屏 369dpi，555×416dp 触发 compact）；AppBar 标题副标题同行显示；
- 远程页按设计稿重排为 EasyTier 配置卡 + 连接状态卡 + 手动添加条，节点/延迟取真实 EasyTier 状态，丢包无数据源显示“—”；
- 修正后再次真机串流：下屏控制台 640dp 布局，状态页实测 22–23 FPS（静态桌面）、RTT 3–4 ms，连接页/退出确认/返回壳层正常；
- 控制台 5 页签全部接真实控件：画面（分辨率/帧率/屏幕模式/码率/ABR/缩放/HDR）、音频（本机音量/静音/声道/直通/麦克风）、输入（模式/触摸/速度/陀螺仪/震动）、连接（地址/延迟/丢包带宽/网络测试/重连/休眠）；
- 工具态三页可用：触摸板单指移动/点击、双指滚动/右键、双击/长按拖拽；键盘 ASCII 5 行 + 符号 + 系统 IME；快捷键 12 键 + 动作条，Win+L/休眠/退出均二次确认；
- 主机页网络测试改为真实 TCP 探测（3/3 端口，最低 2 ms、平均 10 ms），结果覆盖层可重测；更多菜单增加重新配对；
- 设置“帮助 / 诊断”页显示真实设备/系统/640×480dp/29 个视频解码器/11 个输入设备，并可跳转网络诊断与帮助文档；
- 上屏浏览态按页面补全：主机大标题/地址/状态 tags/显示器示意、应用真实图、EasyTier 网络图与虚拟 IP、设置分类说明 + 前 5 条当前值；仍只读、无焦点；
- 2026-11-24 Foundation 1123 能力适配：主机页显示真实系统/GPU/显示器模式；应用详情显示来源（Steam/Xbox）、类型（桌面/大屏/工具）、默认标记和最近游玩；无图应用的生成封面直接显示；
- 2026-11-24 上屏应用图升级：Foundation `FsArt` 有 `hero` 时显示 300×169 横图，Steam Big Picture / 大屏实机通过；无 hero 回退 190×190 poster；
- 2026-11-24 输入释放：暂停/弹层/退出时释放转发键、右键和中键状态（V+ #865 等价移植）；lint 的 API 23 与 LocalConfiguration 两处错误已修；
- 旧 full debug 覆盖安装到正式包名，配对/远程配置保留；
- Presentation 退出 lifecycle 崩溃已修复（`bcfefa01`）；
- 主机 Sunshine 已从 `ensure_only_display` 改为 `ensure_active`，物理屏在串流/退出后保持激活。

## 4. 当前明确未完成

### 4.1 稳定性/真机验收

- 实体手柄：A/B、十字键/HAT、摇杆、扳机、直通/本地切换；
- 实体键盘与 12 个快捷键到主机，断流/切模式无卡修饰键；
- EasyTier 实际隧道：VPN 授权、启动、虚拟 IP、停止；
- 30 分钟以上长时串流、温度/掉帧/内存；
- 重连、断流、主机睡眠/唤醒；
- 显示热插拔、下屏被占用；
- 音频输出/声道/麦克风的实际听感；
- 538×469 与 Thor（当前无设备）。

### 4.1.1 2026-11-22 RP Duo 复核发现与修复

- **✅ 实体手柄转发已修**：Activity 现在把按键转发给下屏 Presentation。实体 D-pad、下确认、
  右返回均实测通过；修复前实体 B 会直接退到桌面。
- **✅ 两种风格 ABXY 已修**：Xbox 下键=304/BUTTON_A、右键=305/BUTTON_B；Retro 设备为
  vendor 0x2022/product 0x3001，下键=305/BUTTON_B、右键=304/BUTTON_A。`DuoKeys` 用
  `ButtonA` 确认 / `ButtonB` 返回，`DuoFaceButtonMapper` 只对 Retro 内置手柄交换 A↔B/X↔Y；
  两种风格下确认/返回实测通过。
- **✅ 仅控制流崩溃已修**：`checkbox_control_only=true` 时跳过 video SETUP，原 `SdpGenerator`
  断言 `VideoPortNumber != 0` 触发 SIGABRT；现在 ANNOUNCE 的 video 行用 47998 兜底。
  已用 RP Duo 实测控制流串流正常，普通视频串流同时通过。
- **✅ 键位提示已随风格**：Xbox 显示 B/A，Retro 显示 A/B；读取系统
  `flip_button_layout` / `temp_abxy_layout_mode`，canonical keycode 仍为 A/B。
- **✅ `STATE_OFF` 过滤已加**：合盖/休眠的物理场景仍需单独验收。
- **✅ 下屏 launcher re-home 已加**：`DuoMainActivity` 改 `singleTop` + `setLaunchDisplayId(0)`；
  `am start --display 2` 实测回到 Display 0。
- **音频解码**：本机 vendor codec XML 无 AC3/E-AC3，存在 AAC/Opus/FLAC/raw；串流不要依赖杜比硬解。

完整资料与复核表见 [RP-DUO-DEV.md](RP-DUO-DEV.md)。

### 4.2 产品功能

- 设置高级项已与设计稿对齐（增强触摸/麦克风增益/陀螺仪已实现），并补齐帧节奏/FEC、手柄鼠标速度/D-pad、麦克风按钮显示/位置/颜色；摇杆校准/手柄诊断仍无产品入口，按 P1/P2 待定；
- 多地址选择、主机筛选/排序、恢复上次会话、控制台“手柄鼠标模拟”、远程“恢复默认配置”、麦克风按钮颜色/位置、应用桌面快捷方式已接；
- 表单输入仍依赖系统 IME，手柄 ASCII 输入未覆盖 PIN/IP/EasyTier 表单；
- 主机详情/编辑、运行中游戏的退出/切换冲突处理仍可完善；应用搜索/首字母索引未做（P2）；
- Sunshine WebUI 改为打开系统浏览器，不在客户端保存认证凭据；
- 主机静态 OS/GPU/显示模式已接 Foundation `FsPlatform/FsOsVersion/FsGpu/FsDisplayModes`；实时 CPU/GPU/内存负载仍未接，继续显示“未提供”；
- QR 扫码未接（duo 已移除相机权限，暂不做）；
- 单屏模式与 PiP 延期（D-043），代码保留、PiP 入口标记延期，不继续投入。

### 4.3 工程/发布

- full edition 与 legacy 源码仍在：`PcView`/`AppView`/`StreamSettings`/`GameMenu` 等由 R8 删除，但源码未删（D2）；
- `audioHapticsSdkDir` 仍是全局构建硬依赖，`moonlight-core` 仍编译 haptics 适配层；
- `:framegen`、`:usbip-backend`、`:moonlight-haptics-android` 仍是 full 编译依赖；
- release 仍未配置正式签名；公开仓库已建立（GitHub `0sour/LumaDuo`，2026-11-21），无 tag/release；
- GPL/NOTICE 已保留，但发布前仍需完整审计；
- 未推送、未打 tag、未发布（继续遵守“用户测试前不发布”）。

## 5. 近期规划

> 完整的分阶段开发计划（v0.2 → v0.3 → v1.0、下一 sprint、发布门槛与风险）见
> [ROADMAP.md](ROADMAP.md)。本节保留近期 P0/P1/P2 清单。

### P0 — 稳定性收口（下一轮优先）

| 任务 | 验收 |
|---|---|
| 实体手柄转发 ✅ | 已实现 Activity → Presentation 转发；实体 D-pad、下确认、右返回实测通过（2026-11-22） |
| ABXY ✅ Xbox + Retro | Xbox 下=96/右=97；Retro 下=97/右=96，product 0x3001 已归一化并实测 |
| 下屏生命周期 ✅ | `STATE_OFF` 过滤 + re-home 已实现；`am start --display 2` 实测回到 Display 0；合盖/拔插仍待验 |
| 键位提示 ✅ | Xbox B/A、Retro A/B 随系统风格切换；真机两种风格已截屏核对 |
| 仅控制流 ✅ | 原生 `SdpGenerator` 零端口断言已修；控制流串流不再 SIGABRT |
| 实体手柄全键位 | 直通控制 PC；本地下确认/右返回/HAT 移动焦点；切回无卡键 |
| 键盘/快捷键 | ASCII 键、Ctrl/Alt/Win/Shift、12 快捷键、断流释放 |
| EasyTier 端到端 | VPN 授权 → 虚拟 IP → 串流 → 停止；失败有明确提示 |
| 长时串流 | ≥30 分钟；无崩溃/ANR/黑屏；退出后主机显示正常 |
| 断流/重连 | 主机睡眠、网络断开、`recreate` 重连、按键释放 |
| 主机显示安全 | 保持 `ensure_active`；确认退出后物理屏不再黑；记录恢复步骤 |

### P1 — 产品打磨

| 任务 | 验收 |
|---|---|
| Foundation Fs* 元数据 ✅ | `/serverinfo` 主机 profile + `/applist` 应用事实已解析；真机显示系统/GPU/显示模式、来源/类型/默认/最近游玩 |
| Foundation 封面/hero ✅ | 生成封面自动显示；`FsArt` hero 上屏 300×169，poster 回退；按 `FsArtVersion` 懒加载并限缓存 |
| V+ #865 输入释放 ✅ | 暂停/弹层/退出释放转发键、右键、中键；lint 通过 |
| 大屏/桌面/工具语义 | 当前仅标签；是否从应用网格移到独立入口、工具分组的决定未做 |
| 画面/音频页占位清理 | 每行是“可用/需重连/隐藏”之一，不再有模糊占位 |
| 表单 ASCII 输入 | PIN、IP、EasyTier 表单可完全用手柄完成 |
| 主机/应用操作 | 运行中游戏退出/切换、应用详情、隐藏/快捷方式语义清楚 |
| 连接页信息 | 网络测试、EasyTier、RTT/丢包文案一致；失败可行动 |
| 设置收敛 | 最小版只保留真实可用项；需重连的项明确标注 |

### P2 — 代码清理（稳定后再做）

| 任务 | 说明 |
|---|---|
| D2 legacy 删除 | 拆共享模型/入口后删除 `PcView`/`AppView`/`StreamSettings`/`GameMenu` |
| full edition 退场 | 删除 `nonRoot`/`root` 或降为临时调试变体 |
| 可选编译依赖清理 | audio-haptics/framegen/usbip/pyrowave 与 JNI 同步拆 |
| CI/发布 | 签名、CI 构建、GPL/NOTICE 审计、升级路径 |

### 延期（不排期）

- 单屏模式、PiP；
- QR 扫码；
- Thor 适配；
- 主机实时负载/Host Bridge；
- Sunshine WebUI；
- 文件传输（原本就不做）。

## 6. 发布门槛

在用户明确测试通过前：

- 不推送、不打 tag、不发布；
- release 必须先完成签名配置与 GPL/NOTICE 审计；
- 至少通过：冷启动、配对、串流、直通/本地、实体手柄/键盘、EasyTier、退出/断开、长时稳定性；
- RP Duo 是 OLED：任何长时静态页面必须先降亮度，测试间隙熄屏；长时串流降低下屏亮度；
- 单屏/PiP 不作为当前门槛（D-043）。

## 7. 相关文档

- [MINIMAL-EDITION.md](MINIMAL-EDITION.md) — 最小版范围、瘦身和 Phase D
- [STREAM-CONSOLE.md](STREAM-CONSOLE.md) — 双屏控制台与输入模型
- [TESTING.md](TESTING.md) — 测试用例与真机记录
- [ROADMAP.md](ROADMAP.md) — 里程碑
- [DECISIONS.md](DECISIONS.md) — D-043 单屏延期
