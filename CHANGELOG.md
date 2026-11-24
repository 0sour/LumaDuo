# Changelog

All notable changes to LumaDuo are recorded here.

## [Unreleased]

### Fixed

- Declare `android:label="@string/app_label"` on the `<application>` element.
  Launchers that read the application label instead of the activity alias no
  longer fall back to the package name and now show **LumaDuo**.
- Physical controller input now reaches the bottom shell: `DuoMainActivity`
  forwards key events to the bottom `Presentation` before its own fallback.
  With the connected Xbox controller, D-pad navigation, bottom confirm and
  right back were verified on device.
- Xbox-style ABXY mapping: raw capture shows bottom=`BUTTON_A` (96),
  right=`BUTTON_B` (97), left=`BUTTON_X` (99), top=`BUTTON_Y` (100).
  `DuoKeys` and the Activity/Presentation A/B interception now use that mapping.
- Retro-style ABXY normalization: the Retro built-in controller identifies as
  vendor `0x2022` / product `0x3001`; `DuoFaceButtonMapper` swaps A↔B and X↔Y
  only for that device. Verified with raw `BTN_EAST`/`BTN_GAMEPAD` injection:
  bottom confirms and right returns.
- Bottom-display discovery now ignores `STATE_OFF` displays.
- `DuoMainActivity` is `singleTop` and re-homes an instance that the bottom
  launcher started on display 2 back to display 0; verified with
  `am start --display 2`.

### Added

- Host page: all/paired/unpaired filter chips, online→paired→name ordering,
  multi-address selection with a real address sheet, and "restore last session"
  across any online host with a running app.
- Host actions: Sunshine WebUI opens the browser at the host's HTTPS Web UI;
  "create desktop shortcut" pins a real launcher shortcut via `ShortcutHelper`.
- Settings: a real frame-pacing/FEC page (pacing mode, host cadence sync, FEC
  mode/percentage, host render scale), controller-mouse speed/D-pad options,
  microphone button visibility/position/icon color, IPv6 jump to hosts, and a
  remote EasyTier "restore default values" action.
- Stream console input tab: controller-mouse-emulation toggle wired through
  `StreamSessionController` to `ControllerHandler`, plus a matching
  `DuoStreamState.controllerMouse` flag.
- Removed dead minimal-edition rows for USB and replaced the PiP toggle with an
  explicit D-043 deferred status so no visible control is a no-op.

### Changed

- App page: the app bar title is now simply "应用"; the current host and a
  horizontally scrollable host switch row live in the page body, so apps are
  always loaded and shown for one host at a time instead of being mixed
  together.
- App page: selecting an app now mirrors its real Sunshine box art to the top
  display instead of showing an enlarged initial; the initial tile remains only
  as a fallback while art is unavailable.
- Host detail no longer duplicates actions: the side panel is now the primary
  action plus one "主机操作" entry (and "应用列表" only when it is not already
  the primary). Re-pair, network test and delete live only inside the operations
  sheet, so the detail panel is no longer a button wall.
- Host app bar no longer repeats "进入应用列表"; the app-list entry lives in the
  detail panel. Tapping refresh now shows a transient "主机列表已刷新" notice.
- Repository cleanup for the first LumaDuo snapshot: removed V+ promo assets,
  store metadata, upstream docs, CI/release scripts and AppVeyor config; added
  the LumaDuo documentation set, clickable HTML prototype, icon sources,
  NOTICE/SECURITY/CONTRIBUTING and a complete GitHub README. Local device
  screenshots and private test data are intentionally excluded.

### Added

- Top-screen browsing pages now mirror the bottom selection with page-specific
  heroes: host address/status/tags plus a monitor visual, app initial tile,
  EasyTier network diagram with real connection state, and settings category
  summary with the first five current values. The layout uses a brand line,
  split copy/visual hero, and keeps the top surface read-only.
- Stream console tabs now drive real settings instead of placeholders: video
  (resolution, FPS, host screen mode, bitrate, adaptive bitrate, render scale,
  HDR, full-edition overlay), audio (local volume, mute, channels, passthrough,
  mic), input (input mode, touch mode, touchpad speed, gyro, rumble), and
  connection (address, loss/bandwidth, network test, reconnect, host sleep with
  confirmation). The status tab gained the prototype's STREAM HEALTH header and
  read-only badge.
- Toolkit touchpad now supports single-finger move/click, double-tap and
  long-press drag lock, two-finger scroll and two-finger tap right-click,
  backed by real mouse down/up/scroll engine calls. Win+L, sleep and exit from
  the shortcuts strip require confirmation.
- Host detail/more runs a real TCP network probe and shows a result overlay
  (3/3 Sunshine ports, min/average connect time) with retest; paired hosts can
  be re-paired from the more menu.
- Settings "帮助 / 诊断" is now a real diagnostics page: device, Android version,
  current window size, video decoder count, input device count, network-test
  shortcut and in-app setup guide.
- Settings advanced pages implemented enhanced-touch zones/divider, microphone
  volume processing/fixed gain, and gyro-to-mouse sensitivity plus X/Y invert.
- App actions show the host-provided command count alongside App ID and HDR
  support.
- Real host network probe in the stream console: the connection tab now runs
  a client-side TCP check against Sunshine ports 47984/47989/47990 and shows
  reachable port count plus min/average connect latency. It does not
  authenticate, read host data, or invent packet loss.
- Real host app icons: after the app list loads, the shell fetches Sunshine
  `appasset` box art lazily, caches it in memory, and renders it in the app
  grid/detail. Failed fetches keep the letter tile and never block launching.
- `StreamSessionController` / `StreamSnapshot` / `StreamMetrics` engine
  boundary: `Game` publishes real session phase, codec/HDR, configured and
  actual FPS, bitrate, RTT, host/decode/render latency and duration through a
  `StateFlow`; console actions now depend on the interface instead of the
  Activity, and unknown host CPU/GPU/RAM/packet-loss values stay `null`.
- Minimal `duo` product edition Phase B/C: GameMenu, Crown, virtual controller,
  float ball, USB driver/forwarding, audio haptics, performance overlays and
  frame generation are compile-time gated out of the minimal product and
  removed by R8.
- Native/asset trimming for duo: framegen, USBIP, haptics, iperf3 and
  `libc++_shared` are excluded from the APK; haptics audio assets move to the
  full-edition source set. Duo release is now 16.43 MB (-44.5% vs the original
  29.60 MB V+ release, -18.8% vs Phase A).
- Minimal manifest additionally removes `UsbDriverService`,
  `KeyboardAccessibilityService`, `ShizukuProvider`, `PosterContentProvider`
  and the Shizuku permission.
- Telemetry removal: Firebase/Crashlytics dependencies, Gradle plugin,
  initialization, analytics preference and provider marker are gone;
  `AnalyticsManager` is a local-only no-op facade, and the renderer exception
  test no longer depends on Crashlytics internals.
- Minimal settings/console cleanup: performance overlay, experimental,
  external display, native haptics, USB driver, virtual controller and audio
  vibration rows are hidden in the minimal edition.

### Changed

- Settings now follows the HTML prototype's flat information architecture
  (D-044): each category shows its direct option rows instead of an extra
  parent page. Video, audio, input, host and interface rows were rewritten to
  match the prototype order and wording; upstream-only tuning moved behind a
  single "高级" row per category, and the About page now opens the setup guide
  in-app as "帮助 / 诊断".
- The remote page now follows the prototype's card layout: one EasyTier config
  card with a status pill, inline 本机 ↔ EasyTier ↔ PC diagram, real node/latency
  stats (packet loss stays “—” because EasyTier exposes no loss field), and a
  full-width manual-host bar. Copy-PC-config and QR moved into the config card.
- Single-screen mode and PiP are deferred until the project is stable
  (D-043). Current acceptance is dual-screen RP Duo only; existing
  single-screen code is retained but is not a milestone or release gate.
- The minimal `duo` flavor now owns the production package
  `com.sour.lumaduo`; the full V+ edition moved to `com.sour.lumaduo.full`
  (`com.sour.lumaduo.full.debug` for debug). Upgrading the previous full debug
  install on RP Duo kept pairings and remote configuration.
- `Game` forces the LumaDuo console for every minimal-edition launch, including
  notification/external entry points.
- Duo release composition: dex 6.56 MB + 0.30 MB, resources.arsc 0.39 MB,
  EasyTier native + `libmoonlight-core` only; no Firebase permissions.

### Fixed

- The console/tools switcher and toolkit tabs had no width constraint, so the
  first tab filled the whole row and hid the other entries; both now use fixed
  72/76 dp targets, making 控制台/工具 and 触摸板/键盘/快捷键 reachable by touch.
- The bottom `Presentation` created its ComposeView with the Activity context, so
  Compose used the top screen's 369 dpi instead of the bottom panel's 320 dpi.
  The 1280×960 bottom display was therefore laid out as 555×416 dp, tripping the
  `<600 dp` compact breakpoint, shrinking the rail/app bar and hiding every
  app-bar subtitle. The ComposeView now uses the display-scoped context
  (640×480 dp, non-compact) and explicitly installs
  `ViewTreeOnBackPressedDispatcherOwner`; the shell app bar also places title
  and subtitle on one line, matching the prototype.
- Exiting a stream could crash `SecondScreenPresentation` with
  `State is 'DESTROYED' and cannot be moved to 'STARTED'`: `Dialog.dismiss()`
  delivered `onStop` after the lifecycle registry was already destroyed. The
  presentation now lets `super.dismiss()` deliver `onStop` while started,
  guards `onStop`, then emits `ON_DESTROY` and clears the ViewModelStore.
- RP Duo first smoke exposed a host-side Sunshine VDD issue:
  `display_device_prep=ensure_only_display` disables physical monitors during a
  session, and an abnormally ended session left all host screens black because
  Sunshine did not restore the topology. The client crash above is fixed; the
  host config was changed to `ensure_active` (backup
  `sunshine.conf.bak-20261121`) so physical displays stay active even if a
  session ends badly.
- `DuoStreamBridge` bandwidth/RTT parsing delegates to `StreamMetrics`, so the
  engine snapshot and product display cannot drift.
- NonRoot haptics tests remain flaky only under full parallel build load; a
  standalone run passes.

### Phase A baseline

- Minimal `duo` product edition: a third flavor in the existing dimension,
  arm64-only, with a manifest overlay that removes legacy V+ activities,
  widget/update receivers, camera/external-storage permissions and unused
  features. Duo release was 20.24 MB vs 29.60 MB previously.
- `MINIMAL_EDITION` product gates: all minimal-edition launches enter the
  LumaDuo console, legacy shell actions are hidden, open-source notices open
  directly, shortcuts/notifications target `DuoMainActivity`, and legacy
  settings rows are removed.
- Resource shrinking and `en`/`zh-rCN` locale filtering reduce
  `resources.arsc` from 3.73 MB to 0.51 MB in the duo release.

- Real dual-screen stream console: `Game` now hosts the bottom
  `SecondScreenPresentation` when launched from the LumaDuo shell, with a
  `DuoStreamBridge` that samples real FPS/RTT/latency/bitrate/duration and
  never fabricates host CPU/GPU/RAM.
- Window-focus input contract for streaming: passthrough uses
  `FLAG_NOT_FOCUSABLE`, local clears it and requests focus; touch enters local,
  A/Back returns to passthrough, and `onExternalGameMenuOpened/Dismissed`
  release/neutralize controller state.
- `GameDuoStreamActions` and `DuoStreamKeySender`: mouse buttons/motion, ASCII
  key names, modifier masks, reverse-release chords and the 12 toolkit
  shortcuts; disconnect now releases forwarded keys.
- Single-screen fallback: `DuoMainActivity` renders the shell in its own window
  when no presentation display exists; `Game` adds a 60% Compose console panel
  with the compact console layout.
- Compact shell/console breakpoints (<600dp rail 52dp, 40dp app bar, no
  subtitle), HAT-axis to D-pad focus navigation, and console initial focus.
- Initial project documentation: product scope, architecture, device matrix,
  build guide, roadmap, input contract, networking, testing, decisions,
  legal and security notes.
- UI design system: Lumen Gold accent, bili-duo-derived dark surfaces,
  focus ring, rail, ModeBar, typography and component rules.
- Screen specification for hosts, apps, remote networking, settings,
  stream console, pairing and single-screen mode.
- Clickable HTML prototype for the bottom-screen UI and stream console.
- Stream toolkit pane: full-width touchpad, ASCII keyboard and shortcuts,
  with gamepad focus, sticky modifiers, release semantics and Win+L confirmation.
- Read-only stream status tab, first and default in the console: client-measured
  FPS/latency/bitrate, network/device details, 60-second trend and optional host
  static info, with no focusable settings controls and no fabricated host load.
- Final app icon: UUYC A1 dual-screen gamepad mark recolored to all-gold
  Lumen Gold, with adaptive and legacy Android resources.
- Documentation pass: current design baseline, status-field data contract,
  icon-scaling rule, 2x2 status matrix spec, prototype self-check checklist and
  refreshed document index.
- M1 package migration: `com.sour.lumaduo` across app, flavors, tests, AIDL,
  JNI and native `FindClass`; EasyTier's `com.easytier.jni` namespace preserved.
- M1 branding: LumaDuo application label, version line `0.1.0`, adaptive and
  legacy launcher icons, splash background and A1 full-logo vector.
- M2a Compose foundation: Lumen dark theme tokens, typography, shapes, metrics,
  white focus ring, 64dp Rail, 44dp AppBar, 28dp ModeBar and a debug-only
  `DuoShellProbeActivity` for 640x480 and second-display layout checks.
- M0.5 `SecondScreenPresentation`: Lifecycle/ViewModelStore/SavedState/
  ActivityResult/BackDispatcher owners, Compose shell, read-only top probe.
- M0b real-device baseline: RP Duo + Foundation Sunshine, 1080p60 HEVC,
  ≈18 ms latency, 4 ms RTT, 0% loss, 20 Mbps configured bitrate.
- M0.5 real-device focus pass: bottom display 2 at 1280×960 @320dpi, Compose
  Presentation focus, D-pad ring, touch rail switching and Return interception.
- M2b-1 real host shell: ComputerManagerService snapshot API, DuoHostRepository,
  real host rows, AppView launch for paired hosts and legacy PcView fallback.
- V+ startup update prompt disabled for LumaDuo's independent version line.
- M2b-2 real app shell: direct read-only app-list fetch, 3-column grid,
  tap-to-stream through ServerHelper, loading/error states and legacy PcView
  fallback for unpaired hosts.
- M2b-3 real settings: `DuoSettingsRepository` over `PreferenceConfiguration`
  for resolution, frame rate, bitrate (Kbps<->Mbps), codec, HDR, adaptive
  bitrate, audio channels and rumble; scene writes use `writeScenePreferences`.
- M2b-3 real remote summary: `DuoRemoteRepository` reads the stored EasyTier
  TOML; network name, virtual IP, peers and listeners are shown, the secret is
  reduced to set/unset, and no runtime state is fabricated.
- M2b-3 production entry: `DuoMainActivity` hosts the read-only top screen and
  the bottom `SecondScreenPresentation`; launcher aliases now target it and
  `PcView` stays as the add/pair/EasyTier fallback.
- Shared `DuoShellScreen` used by both production and the debug probe; demo
  remote/settings pages removed.
- `LumaFocusable` gained directional-key and focus-change hooks; settings rows
  adjust with A/left/right and categories select on focus.
- Presentation intercepts gamepad `KEYCODE_BUTTON_B` in addition to Back and
  Escape; a top-window Back callback prevents legacy Activity key-ups from
  closing the dual-screen shell.
- UI v0.3 information architecture: `docs/ui/FEATURE-MAP.md` maps every major
  V+ browse/settings/Game Menu/input feature to one LumaDuo entry, with
  keep/merge/defer/drop decisions.
- Host page: list + detail panel, connect/resume, app list, PIN pairing, WOL,
  network test entry, delete confirmation, more-actions sheet (sleep, quit,
  WebUI, IPv6, unlock) and manual add with numeric keypad.
- App page: 3-column grid, detail strip, app action sheet (launch, launch with
  last settings, quit-and-launch, quit, hide/unhide, shortcut, info).
- Remote page: EasyTier start/stop with VPN consent, real TOML editor, advanced
  flags, PC `.2` TOML copy, QR export and manual virtual-IP host entry.
- 7 settings categories: 画面 / 音频 / 输入 / 网络 / 主机 / 界面 / 关于, with
  subpages for quality, display, HUD, experimental, audio, mic, gamepad,
  touch, keyboard/mouse, OSC, gyro and host behavior.
- Stream console UI: status (read-only, 2x2 matrix + trend), video, audio,
  input, connection tabs; console/toolkit dual pane; touchpad, ASCII keyboard,
  fixed shortcuts and action strip.
- Debug-only `DuoStreamPreviewActivity` for the console with an explicit
  "no session" state; no fake metrics.
- Shared in-window overlay/sheet/confirm components; focusable setting rows,
  numeric keypad, QR panel and slider rows.
- UI v0.3 build verification: `:app:assembleNonRootDebug` and
  `:app:assembleNonRootRelease` both pass. After the focus/display pass the debug
  APK is 49,646,178 B (`93cad755…19a7`) and unsigned release is 29,553,975 B
  (`87f86eb3…ea03`). Device installation, tests and visual checks were
  intentionally not run.
- Overlay focus now enters the panel on open; Back/Escape/Button-B route through
  the Presentation's `OnBackPressedDispatcher`, so overlays and settings
  subpages close before page navigation. Text fields keep B for typing while
  the IME owns the input connection.
- `docs/ui/DUO-PITFALLS.md`: consolidated bili-duo / UUYC-Duo lessons for
  Presentation hosting, bottom-launcher re-home, focus rings and scroll clipping,
  DPAD_CENTER double activation, IME state, incremental-build stale dex,
  release authorization and UUYC protocol constraints.
- Gamepad selection pass: host rows and app cards preview their details when
  focused, the active rail item's confirm press enters page content, the app grid
  drops to two columns below 600dp, and sliders are single focus targets with a
  visible ring and left/right step adjustment.
- Local input semantics now follow the documented Duo contract: physical B
  confirms and physical A returns across the new Compose shell; the bottom
  `Presentation` intercepts physical A as back and lets B reach the focus tree.
  Legacy V+ pages (PcView/Game Menu) intentionally remain A-confirm until the
  hardware silkscreen is confirmed.
- The bottom `Presentation` now delegates `ActivityResultRegistry` to its host
  Activity, so SAF/picker/permission launchers in the bottom Compose tree have a
  real registry instead of the previous no-op stub.
- `DuoFocusMemory` remembers the last focused `LumaFocusable`; closing an overlay
  posts a focus restore to the trigger. Host/app list rows and settings rows now
  carry transparent focus-ring clearance.
- Dual-screen display pass: a `DisplayListener` re-homes the bottom
  `Presentation` on display add/remove/change, excludes the Activity's own
  display, updates metrics live and retries after invalid-display/token errors.
  Both windows keep the screen awake; the bottom window is fullscreen with
  system bars hidden; the top idle screen now mirrors the selected host/app and
  only shows display metrics in debug builds.

### Changed

- Rejected bili-duo pink as LumaDuo's primary color; adopted Lumen Gold
  `#F2C14E` with signal cyan `#5ED0FF` as a secondary status color.
- Clarified that bili-duo's design language is inherited, but its pages and
  information architecture are not cloned.
- Stream console tabs reordered to put the read-only status page first and make
  it the default; host live CPU/GPU/RAM is no longer required, so the design
  does not depend on modifying Foundation.
- Prototype brand mark now inlines the final UUYC A1 all-gold SVG and scales it
  for the header, rail and top-screen brand line; no redrawn small-size variant
  and no external icon fetch. The favicon uses the same SVG as a data URI; the
  status note uses an info icon.
- Status details replaced four full-width rows with a 2x2 info matrix: icon,
  primary value and secondary detail per cell (screen/network/device/host).
- Settings expanded to 7 categories; "外观" became "界面", and "主机" now owns
  host-side behavior and clipboard settings.
- EasyTier generated hostname no longer contains `moonlight-V+`; new configs
  use `LumaDuo`.
- Debug shell probe replaced by the production shell + `DuoStreamPreviewActivity`
  for console layout review.
- Source checkout moved to an ASCII path; build guide records the
  Chinese-path failures, proxy port, submodule repairs and M1 artifact hashes.

### Not started

- M0.5 remaining: bottom IME, 538×469, display hotplug, single-screen fallback.
- M1 remaining device checks: audio, physical gamepad passthrough, EasyTier
  runtime, release signing/install.
- M3 remaining: wire `Game` session snapshot into the console, InputRouter and
  touchpad/keyboard/shortcut sinks, single-screen drawer.
- M4 remaining device verification: EasyTier start/stop, PC config copy/QR,
  virtual-IP host add.
