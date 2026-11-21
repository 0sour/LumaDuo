# 构建、安装与调试

> 环境基线：2026-11-21 · RP Duo / Android 16 已实测。
> 本文所有命令以仓库根目录为当前目录；不要把仓库放在包含中文、空格或括号的路径下。

## 1. 工具链

| 组件 | 版本 / 说明 |
|---|---|
| JDK | 17（Gradle 与 AGP 需要） |
| Android SDK | compileSdk 36，Build Tools 36.0.0 |
| Android NDK | 28.2.13676358 |
| CMake | 3.22.1 |
| Gradle | 9.4.1（使用仓库内 wrapper，不需要单独安装） |
| Git | 支持 submodule |
| Python | 3.9+，仅可选脚本使用，构建本身不依赖 Python |

安装 SDK 组件：

```bash
sdkmanager "platforms;android-36" \
  "build-tools;36.0.0" \
  "ndk;28.2.13676358" \
  "cmake;3.22.1"
```

## 2. 拉取源码

```bash
git clone --recurse-submodules <repository-url> luma-duo
cd luma-duo
git submodule update --init --recursive
```

仓库包含三个子模块：

| 路径 | 用途 |
|---|---|
| `app/src/main/jni/moonlight-core/moonlight-common-c` | Moonlight 协议核心 |
| `framegen/src/main/cpp/lsfg-vk-android` | full edition 的帧生成（duo 不打包） |
| `third-party/pyrowave` | full edition 的 Vulkan 解码（duo 不打包） |

`duo` 产品变体不使用 framegen / PyroWave / USBIP / haptics 的 native 产物，但 full edition 仍需要这些子模块参与编译。

### 2.1 audio-haptics SDK

`settings.gradle` 目前把 audio-haptics 的 Android 模块作为全局编译依赖；即使只构建 `duo`，也需要准备 SDK：

```bash
git clone https://github.com/AlkaidLab/moonlight-audio-haptics.git ../moonlight-audio-haptics
git -C ../moonlight-audio-haptics checkout b3f97c3bb7500ea7b1985aea568e5c7b40308d3b
```

在 `local.properties` 中写入：

```properties
sdk.dir=/path/to/Android/Sdk
audioHapticsSdkDir=/path/to/moonlight-audio-haptics
```

也可以使用环境变量 `AUDIO_HAPTICS_SDK_DIR`，或构建时传 `-PaudioHapticsSdkDir=...`。`local.properties` 已被 `.gitignore` 忽略，不要提交。

## 3. 变体与包名

| 变体 | applicationId | 用途 | ABI |
|---|---|---|---|
| `duoDebug` | `com.sour.lumaduo.debug` | 双屏产品调试包 | arm64-v8a |
| `duoRelease` | `com.sour.lumaduo` | 正式产品包（当前未配置签名） | arm64-v8a |
| `nonRootDebug` / `nonRootRelease` | `com.sour.lumaduo.full`（debug 加 `.debug`） | V+ 全量产品面对照/调试 | arm64-v8a, armeabi-v7a |
| `rootDebug` / `rootRelease` | `com.sour.lumaduo.root` | root 构建对照 | arm64-v8a, armeabi-v7a |

`duo` 是 LumaDuo 的唯一产品线；`nonRoot` / `root` 只作为上游能力对照和临时调试变体保留，计划在 D2 清理。

## 4. 构建

```bash
# 日常开发
./gradlew :app:assembleDuoDebug

# release（未签名，不能直接发布）
./gradlew :app:assembleDuoRelease

# 单元测试与 lint
./gradlew :app:testDuoDebugUnitTest :app:lintDuoDebug

# full edition 回归，确认共用引擎没有被改坏
./gradlew :app:assembleNonRootRelease
```

Windows 使用 `gradlew.bat`。首次构建需要下载 Gradle、依赖与 NDK 产物，耗时明显高于增量构建。

### 4.1 代理

Gradle 默认读取 `~/.gradle/gradle.properties`。需要代理时，按本机代理端口配置：

```properties
systemProp.http.proxyHost=127.0.0.1
systemProp.http.proxyPort=7890
systemProp.https.proxyHost=127.0.0.1
systemProp.https.proxyPort=7890
```

不要把带账号密码的代理配置提交到仓库。

## 5. 安装与启动

```bash
adb devices
export DUO=<adb devices 输出中的 serial>

adb -s "$DUO" install -r -d app/build/outputs/apk/duo/debug/app-duo-debug.apk
adb -s "$DUO" shell am start -W \
  -a android.intent.action.MAIN \
  -c android.intent.category.LAUNCHER \
  -n com.sour.lumaduo.debug/com.sour.lumaduo.LauncherSaba
```

启动入口是 `com.sour.lumaduo.duo.DuoMainActivity`；桌面 `LAUNCHER` 挂在 `LauncherSaba` / `LauncherSabaDark` 两个 `activity-alias` 上。

**不要**使用 `am start --display 2` 启动 Activity：RP Duo 的下屏 `canHostTasks=false`，Activity 不能直接放到下屏。正确做法是在默认屏启动 Activity，再按 `FLAG_PRESENTATION` 查找下屏并 `Presentation.show()`。

## 6. 双屏调试

### 6.1 确认显示与焦点

```bash
"$ADB" -s "$DUO" shell "dumpsys display | grep -E 'DisplayDeviceInfo|mDisplayId=|FLAG_PRESENTATION|canHostTasks'"
"$ADB" -s "$DUO" shell "dumpsys window displays | grep -E 'Display: mDisplayId|mCurrentFocus'"
```

RP Duo 的下屏是逻辑 display 2，尺寸 1280×960 @320dpi，即 640×480dp。上屏 Activity 与下屏 `Presentation` 是两个窗口；输入注入时注意区分：

```bash
# 下屏焦点/按键
adb -s "$DUO" shell input -d 2 keyevent 22
adb -s "$DUO" shell input -d 2 keyevent 4

# 上屏
adb -s "$DUO" shell input -d 0 keyevent 4
```

### 6.2 截图

`input -d` 使用逻辑 display id，`screencap -d` 使用 SurfaceFlinger unique id。先从 `dumpsys display` 取得两屏的 unique id，再分别截图：

```bash
"$ADB" -s "$DUO" exec-out "screencap -p -d $TOP_DISPLAY_ID" > top.png
"$ADB" -s "$DUO" exec-out "screencap -p -d $BOTTOM_DISPLAY_ID" > bottom.png
```

不要用 `-d 0` / `-d 2` 作为 `screencap` 参数，否则会报 invalid display id。设备处于 Dozing 时截图可能全黑，先执行：

```bash
adb -s "$DUO" shell input keyevent 224
adb -s "$DUO" shell wm dismiss-keyguard
```

### 6.3 日志

```bash
adb -s "$DUO" logcat -v time | grep -E "LumaDuo|AndroidRuntime|libmoonlight|EasyTier"
```

日志中不得出现配对 PIN、EasyTier `network_secret`、证书私钥、账号 token 或完整公网地址。

## 7. 常见问题

| 现象 | 处理 |
|---|---|
| `audioHapticsSdkDir is required` | 按 2.1 准备 SDK，并在 `local.properties` / 环境变量中指向它 |
| NDK 版本不匹配 | 确认 `ndk;28.2.13676358` 已安装，且 `local.properties` 的 `sdk.dir` 正确 |
| 子模块目录移动后编译失败 | 子模块 `.git` 文件里的 `gitdir` 可能仍是旧绝对路径，改成相对路径后重试 |
| 中文/空格路径导致 native 构建异常 | 把仓库移到纯 ASCII 路径，例如 `/opt/luma-duo` |
| 下屏 Compose 尺寸像手机 | 确认 `ComposeView` 使用 display-scoped `context`，不要传 Activity context |
| 下屏 Back 崩溃 `No OnBackPressedDispatcherOwner` | `Presentation` 的 decor 需要 `setViewTreeOnBackPressedDispatcherOwner(this)` |
| 构建 release 失败/无法安装 | 当前未配置正式签名；release 只用于本地检查，不对外发布 |
| Gradle 下载超时 | 配置代理或使用镜像；不要把代理凭据提交到仓库 |

## 8. 发布前置条件

- 配置正式签名，且 keystore 不进入仓库。
- 完整跑通：冷启动、配对、串流、直通/本地、实体手柄/键盘、EasyTier、退出/断开、30 分钟以上稳定性。
- 完成 GPL-3.0 / NOTICE / 第三方许可证审计。
- 用户明确测试通过后才打 tag、推送 release 或发布 APK。
