# 路线图

> 状态（2026-11-21）：双屏 RP Duo 主链路已跑通；M0b baseline、M0.5 双屏探针、M2b-3 真实主机/应用/设置/远程配置、M3 真实双屏控制台、最小 duo Phase A/B/C/D1、真实网络探测与主机应用图标已完成。单屏/PiP 按 D-043 延期，不作为当前里程碑或发布门槛。
> 最小可控版：duo release 16.46 MB，已接管正式包名 `com.sour.lumaduo`，旧 full debug 覆盖升级数据保留。近期以稳定性收口为主，详见 `docs/STATUS-AND-PLAN.md`。
> 剩余：实体手柄/键盘/快捷键、EasyTier 实际隧道、长时串流、断流重连、显示热插拔；P1 占位清理/表单输入；D2 legacy 源码清理；release 签名与许可审计。单屏/PiP、QR、Thor、Host Bridge 延期。
> 私有远程仓库已建立；源码在本仓库，`main` 最新 `67c95e90`。

## M0a：未修改 V+ baseline 构建（已完成）

**目标**：验证工具链和上游可构建性。

任务：

- 创建私有仓库 `luma-duo`，保留 V+ `v12.15.17` 历史
- 安装 JDK 17 / SDK 36 / Build Tools 36.0.0 / NDK 28.2 / CMake 3.22.1
- 拉取 V+ + 子模块 + audio-haptics
- `:app:assembleNonRootDebug`
- 记录 APK、构建日志、耗时

退出标准：

- 构建成功
- 不修改任何源码
- 记录失败项和修复方法

## M0b：RP Duo baseline 串流（首轮通过）

**目标**：验证 V+ 在 Duo + Foundation Sunshine 上的真实表现。

任务：

- [x] 安装 baseline APK
- [x] 配 Foundation Sunshine，局域网配对
- [x] 启动串流
- [x] 记录首帧、码率、延迟、HDR、双屏外接行为
- [x] 记录 V+ 自带菜单行为
- [ ] 记录实体手柄直通（未接实体手柄）

退出标准：

- [x] 1080p60 稳定（实际 HEVC，20 Mbps 配置）
- [x] 首帧/延迟数据可接受（≈18 ms 延迟、4 ms RTT、0% 丢包）
- [x] 基线问题清单形成（见 TESTING.md）

## M0.5：双屏探针（首轮通过）

**目标**：在改 UI 前确定宿主模型、焦点和 IME。

任务：

- [x] 枚举 display：id、name、size、density、flags、canHostTasks
- [x] 在上屏 Activity + 下屏 Presentation 显示 Compose
- [x] 记录手柄事件落到哪个窗口（`input -d 2` 进入 Presentation）
- [x] 记录 Return/Back、焦点
- [ ] 记录 IME、焦点恢复
- [x] 测试 640×480（538×469 无 Thor）
- [ ] 可选：Thor 第二 Activity fallback

退出标准：

- [x] Presentation 是否可聚焦确定
- [ ] InputRouter 的事件来源和去重策略确定
- [ ] IME 策略确定
- [ ] 单屏回退路径确定

## M1：全面改包名 + 品牌化（构建完成；真机首轮通过视频链路）

**目标**：从 `com.limelight` 迁到 `com.sour.lumaduo`。

任务：

- [x] 改 applicationId / namespace / 源码包 / 四个 library namespace
- [x] 改 JNI 符号、`FindClass`、`JNI_NAME` 宏
- [x] 保留 `com.easytier.jni` 不动
- [x] 改 ProGuard、Shizuku、Manifest、测试
- [x] 换 LumaDuo 名称、图标、启动页
- [x] smoke：启动 → 配对 → 串流视频（真机首轮）
- [ ] smoke 剩余：音频、实体手柄直通、EasyTier、Shizuku

退出标准：

- [x] 无 `UnsatisfiedLinkError`
- [x] 配对/串流视频正常
- [ ] EasyTier 正常
- [x] 应用名/图标/包名正确

## M2：新应用壳（M2a 完成；M2b-3 真实页面与生产入口完成）

**目标**：Rail + 主机/应用/远程/设置 + 模式条。

任务：

- [x] Compose 设计系统（Lumen Gold）
- [x] Rail 64dp / AppBar 44dp / ModeBar 28dp
- [x] 主机列表真实数据（ComputerManagerService 快照 + 更新流）
- [x] 主机详情/添加/PIN 配对进新壳（代码完成，待真机；`PcView` 仅回退）
- [x] 应用网格真实数据（Desktop / Steam Big Picture / Xbox Game）
- [x] 从下屏应用网格启动现有串流 Activity
- [x] 远程页读取真实 EasyTier TOML（网络名/虚拟 IP/Peer/监听，密钥始终遮罩）
- [x] 设置分类（画面/音频/输入/网络/主机/界面/关于）接 `PreferenceConfiguration`
- [x] 画面/音频/震动可手柄调整并即时持久化；网络/EasyTier 状态只读
- [x] 生产入口切到 `DuoMainActivity`；`PcView` 保留为添加/配对/EasyTier 管理回退
- [x] EasyTier 下屏启动/停止、PC 配置复制/QR（代码完成，待真机）
- [x] 单屏回退（无下屏时壳层自渲染；串流用 60% 控制台面板）
- [x] Debug 控制台预览 `DuoStreamPreviewActivity`（明确“无会话”，不造假数据）
- [x] 手柄选中优化：主机/应用焦点即预览、活动 Rail 确认键进入内容、滑杆焦点环/左右调整、窄屏应用 2 列
- [x] 上下屏显示优化：DisplayListener 热插拔/re-home、上下屏常亮、下屏全屏、上屏镜像主机/应用（代码完成，待真机）
- [x] 输入修复：新壳层 B 确认/A 返回、Presentation registry 委托、overlay 焦点边界/恢复、DPAD_CENTER 去重
- [x] 禁用 V+ 启动更新弹窗

退出标准：

- 所有一级页面可纯手柄到达
- 640×480 与 538×469 无裁切
- 焦点环不裁切、不残影

## M3：串流控制台 + 输入状态机（UI v0.3 完成；会话/InputRouter 未接线）

**目标**：上屏视频 + 下屏控制台 + 直通/本地模式。

任务：

- [x] `DuoStreamBridge` 接入 `Game` 的 `NvConnection` 会话与 `PerformanceInfo`
- [x] 上屏 `Game` StreamView + 连接/错误状态（沿用 V+ 视频链路）
- [x] 下屏控制台 5 tabs（状态/画面/音频/输入/连接）；状态置首并默认
- [x] 控制台/工具双态：触摸板、ASCII 键盘、快捷键 + 动作条
- [x] 只读状态页：KPI + 60s 趋势 + 2×2 矩阵；无 focusable；无伪造主机负载
- [x] 串流状态数据仓库接真实 `Game` 快照（`DuoStreamBridge`）
- [x] 鼠标/键盘/快捷键发送（`GameDuoStreamActions` + `DuoStreamKeySender`）；断流 release-all
- [x] 直通/本地窗口焦点路由；菜单态复用 `onExternalGameMenuOpened/Dismissed`
- [x] Return/Back 本地菜单 UI；模式条
- [x] 复用 V+ `onExternalGameMenuOpened/Dismissed`
- [x] 数字/ASCII 键盘 UI（工具/添加主机）
- [ ] 双屏亮度（窗口级，全局回退）+ 串流常亮
- [ ] 保留 V+ 剪贴板同步；不做文件传输（设置项已就位）
- [x] Debug `DuoStreamPreviewActivity`（明确“无会话”，不造假数据）

退出标准：

- 直通时手柄不影响本地 UI
- 本地模式无双响应/卡键
- Return/Back/IME/焦点全部可用
- 串流中调整设置不崩溃
- 上下屏亮度独立或明确回退全局；串流不熄屏
- 工具纯手柄可达；键盘无卡修饰键；快捷键/断流无残留输入
- 触摸板手势与按钮在 640×480 下不裁切
- 状态页触摸/确认不改变任何设置；主机负载不可用时显示“未提供”

## M4：配对、EasyTier（单屏延期）

> 最小可控版进展：Phase A/B/C/D1 完成——`duo` flavor（16.46 MB release，正式包名 `com.sour.lumaduo`）、`StreamSessionController` 接口、Firebase/GameMenu/Crown/USB/audio-haptics/framegen 删除；设置/远程/密度/上屏已对齐，控制台 5 页签与工具态入口补齐；D2 待做，见 `docs/MINIMAL-EDITION.md` 与 `docs/STATUS-AND-PLAN.md`。
>
> 单屏/PiP 按 D-043 延期，不纳入当前 M4 验收；已有代码保留，但不再作为发布门槛。

**目标**：把远程连接补完整；双屏 RP Duo 稳定后再评估单屏。

任务：

- [x] PIN 配对 UI + 真实 `PairingManager` 流程（已在 RP Duo 验证）
- [ ] QR 扫码配对（有摄像头时；duo 已移除相机权限，延期）
- [x] EasyTier 配置/状态/启动停止（`DuoRemoteController`，待真机）
- [x] PC `.2` 配置生成 + 复制
- [x] PC 配置二维码（ZXing 生成）
- [x] 虚拟 IP 手动添加
- [~] 单屏壳 + 60% 控制台面板（代码保留；延期验收）
- [~] 单屏状态页/工具面板（代码保留；延期验收）
- [~] PiP（代码保留；延期验收）

退出标准（当前阶段）：

- 局域网串流稳定；EasyTier 远程路径真机通过
- PC 配置不需要手写 TOML
- 双屏 RP Duo 的实体手柄/键盘/快捷键可用
- 单屏/PiP 不作为当前门槛

## M5：发布准备

任务：

- 状态页/HDR/音频/实体手柄长测
- 错误文案/空态/重试
- GPL/NOTICE/源码归档
- 版本号/签名/Release notes
- D2 legacy 源码/资源/full edition 清理
- 文档同步

退出标准：

- 无 P0/P1 缺陷
- 许可证与源码可获取
- 发布包不含遥测/密钥
- 用户明确测试通过后才推送/发布

## 里程碑门

| 门 | 必须通过 |
|---|---|
| M0 → M0.5 | 未修改 V+ 能构建并串流 |
| M0.5 → M1 | 宿主/焦点/IME 策略确定 |
| M1 → M2 | 改包名 smoke test 通过 |
| M2 → M3 | 所有页面可手柄到达 |
| M3 → M4 | 直通/本地切换稳定 |
| M4 → 发布 | 远程 + 双屏稳定性 + 许可证；单屏/PiP 延期 |
