# LumaDuo 最终图标 · UUYC A1 全金版

## 决定

- 图形：直接复用 UUYC-Duo A1「双屏手柄」图标，不重画。
- 配色：全金。
  - 背景 `#15151C`
  - 屏幕/光标/十字键 `#F5F5F7`
  - 信号弧/下屏/ABXY `#F2C14E`
- 不保留 UUYC 的青色；Android 资源里已确认无 `#6CCBFF` / `#5ED0FF` / `#EAF6FF` 残留。

## 文件

- `svg/a1-gold.svg`：全金矢量源。
- `png/a1-gold-512.png`：512px 预览。
- `png/compare.png`：原版 / 全金 / 金+青信号对比图。
- `android/drawable/ic_launcher_foreground.xml`：adaptive icon 前景。
- `android/values/colors.xml`：`ic_launcher_background = #15151C`。
- `android/mipmap-anydpi-v26/`：API 26+ adaptive icon。
- `android/mipmap-mdpi` … `xxxhdpi`：API 22–25 legacy PNG，含圆形版本。

## 接入 LumaDuo 时

1. 复制 `android/drawable/ic_launcher_foreground.xml` 到 app 的 `src/main/res/drawable/`。
2. 把 `android/values/colors.xml` 中的 `ic_launcher_background` 合并进 app 的 `values/colors.xml`。
3. 复制 `android/mipmap-*` 到 app 的 `src/main/res/`。
4. Manifest 使用：

```xml
android:icon="@mipmap/ic_launcher"
android:roundIcon="@mipmap/ic_launcher_round"
```

5. 应用内 `DuoLogo` 直接使用同一 A1 矢量源（SVG / Android VectorDrawable）缩放；**不要**按小尺寸重新绘制，否则线条比例会失真。

## 说明

- 没有 `<monochrome>` 层：这是刻意的，避免双屏/手柄细节在单色主题图标里糊成一团。若以后要支持 Android 13 主题图标，另做一版专用单色 glyph，不要直接加 monochrome。
- 来源与许可证见 `NOTICE.txt`。UUYC-Duo 与 LumaDuo 均为 GPL-3.0。
