# LumaDuo 文档索引

> 最后更新：2026-11-21 · 当前阶段：双屏 RP Duo 主链路已跑通，Phase A/B/C/D1、设置/远程/密度修复、控制台入口补齐与上屏内容补全完成（`67c95e90`）：duo release **16.46 MB**，正式包名 `com.sour.lumaduo`；启动/发现/配对/串流/直通/本地/退出/断开、网络测试、真实应用图标、5 页签控制台与工具态通过。单屏/PiP 按 D-043 延期；下一步稳定性收口（实体手柄/键盘/EasyTier/长时）与 D2 代码清理
> 源码在本仓库；真机数据与未验证项见 TESTING.md，不用构建结果替代设备验收。

本目录是 LumaDuo 的设计与开发基线。代码已开始：M1 包名/品牌化、M2a 主题/壳层、M2b 真实数据、M3 UI v0.3 与真实串流控制台已落地；单屏代码保留但按 D-043 延期，不作为当前验收目标。

## 当前设计基线

- 一级入口：主机 / 应用 / 远程 / 设置。
- 设置 7 类：画面 / 音频 / 输入 / 网络 / 主机 / 界面 / 关于。
- 串流控制台：状态 / 画面 / 音频 / 输入 / 连接；**状态置首并默认**，只读。
- 控制台/工具双态：工具为触摸板 / 键盘 / 快捷键，全宽；工具只在本地模式可操作。
- 上屏只读；控件只在下屏；生产串流默认直通，Return/Back 进入本地。
- 品牌标：UUYC A1 全金矢量源直接缩放，不重绘小尺寸版本。
- 主机实时 CPU/GPU/内存不是 MVP 硬依赖；状态页缺省显示“未提供”，接入 Host Bridge 后再补。

## 阅读顺序

1. [STATUS-AND-PLAN.md](STATUS-AND-PLAN.md) — 当前状态、已验证/未验证、近期 P0/P1/P2 规划
2. [PRODUCT.md](PRODUCT.md) — 产品定位、用户、MVP 边界、非目标
2. [DECISIONS.md](DECISIONS.md) — 已锁定/已取代决策，冲突时以此为准
3. [ARCHITECTURE.md](ARCHITECTURE.md) — 技术架构、模块边界、状态页数据契约
4. [DEVICE-MATRIX.md](DEVICE-MATRIX.md) — RP Duo / AYN Thor 的显示与输入差异
5. [INPUT-AND-FOCUS.md](INPUT-AND-FOCUS.md) — 手柄状态机、键位、焦点
6. [ui/DUO-PITFALLS.md](ui/DUO-PITFALLS.md) — bili-duo / UUYC-Duo 双屏与手柄踩坑复盘
7. [ui/DESIGN-SYSTEM.md](ui/DESIGN-SYSTEM.md) — 颜色、字体、间距、组件、动效
8. [ui/SCREEN-SPEC.md](ui/SCREEN-SPEC.md) — 页面与布局规格
9. [ui/FEATURE-MAP.md](ui/FEATURE-MAP.md) — 上游功能盘点与 LumaDuo 入口矩阵
10. [ui/TOP-SCREEN.md](ui/TOP-SCREEN.md) — 上屏视频与只读状态
11. [ui/PROTOTYPE.md](ui/PROTOTYPE.md) — 原型范围、交互、限制
12. [ui/prototype/index.html](ui/prototype/index.html) — 可点击 HTML 原型
13. [ui/prototype/README.md](ui/prototype/README.md) — 原型页面、键位、URL 参数
14. [ROADMAP.md](ROADMAP.md) — M0 / M0.5 / M1–M5
15. [STREAM-CONSOLE.md](STREAM-CONSOLE.md) — `Game` 托管下屏、状态桥、直通/本地输入与单屏控制台
16. [MINIMAL-EDITION.md](MINIMAL-EDITION.md) — 最小 `duo` 产品面、构建变体、瘦身数据与 Phase B/C 路线
17. [BUILDING.md](BUILDING.md) — 工具链、源码、构建命令
18. [NETWORKING.md](NETWORKING.md) — 配对、EasyTier、虚拟 IP
19. [TESTING.md](TESTING.md) — 测试策略与设备验收
20. [LEGAL.md](LEGAL.md) / [../NOTICE](../NOTICE) — 许可证与来源
21. [../SECURITY.md](../SECURITY.md) — 敏感数据、状态页/主机数据与权限边界

## 文档状态

| 文档 | 状态 |
|---|---|
| PRODUCT / DECISIONS / ARCHITECTURE | v0.3 设计基线；状态页、数据源、图标规则、7 类设置已定 |
| DEVICE-MATRIX / INPUT-AND-FOCUS | v0.2；Thor 需真机复核 |
| DESIGN-SYSTEM / SCREEN-SPEC / TOP-SCREEN | v0.3；7 类设置、主机详情、远程配置、控制台/toolkit、确认层 |
| FEATURE-MAP | v0.3；上游 PcView/设置树/Game Menu/输入网络能力 → LumaDuo 单一入口矩阵 |
| DUO-PITFALLS | v0.1；bili-duo / UUYC-Duo 的显示、焦点、IME、构建与发布踩坑复盘 |
| HTML 原型 | 可点击 v0.3；状态默认、工具态、内联 A1 图标、主机详情/添加/配对、远程配置、7 类设置 |
| STATUS-AND-PLAN | v0.1；2026-11-21 现状、P0/P1/P2 规划；单屏/PiP 延期（D-043） |
| STREAM-CONSOLE | v0.2；`Game` 托管 Presentation、状态桥、`StreamSessionController` 边界、直通/本地焦点、60% 单屏面板（保留但延期验收） |
| MINIMAL-EDITION | v0.3；duo flavor/最小 manifest/Phase B/C/D1；duo release 16.46 MB，D2 源码清理待做 |
| BUILDING / ROADMAP / TESTING | BUILDING 面向本仓库重写；ROADMAP/TESTING 标明代码完成、真机门与未验证项 |
| NETWORKING | 配对、EasyTier、虚拟 IP 调查结论 + 实施计划 |

## 维护规则

- 设计 token、页面结构、输入语义的改动，必须同步更新 `DECISIONS.md` 和相关规格。
- 状态页字段、数据源、刷新率改动 → 同步 `ARCHITECTURE.md` 5.5、`ui/SCREEN-SPEC.md` 7.7、`TESTING.md` M3。
- 品牌素材/尺寸改动 → 同步 `design/icons/uuyc-a1/README.md`、`ui/DESIGN-SYSTEM.md` 8.1；不重绘小尺寸版本。
- 原型行为改动 → 同步 `ui/PROTOTYPE.md`、`ui/prototype/README.md`；示例数据必须明确标注。
- 任何“已验证”结论必须写清设备、系统版本、日期和验证方式。
- 不把未验证的 Thor/ROM 行为写成确定事实。
- HTML 原型是视觉与焦点参考，不是生产代码；生产实现以 Compose 规格为准。
