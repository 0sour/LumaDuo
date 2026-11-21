# 许可证与合规

## 1. LumaDuo 自身

- 许可证：**GPL-3.0**
- 仓库根目录保留 `LICENSE`。
- 分发 APK/源码时，必须提供完整对应源码或 GPL 要求的书面要约。
- 不得把 LumaDuo 重新许可为闭源/专有。

## 2. 上游

| 组件 | 许可证 | 使用方式 | 义务 |
|---|---|---|---|
| Moonlight V+ | GPL-3.0 | Android 客户端底座 | 保留 LICENSE/NOTICE；源码可获取 |
| Moonlight Android | GPL-3.0 | V+ 上游 | 同上 |
| Foundation Sunshine | GPL-3.0 | PC 端，不打包 | 文档注明；分发其二进制时遵守 GPL |
| Sunshine | GPL-3.0 | Foundation 上游 | 同上 |
| EasyTier | LGPL-3.0 | Android JNI/so | 保留 LGPL/NOTICE；允许替换库 |
| audio-haptics SDK | Apache-2.0 | 构建依赖 | 保留 Apache NOTICE |
| bili-duo | MIT | UI 设计语言/部分 Compose 代码 | 保留 MIT 版权声明 |
| UUYC-Duo A1 图标 | GPL-3.0 | 图标几何 | 保留 NOTICE；LumaDuo GPL 兼容 |

## 3. bili-duo 代码复用

bili-duo 是 MIT，允许复制/修改/再分发，但必须保留：

```text
MIT License
Copyright (c) 2026 yearzen1
```

如果复制 `Theme.kt`、`DuoFocus.kt`、`DuoRail.kt`、`TabCycle.kt`、`DuoBrand` 等文件，在文件头或 `NOTICE` 中保留版权与许可声明。

LumaDuo 整体是 GPL-3.0；MIT 代码并入 GPL 项目是兼容的，但 MIT 声明不能被去掉。

## 4. UUYC 图标

- 来源：`UUYC-Duo/docs/ui/duoremote-icon.svg`
- 原项目：GPL-3.0
- LumaDuo：GPL-3.0
- 已重着色为全金，保留 `design/icons/uuyc-a1/NOTICE.txt`。
- 不包含 UUYC 的协议、RTC、文件传输代码。

## 5. 商标与素材

不使用：

- Bilibili logo、2233 娘、B 站截图/封面
- AYN / Retroid 官方 logo、固件、商店素材
- NVIDIA / Moonlight / Sunshine 官方品牌素材
- UUYC / 网易 UU 远程的图标/品牌

LumaDuo 是非官方项目，与上述公司无隶属关系。

## 6. 字体

- 使用系统字体（Noto Sans CJK / Roboto）。
- 不打包第三方商业字体。
- 数字使用系统 tabular figures，不引入字体文件。

## 7. 分发清单

发布 APK 前检查：

- [ ] `LICENSE` 存在
- [ ] `NOTICE` 存在
- [ ] `design/icons/uuyc-a1/NOTICE.txt` 存在
- [ ] bili-duo MIT 声明（如复制代码）
- [ ] EasyTier LGPL 声明
- [ ] audio-haptics Apache 声明
- [ ] 源码 tag/commit 可获取
- [ ] 不包含密钥、keystore、google-services.json
- [ ] 不包含未授权素材
- [ ] Release notes 注明非官方
