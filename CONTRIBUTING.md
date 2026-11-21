# 贡献指南

## 1. 开始前

先读：

- [README.md](README.md)
- [docs/PRODUCT.md](docs/PRODUCT.md)
- [docs/DECISIONS.md](docs/DECISIONS.md)
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
- [docs/INPUT-AND-FOCUS.md](docs/INPUT-AND-FOCUS.md)
- [docs/ui/DESIGN-SYSTEM.md](docs/ui/DESIGN-SYSTEM.md)

如果改动涉及输入、页面结构、网络或设计 token，先更新文档/决策，再写代码。

## 2. 分支与提交

- `main`：稳定基线
- 功能分支：`feat/...`、`fix/...`、`docs/...`
- 提交信息：`type(scope): summary`，例如 `feat(duo): add display capability probe`

不把无关改动混在一个提交里。改包名、UI、网络分开。

## 3. 代码风格

- Kotlin/Compose，跟 V+ 现有风格；新文件不引入新的 UI 框架。
- 所有颜色/字号/间距用 token；不在页面里写裸 `Color(0x...)`。
- 焦点控件必须可 D-pad 到达；不允许 touch-only 关键操作。
- 新页面必须同时检查 640×480 和 538×469。
- 不在 `Presentation` 里用 `Dialog` / `AlertDialog` / `ModalBottomSheet`。
- 不在日志里打印 PIN、EasyTier 密钥、证书、token、公网 IP 列表。

## 4. 测试

- 纯状态机/配置生成必须有 JVM 单测。
- UI 改动至少做 640×480 截图检查；双屏/输入改动必须真机。
- 改 JNI/包名必须做真机 smoke test。
- 提交 PR 时写清：改了什么、怎么验证、哪些没验证。

## 5. 文档

- 设计 token / 页面结构 / 输入语义改动 → 同步 `docs/ui/` 和 `docs/INPUT-AND-FOCUS.md`。
- 状态页字段 / 数据源 / 刷新率改动 → 同步 `docs/ARCHITECTURE.md` 5.5、`docs/ui/SCREEN-SPEC.md` 7.7、`docs/TESTING.md` M3。
- 品牌素材/尺寸改动 → 同步 `design/icons/uuyc-a1/README.md`、`docs/ui/DESIGN-SYSTEM.md` 8.1；直接缩放最终 SVG，不重绘小尺寸版本。
- 原型行为改动 → 同步 `docs/ui/PROTOTYPE.md`、`docs/ui/prototype/README.md`；示例数据必须标注。
- 新决策 → 加到 `docs/DECISIONS.md`，写日期、理由、状态。
- 未确认 → 写入 `docs/STATUS-AND-PLAN.md` 的待确认项，不要写成事实。

## 6. 许可证

- 新文件默认 GPL-3.0。
- 复制 bili-duo（MIT）代码：保留 MIT 版权声明。
- 复制 UUYC（GPL-3.0）代码：保留来源说明。
- 不提交未授权素材/字体/图标。

## 7. PR 检查

- [ ] 文档同步
- [ ] 单测/真机验证
- [ ] 640×480 / 538×469
- [ ] 焦点/返回/IME
- [ ] 无密钥/遥测
- [ ] LICENSE/NOTICE 未破坏
