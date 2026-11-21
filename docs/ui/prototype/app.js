/* SPDX-License-Identifier: GPL-3.0-only */
'use strict';

/* ------------------------------------------------------------------ *
 * LumaDuo UI prototype
 * Static, clickable design reference. No network, no production code.
 * ------------------------------------------------------------------ */

const topScreen = document.getElementById('topScreen');
const bottomScreen = document.getElementById('bottomScreen');
const dualView = document.getElementById('dualView');
const singleView = document.getElementById('singleView');
const viewToggle = document.getElementById('viewToggle');

const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => Array.from(root.querySelectorAll(sel));
const icon = (name, cls = '') => `<svg class="icon ${cls}" aria-hidden="true"><use href="#i-${name}"></use></svg>`;
const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

function logoMark(cls = '') {
  return `<svg class="${cls}" viewBox="0 0 512 512" aria-hidden="true"><use href="#i-duo-logo-full"/></svg>`;
}

/* ----------------------------- data ----------------------------- */

const hosts = [
  {
    name: '书房台式机', addr: '192.168.1.20', state: 'online', paired: true,
    tag: 'LAN', last: '2 分钟前', gpu: 'RTX 4070', os: 'Windows 11',
  },
  {
    name: '客厅小主机', addr: '10.0.0.2', state: 'online', paired: false,
    tag: 'EasyTier', last: '昨天', gpu: 'RX 7800 XT', os: 'Windows 11',
  },
  {
    name: '卧室 PC', addr: '192.168.1.33', state: 'offline', paired: true,
    tag: 'LAN', last: '3 天前', gpu: 'RTX 3060', os: 'Windows 10',
  },
];

const apps = [
  { name: 'Steam 大屏', sub: 'Steam Big Picture', initial: 'S', color: '#7BA7C7', fg: '#0B1218' },
  { name: '桌面', sub: 'Windows Desktop', initial: 'D', color: '#9B9BA8', fg: '#121216' },
  { name: '赛博朋克 2077', sub: 'CD Projekt Red', initial: 'C', color: '#F2C14E', fg: '#1A1205' },
  { name: '极限竞速：地平线 5', sub: 'Forza Horizon 5', initial: 'F', color: '#4CC38A', fg: '#07140E' },
  { name: 'RetroArch', sub: 'Emulator Frontend', initial: 'R', color: '#5ED0FF', fg: '#06141B' },
  { name: 'Moonlight 测试', sub: 'Utility', initial: 'M', color: '#A78BFA', fg: '#120C1C' },
];

const settingsGroups = [
  {
    id: 'video', name: '画面', icon: 'monitor', desc: '画质、显示、HUD 与实验性',
    rows: [
      { id: 'res', title: '分辨率', desc: '串流输出尺寸', value: '1920 × 1080', options: ['1920 × 1080', '1280 × 720', '原生'] },
      { id: 'fps', title: '帧率', desc: '上屏刷新率', value: '60 Hz', options: ['60 Hz', '120 Hz', '30 Hz'] },
      { id: 'bitrate', title: '码率', desc: '网络带宽上限', value: '20 Mbps', options: ['20 Mbps', '50 Mbps', '100 Mbps'] },
      { id: 'abr', title: '智能码率', desc: '弱网自动调整', value: '关', on: false, toggle: true },
      { id: 'codec', title: '视频格式', desc: 'H.264 / HEVC / AV1', value: '自动', options: ['自动', 'HEVC', 'H.264', 'AV1'] },
      { id: 'hdr', title: 'HDR', desc: '需要主机与上屏同时支持', value: '关', on: false, toggle: true },
      { id: 'scale', title: '显示缩放', desc: '主机渲染缩放', value: '100%', options: ['100%', '75%', '50%'] },
      { id: 'overlay', title: '性能浮层', desc: '控制台显示实时统计', value: '关', on: false, toggle: true },
    ],
  },
  {
    id: 'audio', name: '音频', icon: 'volume', desc: '声道、直通、麦克风与振动',
    rows: [
      { id: 'channels', title: '声道', desc: '设备 PCM 能力内自动降级', value: '立体声', options: ['立体声', '5.1', '7.1', '7.1.4'] },
      { id: 'passthrough', title: '音频直通', desc: 'AC3 / EAC3 / Opus', value: '关', on: false, toggle: true },
      { id: 'mic', title: '麦克风', desc: '需要 Foundation 扩展', value: '关', on: false, toggle: true },
      { id: 'haptic', title: '音频振动', desc: '按游戏音频驱动触感', value: '关', on: false, toggle: true },
      { id: 'spatial', title: '空间音频', desc: '7.1.4（如设备支持）', value: '关', on: false, toggle: true },
    ],
  },
  {
    id: 'input', name: '输入', icon: 'gamepad', desc: '手柄、触控、键盘与虚拟手柄',
    rows: [
      { id: 'deadzone', title: '摇杆死区', desc: '左 / 右摇杆', value: '7%', options: ['0%', '7%', '12%', '20%'] },
      { id: 'rumble', title: '震动模式', desc: '主机震动去向', value: '仅手柄', options: ['智能分配', '仅本机', '仅手柄'] },
      { id: 'touchmode', title: '触摸模式', desc: '下屏触摸映射', value: '触控板', options: ['增强', '经典', '触控板', '原生'] },
      { id: 'padSpeed', title: '触控板速度', desc: '相对鼠标灵敏度', value: '1.0×', options: ['0.8×', '1.0×', '1.2×', '1.5×'] },
      { id: 'gyro', title: '陀螺仪', desc: '映射右摇杆或鼠标', value: '关', on: false, toggle: true },
      { id: 'osc', title: '虚拟手柄', desc: '屏幕按键层', value: '关', on: false, toggle: true },
      { id: 'escKey', title: 'ESC 菜单键', desc: '双击打开本地控制台', value: 'Esc', options: ['Esc', 'F1', 'F12'] },
    ],
  },
  {
    id: 'network', name: '网络', icon: 'globe', desc: 'EasyTier、STUN 与测速',
    rows: [
      { id: 'easytier', title: 'EasyTier', desc: '运行状态与虚拟 IP', value: '未启动' },
      { id: 'stun', title: 'STUN', desc: '解析主机公网地址', value: '关', on: false, toggle: true },
      { id: 'nettest', title: '网络测试', desc: '延迟 / 抖动 / 丢包 / 建议码率', value: '未测试' },
      { id: 'manual', title: '手动添加主机', desc: '局域网或虚拟 IP', value: '10.0.0.2' },
      { id: 'ipv6', title: 'IPv6', desc: '按主机启用；详情页高级项', value: '自动' },
    ],
  },
  {
    id: 'host', name: '主机', icon: 'host', desc: '主机端行为、后台与剪贴板',
    rows: [
      { id: 'sops', title: '主机优化', desc: 'Sunshine SOPS', value: '开', on: true, toggle: true },
      { id: 'screenmode', title: '屏幕组合', desc: '主机默认 / 主屏 / 副屏 / 独占', value: '主机默认', options: ['主机默认', '主屏', '副屏', '独占'] },
      { id: 'hostaudio', title: '主机音频', desc: '声音在 PC 播放', value: '关', on: false, toggle: true },
      { id: 'control', title: '仅控制流', desc: '不传输视频', value: '关', on: false, toggle: true },
      { id: 'quit', title: '退出行为', desc: '断开时是否停止主机应用', value: '停止应用', options: ['停止应用', '仅断开'] },
      { id: 'background', title: '后台串流', desc: '切后台后的连接策略', value: '断开', options: ['断开', '恢复', '保持', '保持+音频'] },
      { id: 'clipText', title: '剪贴板文本', desc: '双向同步；不做文件传输', value: '开', on: true, toggle: true },
      { id: 'clipImage', title: '剪贴板图片', desc: '本机到主机', value: '关', on: false, toggle: true },
    ],
  },
  {
    id: 'interface', name: '界面', icon: 'sliders', desc: '语言、控制台与状态页',
    rows: [
      { id: 'language', title: '语言', desc: 'MVP 锁定简体中文', value: '简体中文' },
      { id: 'theme', title: '主题', desc: '深色 + Lumen Gold 锁定', value: '深色' },
      { id: 'statusItems', title: '状态页指标', desc: '帧率/延迟/码率/RTT/丢包', value: '默认 5 项' },
      { id: 'hudStyle', title: '浮层样式', desc: '迷你 / 标准 / 详细', value: '标准', options: ['迷你', '标准', '详细'] },
      { id: 'consoleOpacity', title: '控制台不透明度', desc: '直通 60% / 本地 100%', value: '固定' },
    ],
  },
  {
    id: 'about', name: '关于', icon: 'info', desc: '版本、上游、许可与诊断',
    rows: [
      { id: 'version', title: '版本', desc: 'LumaDuo', value: '0.1.0' },
      { id: 'upstream', title: '上游', desc: '串流客户端基线', value: 'Moonlight V+ 12.15.17' },
      { id: 'license', title: '许可证', desc: '项目整体', value: 'GPL-3.0' },
      { id: 'notices', title: '开源声明', desc: '上游与素材许可', value: '查看' },
      { id: 'help', title: '帮助 / 诊断', desc: '能力、手柄、网络诊断', value: '打开' },
      { id: 'update', title: '更新', desc: '尚未配置独立更新源', value: '暂不可用' },
      { id: 'privacy', title: '隐私', desc: '无遥测、无 Crashlytics', value: '说明' },
    ],
  },
];

const streamTabs = [
  { id: 'status', name: '状态' },
  { id: 'video', name: '画面' },
  { id: 'audio', name: '音频' },
  { id: 'input', name: '输入' },
  { id: 'connection', name: '连接' },
];

const toolTabs = [
  { id: 'touchpad', name: '触摸板', icon: 'mouse' },
  { id: 'keyboard', name: '键盘', icon: 'keyboard' },
  { id: 'shortcuts', name: '快捷键', icon: 'bolt' },
];

const shortcuts = [
  { label: 'Ctrl + C', name: '复制' },
  { label: 'Ctrl + V', name: '粘贴' },
  { label: 'Ctrl + X', name: '剪切' },
  { label: 'Ctrl + Z', name: '撤销' },
  { label: 'Ctrl + A', name: '全选' },
  { label: 'Alt + Tab', name: '切换窗口' },
  { label: 'Win + D', name: '显示桌面' },
  { label: 'Win + E', name: '文件管理器' },
  { label: 'Esc', name: '退出 / 取消' },
  { label: 'Tab', name: '切换焦点' },
  { label: 'Ctrl + Shift + Esc', name: '任务管理器' },
  { label: 'Win + L', name: '锁定电脑', danger: true },
];

const kbMainRows = [
  [
    { l: 'Esc', w: 1.25, k: 'Escape', t: 'special' },
    { l: '1', w: 1, k: '1' }, { l: '2', w: 1, k: '2' }, { l: '3', w: 1, k: '3' },
    { l: '4', w: 1, k: '4' }, { l: '5', w: 1, k: '5' }, { l: '6', w: 1, k: '6' },
    { l: '7', w: 1, k: '7' }, { l: '8', w: 1, k: '8' }, { l: '9', w: 1, k: '9' },
    { l: '0', w: 1, k: '0' }, { l: '-', w: 1, k: 'Minus' }, { l: '=', w: 1, k: 'Equal' },
    { l: '⌫', w: 1.25, k: 'Backspace', t: 'special' },
  ],
  [
    { l: 'Tab', w: 1.25, k: 'Tab', t: 'special' },
    { l: 'Q', w: 1, k: 'Q' }, { l: 'W', w: 1, k: 'W' }, { l: 'E', w: 1, k: 'E' },
    { l: 'R', w: 1, k: 'R' }, { l: 'T', w: 1, k: 'T' }, { l: 'Y', w: 1, k: 'Y' },
    { l: 'U', w: 1, k: 'U' }, { l: 'I', w: 1, k: 'I' }, { l: 'O', w: 1, k: 'O' },
    { l: 'P', w: 1, k: 'P' }, { l: '[', w: 1, k: 'BracketLeft' },
    { l: ']', w: 1, k: 'BracketRight' }, { l: '\\', w: 1.15, k: 'Backslash' },
  ],
  [
    { l: 'Caps', w: 1.25, k: 'CapsLock', t: 'special' },
    { l: 'A', w: 1, k: 'A' }, { l: 'S', w: 1, k: 'S' }, { l: 'D', w: 1, k: 'D' },
    { l: 'F', w: 1, k: 'F' }, { l: 'G', w: 1, k: 'G' }, { l: 'H', w: 1, k: 'H' },
    { l: 'J', w: 1, k: 'J' }, { l: 'K', w: 1, k: 'K' }, { l: 'L', w: 1, k: 'L' },
    { l: ';', w: 1, k: 'Semicolon' }, { l: "'", w: 1, k: 'Quote' },
    { l: 'Enter', w: 1.7, k: 'Enter', t: 'special' },
  ],
  [
    { l: 'Shift', w: 1.7, k: 'Shift', t: 'mod' },
    { l: 'Z', w: 1, k: 'Z' }, { l: 'X', w: 1, k: 'X' }, { l: 'C', w: 1, k: 'C' },
    { l: 'V', w: 1, k: 'V' }, { l: 'B', w: 1, k: 'B' }, { l: 'N', w: 1, k: 'N' },
    { l: 'M', w: 1, k: 'M' }, { l: ',', w: 1, k: 'Comma' }, { l: '.', w: 1, k: 'Period' },
    { l: '/', w: 1, k: 'Slash' }, { l: 'Shift', w: 1.7, k: 'Shift', t: 'mod' },
  ],
  [
    { l: 'Ctrl', w: 1.25, k: 'Ctrl', t: 'mod' },
    { l: 'Win', w: 1, k: 'Win', t: 'mod' },
    { l: 'Alt', w: 1.25, k: 'Alt', t: 'mod' },
    { l: '?123', w: 1.2, k: 'symbols', t: 'mod' },
    { l: 'Space', w: 4.5, k: 'Space', t: 'space' },
    { l: '←', w: 1, k: 'ArrowLeft', t: 'special' },
    { l: '↑', w: 1, k: 'ArrowUp', t: 'special' },
    { l: '↓', w: 1, k: 'ArrowDown', t: 'special' },
    { l: '→', w: 1, k: 'ArrowRight', t: 'special' },
  ],
];

const kbSymbolRows = [
  [
    { l: 'Esc', w: 1.25, k: 'Escape', t: 'special' },
    { l: '!', w: 1, k: '1' }, { l: '@', w: 1, k: '2' }, { l: '#', w: 1, k: '3' },
    { l: '$', w: 1, k: '4' }, { l: '%', w: 1, k: '5' }, { l: '^', w: 1, k: '6' },
    { l: '&', w: 1, k: '7' }, { l: '*', w: 1, k: '8' }, { l: '(', w: 1, k: '9' },
    { l: ')', w: 1, k: '0' }, { l: '⌫', w: 1.25, k: 'Backspace', t: 'special' },
  ],
  [
    { l: 'Tab', w: 1.25, k: 'Tab', t: 'special' },
    { l: '_', w: 1, k: 'Minus' }, { l: '+', w: 1, k: 'Equal' },
    { l: '[', w: 1, k: 'BracketLeft' }, { l: ']', w: 1, k: 'BracketRight' },
    { l: '{', w: 1, k: 'BracketLeft' }, { l: '}', w: 1, k: 'BracketRight' },
    { l: '|', w: 1, k: 'Backslash' }, { l: '\\', w: 1.15, k: 'Backslash' },
  ],
  [
    { l: 'Caps', w: 1.25, k: 'CapsLock', t: 'special' },
    { l: ';', w: 1, k: 'Semicolon' }, { l: ':', w: 1, k: 'Semicolon' },
    { l: "'", w: 1, k: 'Quote' }, { l: '"', w: 1, k: 'Quote' },
    { l: ',', w: 1, k: 'Comma' }, { l: '.', w: 1, k: 'Period' },
    { l: '/', w: 1, k: 'Slash' }, { l: 'Enter', w: 1.7, k: 'Enter', t: 'special' },
  ],
  [
    { l: 'Shift', w: 1.7, k: 'Shift', t: 'mod' },
    { l: '~', w: 1, k: 'Backquote' }, { l: '<', w: 1, k: 'Comma' },
    { l: '>', w: 1, k: 'Period' }, { l: '?', w: 1, k: 'Slash' },
    { l: '×', w: 1, k: 'Multiply' }, { l: '÷', w: 1, k: 'Divide' },
    { l: 'Shift', w: 1.7, k: 'Shift', t: 'mod' },
  ],
  [
    { l: 'Ctrl', w: 1.25, k: 'Ctrl', t: 'mod' },
    { l: 'Win', w: 1, k: 'Win', t: 'mod' },
    { l: 'Alt', w: 1.25, k: 'Alt', t: 'mod' },
    { l: 'ABC', w: 1.2, k: 'symbols', t: 'mod' },
    { l: 'Space', w: 4.5, k: 'Space', t: 'space' },
    { l: '←', w: 1, k: 'ArrowLeft', t: 'special' },
    { l: '↑', w: 1, k: 'ArrowUp', t: 'special' },
    { l: '↓', w: 1, k: 'ArrowDown', t: 'special' },
    { l: '→', w: 1, k: 'ArrowRight', t: 'special' },
  ],
];

const streamRows = {
  video: [
    { title: '分辨率', desc: '当前会话输出尺寸', value: '1920 × 1080', options: ['1920 × 1080', '1280 × 720', '原生'] },
    { title: '帧率', desc: '上屏刷新率', value: '60 Hz', options: ['60 Hz', '120 Hz', '30 Hz'] },
    { title: '屏幕模式', desc: '主机 / 虚拟显示器', value: '主机默认', options: ['主机默认', '主屏', '副屏', '独占'] },
    { title: '码率', desc: '当前会话上限', value: '20 Mbps', options: ['20 Mbps', '50 Mbps', '100 Mbps'] },
    { title: '智能码率', desc: '弱网自动调整', value: '关', options: ['关', '开'] },
    { title: '显示缩放', desc: '主机渲染缩放', value: '100%', options: ['100%', '75%', '50%'] },
    { title: 'HDR', desc: '真实 HDR 需重连', value: '关', options: ['关', 'HDR10', 'HLG'] },
    { title: '性能浮层', desc: '隐藏 / 浮动 / 锁定', value: '隐藏', options: ['隐藏', '浮动', '锁定'] },
  ],
  audio: [
    { title: '本机音量', desc: '下屏设备音量', value: '80%', options: ['80%', '60%', '100%'] },
    { title: '静音', desc: '保留视频，关闭本机音频', value: '关', options: ['关', '开'] },
    { title: '声道', desc: '切换需要重连', value: '立体声', options: ['立体声', '5.1', '7.1', '7.1.4'] },
    { title: '音频直通', desc: 'AC3 / EAC3 / Opus', value: '关', options: ['关', '开'] },
    { title: '麦克风', desc: '设备与码率', value: '关', options: ['关', '开'] },
    { title: '音频振动', desc: '强度 / 路由 / 场景', value: '关', options: ['关', '开'] },
  ],
  input: [
    { title: '输入模式', desc: '手柄输入去向', value: '本地', action: 'toggle-mode' },
    { title: '触摸模式', desc: '下屏触摸映射', value: '触控板', options: ['增强', '经典', '触控板', '原生'] },
    { title: '触控板速度', desc: '相对鼠标灵敏度', value: '1.0×', options: ['0.8×', '1.0×', '1.2×', '1.5×'] },
    { title: '陀螺仪', desc: '映射右摇杆或鼠标', value: '关', options: ['关', '开'] },
    { title: '虚拟手柄', desc: '显示 / 布局 / 透明度', value: '关', options: ['关', '开'] },
    { title: '手柄鼠标', desc: '摇杆模拟鼠标', value: '关', options: ['关', '开'] },
    { title: '震动', desc: '智能 / 仅本机 / 仅手柄', value: '仅手柄', options: ['智能分配', '仅本机', '仅手柄'] },
  ],
  connection: [
    { title: '主机', desc: '当前串流目标', value: '书房台式机' },
    { title: '地址', desc: 'LAN / EasyTier', value: '192.168.1.20' },
    { title: '延迟 / RTT', desc: '最近 5 秒平均', value: '18 ms / 4 ms' },
    { title: '丢包 / 带宽', desc: '最近 5 秒平均', value: '0.0% / 20 Mbps' },
    { title: 'EasyTier', desc: '运行状态与虚拟 IP', value: '未启动' },
    { title: 'USB 设备', desc: '共享 / 释放 / 自动共享', value: '0 个' },
    { title: '网络测试', desc: '重新测速并建议码率', value: '未测试' },
    { title: '断开 / 退出', desc: '确认后回到应用页', value: '操作' },
  ],
};

const singleTabs = [
  { id: 'status', name: '状态' },
  { id: 'video', name: '画面' },
  { id: 'audio', name: '音频' },
  { id: 'input', name: '输入' },
  { id: 'connection', name: '连接' },
];

const state = {
  page: 'hosts',
  hostIndex: 0,
  appIndex: 0,
  settingsCat: 0,
  streamTab: 'status',
  streamMode: 'local',
  streamPane: 'console',
  toolTab: 'touchpad',
  padSensitivity: 1,
  padDragLock: false,
  kbSymbols: false,
  kbMods: [],
  kbLast: '',
  easyRunning: true,
  overlay: null,
  overlayReturn: null,
  overlayOrigin: null,
  view: 'dual',
  singleTab: 'status',
  singleCollapsed: false,
  focusId: 'host-0',
  editingHost: null,
  addIp: '10.0.0.2',
  pin: '4821',
  toastTimer: null,
};

/* --------------------------- helpers --------------------------- */

const online = (h) => h && h.state === 'online';
const statusWord = (h) => (online(h) ? '在线' : '离线');
const hostAt = () => hosts[state.hostIndex] || hosts[0] || null;
const appAt = () => apps[state.appIndex] || apps[0] || null;
const settingGroup = () => settingsGroups[state.settingsCat] || settingsGroups[0];

function pill(text, tone = '') {
  return `<span class="pill ${tone}">${text}</span>`;
}

function modeBar({ dot = 'gold', label = '手柄：下屏', hint = 'B 确认 · A 返回', keys = [['B', '确认'], ['A', '返回']] } = {}) {
  return `<footer class="modebar">
    <span class="dot ${dot}"></span>
    <span class="label">${label}</span>
    <span class="hint">${hint}</span>
    <span class="keys">${keys.map(([k, t]) => `<span><kbd>${k}</kbd>${t}</span>`).join('')}</span>
  </footer>`;
}

function appBar(title, sub, actions = []) {
  return `<header class="appbar">
    <div class="appbar-title"><h1>${title}</h1>${sub ? `<span class="sub">${sub}</span>` : ''}</div>
    <div class="appbar-actions">${actions.map((a) => {
      const nav = ['up', 'down', 'left', 'right'].map((d) => a[d] ? ` data-nav-${d}="${a[d]}"` : '').join('');
      return `<button class="icon-btn focusable" data-action="${a.action}" data-focus="${a.focus}"${nav} aria-label="${a.label}">${icon(a.icon)}</button>`;
    }).join('')}</div>
  </header>`;
}

function railTemplate() {
  const items = [
    ['hosts', 'host', '主机'],
    ['apps', 'apps', '应用'],
    ['remote', 'globe', '远程'],
    ['settings', 'sliders', '设置'],
  ];
  return `<aside class="rail">
    <div class="rail-logo">${logoMark()}</div>
    ${items.map(([page, ic, label], i) => {
      const right = page === 'hosts'
        ? (hosts.length ? `host-${state.hostIndex}` : 'host-add')
        : page === 'apps' ? 'app-0'
        : page === 'remote' ? 'remote-toggle'
        : `cat-${settingGroup().id}`;
      const up = i > 0 ? `rail-${items[i - 1][0]}` : '';
      const down = i < items.length - 1 ? `rail-${items[i + 1][0]}` : '';
      return `<button class="rail-item focusable ${state.page === page ? 'active' : ''}" data-page="${page}" data-focus="rail-${page}" data-nav-right="${right}"${up ? ` data-nav-up="${up}"` : ''}${down ? ` data-nav-down="${down}"` : ''} aria-label="${label}">
        ${icon(ic)}<span>${label}</span>
      </button>`;
    }).join('')}
    <div class="rail-spacer"></div>
    <div class="rail-theme">${icon('moon')}<span>深色</span></div>
  </aside>`;
}

function videoScene() {
  return `<div class="video-scene" aria-hidden="true">
    <div class="scene-sun"></div>
    <div class="scene-ridge"></div>
    <div class="scene-ridge r2"></div>
    <div class="scene-window"></div>
    <div class="scene-hudline"></div>
  </div>`;
}

/* --------------------------- top screen --------------------------- */

function topTemplate() {
  if (state.page === 'stream') return videoScene();
  const h = hostAt();
  if (state.page === 'apps') {
    const a = appAt();
    return `<div class="top-preview">
      <div class="top-brandline">${logoMark()}<span>LumaDuo</span></div>
      <div class="top-center">
        <div class="top-app-tile" style="background:${a.color};color:${a.fg}">${a.initial}</div>
        <div>
          <div class="eyebrow">${h ? esc(h.name) : '未选择主机'} · 应用</div>
          <h2>${esc(a.name)}</h2>
          <p>${esc(a.sub)} · 在下屏选择并启动</p>
        </div>
      </div>
      <div class="top-ghost" style="color:${a.color}">${a.initial}</div>
    </div>`;
  }
  if (state.page === 'remote') {
    return `<div class="top-preview">
      <div class="top-brandline">${logoMark()}<span>LumaDuo · EasyTier</span></div>
      <div class="top-net">
        <div class="net-line l1"></div><div class="net-line l2"></div>
        <div class="net-node left">${icon('gamepad')}<span>LumaDuo</span></div>
        <div class="net-cloud"><strong>EasyTier</strong><span>${state.easyRunning ? '已连接 · 2 节点' : '未启动'}</span></div>
        <div class="net-node right">${icon('monitor')}<span>Foundation</span></div>
        ${state.easyRunning ? '<span class="net-packet p1"></span><span class="net-packet p2"></span>' : ''}
      </div>
      <div class="top-host-card" style="left:50%;bottom:20px;transform:translateX(-50%);text-align:center;max-width:none">
        <div class="eyebrow" style="margin-bottom:6px">虚拟网络</div>
        <p style="font-family:var(--mono);font-size:13px;color:var(--text)">10.0.0.1/24 <span style="color:var(--text-tertiary)">↔</span> 10.0.0.2</p>
      </div>
    </div>`;
  }
  if (state.page === 'settings') {
    const g = settingGroup();
    return `<div class="top-preview top-settings">
      <div class="top-brandline">${logoMark()}<span>LumaDuo · 设置</span></div>
      <div class="top-center">
        <div>
          <div class="eyebrow">设置分类</div>
          <h2>${g.name}</h2>
          <p>${g.desc}</p>
        </div>
        <div class="top-settings-list">
          ${g.rows.slice(0, 5).map((r) => `<span>${r.title} · ${r.toggle ? (r.on ? '开' : '关') : r.value}</span>`).join('')}
        </div>
      </div>
      <div class="top-ghost-icon">${icon(g.icon)}</div>
    </div>`;
  }
  if (!h) {
    return `<div class="top-preview">
      <div class="top-brandline">${logoMark()}<span>LumaDuo</span></div>
      <div class="top-center"><div><div class="eyebrow">还没有主机</div><h2>先添加一台 PC</h2><p>在下屏使用 + 添加主机，或连接 EasyTier。</p></div></div>
    </div>`;
  }
  return `<div class="top-preview">
    <div class="top-brandline">${logoMark()}<span>LumaDuo</span></div>
    <div class="top-host-card">
      <div class="eyebrow">当前主机</div>
      <h2>${esc(h.name)}</h2>
      <p>${esc(h.addr)} · ${statusWord(h)} · ${h.paired ? '已配对' : '未配对'}</p>
      <div class="top-tags">
        ${pill(h.tag, h.tag === 'EasyTier' ? 'cyan' : 'gold')}
        ${h.paired ? pill('证书就绪', 'green') : pill('需要配对')}
      </div>
      <div class="top-hint">在下屏选择或操作 · 上屏不接收输入</div>
    </div>
    <div class="top-visual" aria-hidden="true">
      <div class="monitor-shape"></div>
      <div class="monitor-base"></div>
      <span class="signal-orb one"></span>
      <span class="signal-orb two"></span>
    </div>
  </div>`;
}

/* ----------------------------- browse pages ----------------------------- */

function hostRow(h, i) {
  const right = online(h) && h.paired ? 'host-connect' : 'host-pair';
  const up = i > 0 ? `host-${i - 1}` : 'host-add';
  const down = i < hosts.length - 1 ? `host-${i + 1}` : right;
  return `<button class="host-row focusable ${i === state.hostIndex ? 'selected' : ''}" data-action="select-host" data-index="${i}" data-focus="host-${i}" data-nav-up="${up}" data-nav-down="${down}" data-nav-right="${right}" data-nav-left="rail-hosts">
    <span class="status-dot ${online(h) ? 'online' : 'offline'}"></span>
    <span class="host-copy">
      <span class="host-name">${esc(h.name)}</span>
      <span class="host-meta">${esc(h.addr)} · ${h.paired ? '已配对' : '未配对'}</span>
    </span>
    <span class="host-side"><span class="tag">${h.tag}</span></span>
  </button>`;
}

function hostDetail(h) {
  if (!h) {
    return `<div class="card host-detail empty-state">${icon('host', 'xl')}<strong>还没有主机</strong><p>使用右上角 + 手动添加，或前往“远程”配置 EasyTier。</p></div>`;
  }
  const canConnect = online(h) && h.paired;
  const row = `host-${state.hostIndex}`;
  return `<div class="card host-detail">
    <div class="detail-top">
      <div><h2 class="detail-name">${esc(h.name)}</h2><div class="detail-addr">${esc(h.addr)}</div></div>
      <span class="status-dot ${online(h) ? 'online' : 'offline'}" title="${statusWord(h)}"></span>
    </div>
    <div class="detail-tags">
      ${pill(statusWord(h), online(h) ? 'green' : '')}
      ${h.paired ? pill('已配对', 'gold') : pill('未配对')}
      <span class="tag">${h.tag}</span>
    </div>
    <div class="detail-rows">
      <div class="detail-row"><span class="k">最近连接</span><span class="v">${h.last}</span></div>
      <div class="detail-row"><span class="k">显卡</span><span class="v">${h.gpu}</span></div>
      <div class="detail-row"><span class="k">系统</span><span class="v">${h.os}</span></div>
    </div>
    <div class="detail-actions">
      <button class="btn primary connect focusable" data-action="connect-host" data-focus="host-connect" data-nav-left="${row}" data-nav-down="host-pair" ${canConnect ? '' : 'disabled'}>${canConnect ? '继续串流' : (!h.paired ? '先配对' : 'PC 离线')}</button>
      <button class="btn focusable" data-action="pair-host" data-focus="host-pair" data-nav-left="${row}" data-nav-up="host-connect" data-nav-right="host-apps" data-nav-down="host-test">${h.paired ? '重新配对' : '配对'}</button>
      <button class="btn focusable" data-action="host-apps" data-focus="host-apps" data-nav-left="${row}" data-nav-up="host-pair" data-nav-down="host-test" ${online(h) ? '' : 'disabled'}>应用列表</button>
      <button class="btn ghost focusable" data-action="network-test" data-focus="host-test" data-nav-left="${row}" data-nav-up="host-apps" data-nav-right="host-info" data-nav-down="host-more">网络测试</button>
      <button class="btn ghost focusable" data-action="host-info" data-focus="host-info" data-nav-left="${row}" data-nav-up="host-apps" data-nav-down="host-more">详情</button>
      <button class="btn ghost focusable" data-action="host-more" data-focus="host-more" data-nav-left="${row}" data-nav-up="host-test" data-nav-right="host-delete">更多</button>
      <button class="btn ghost danger focusable" data-action="delete-host" data-focus="host-delete" data-nav-left="host-more" data-nav-up="host-info">删除</button>
    </div>
  </div>`;
}

function hostsTemplate() {
  const h = hostAt();
  const content = `<div class="hosts-layout">
    <div class="host-list scroll">${hosts.map(hostRow).join('')}</div>
    ${hostDetail(h)}
  </div>`;
  return `<div class="ui-shell">
    ${railTemplate()}
    <div class="main">
      ${appBar('主机', `${hosts.length} 台设备`, [
        { icon: 'plus', action: 'open-add', focus: 'host-add', label: '添加主机', down: 'host-0', right: 'host-refresh' },
        { icon: 'refresh', action: 'refresh', focus: 'host-refresh', label: '刷新', down: 'host-0', left: 'host-add' },
      ])}
      <div class="content">${content}</div>
      ${modeBar({ label: '手柄：下屏', hint: '选择主机 · B 连接', keys: [['B', '连接'], ['A', '返回']] })}
    </div>
  </div>`;
}

function appCard(a, i) {
  const col = i % 3;
  const row = Math.floor(i / 3);
  const left = col > 0 ? `app-${i - 1}` : 'rail-apps';
  const right = col < 2 ? `app-${i + 1}` : '';
  const up = row > 0 ? `app-${i - 3}` : 'apps-refresh';
  const down = row < 1 ? `app-${i + 3}` : 'app-launch';
  return `<button class="app-card focusable ${i === state.appIndex ? 'selected' : ''}" data-action="select-app" data-index="${i}" data-focus="app-${i}" data-nav-left="${left}"${right ? ` data-nav-right="${right}"` : ''} data-nav-up="${up}" data-nav-down="${down}">
    <span class="app-icon" style="background:${a.color};color:${a.fg}">${a.initial}</span>
    <span class="app-copy"><span class="app-name">${esc(a.name)}</span><span class="app-sub">${esc(a.sub)}</span></span>
  </button>`;
}

function appsTemplate() {
  const h = hostAt();
  const a = appAt();
  const canLaunch = h && online(h) && a;
  const content = `<div class="apps-layout">
    <div class="app-grid scroll">${apps.map(appCard).join('')}</div>
    <div class="app-detail">
      <span class="app-icon" style="background:${a.color};color:${a.fg}">${a.initial}</span>
      <span class="copy"><span class="name">${esc(a.name)}</span><span class="meta">${esc(a.sub)} · ${h ? esc(h.name) : '未选择主机'}</span></span>
      <button class="btn primary focusable" data-action="launch-app" data-focus="app-launch" data-nav-up="app-5" data-nav-left="app-4" ${canLaunch ? '' : 'disabled'}>${icon('play')}启动串流</button>
      <button class="btn ghost focusable" data-action="open-app-actions" data-focus="app-actions" data-nav-up="app-launch" data-nav-left="app-4">${icon('more')}应用操作</button>
    </div>
  </div>`;
  return `<div class="ui-shell">
    ${railTemplate()}
    <div class="main">
      ${appBar(h ? esc(h.name) : '应用', h ? '应用 · ' + statusWord(h) : '应用', [
        { icon: 'refresh', action: 'refresh', focus: 'apps-refresh', label: '刷新应用', down: 'app-0' },
      ])}
      <div class="content">${content}</div>
      ${modeBar({ label: '手柄：下屏', hint: '选择应用 · B 启动', keys: [['B', '启动'], ['A', '返回']] })}
    </div>
  </div>`;
}

function remoteTemplate() {
  const running = state.easyRunning;
  const content = `<div class="remote-layout">
    <section class="card remote-config">
      <div class="card-head">
        <div><h3 class="card-title">EasyTier</h3><div class="card-sub">Android 内置客户端</div></div>
        ${pill(running ? '已连接' : '未启动', running ? 'green' : '')}
      </div>
      <div class="net-rows">
        <div class="net-row"><span class="k">网络名</span><span class="v">family-net</span></div>
        <div class="net-row"><span class="k">网络密钥</span><span class="v">••••••••••••</span></div>
        <div class="net-row"><span class="k">本机虚拟 IP</span><span class="v">10.0.0.1/24</span></div>
        <div class="net-row"><span class="k">Peer</span><span class="v">public.easytier.top:11010</span></div>
        <div class="net-row"><span class="k">PC 虚拟 IP</span><span class="v">10.0.0.2/24</span></div>
      </div>
      <div class="net-actions">
        <button class="btn ${running ? 'ghost' : 'primary'} focusable" data-action="toggle-easytier" data-focus="remote-toggle" data-nav-right="remote-copy" data-nav-down="remote-copy">${icon(running ? 'x' : 'play')}${running ? '停止 EasyTier' : '启动 EasyTier'}</button>
        <button class="btn focusable" data-action="copy-pc" data-focus="remote-copy" data-nav-left="remote-toggle" data-nav-right="remote-qr" data-nav-up="remote-toggle" data-nav-down="remote-manual-input">${icon('copy')}复制 PC 配置</button>
        <button class="btn focusable" data-action="open-qr" data-focus="remote-qr" data-nav-left="remote-copy" data-nav-up="remote-toggle" data-nav-down="remote-manual-add">${icon('qr')}二维码</button>
      </div>
    </section>

    <section class="card remote-status">
      <div class="remote-status-head">
        <div><div class="eyebrow">连接状态</div><div class="status-big"><strong>${running ? '已连接' : '未启动'}</strong><span>${running ? 'P2P · 18 ms' : '等待启动'}</span></div></div>
        <span class="status-dot ${running ? 'online' : 'offline'}"></span>
      </div>
      <div class="net-diagram">
        <div class="diagram-node left">${icon('gamepad')}<span>本机</span></div>
        <div class="diagram-cloud"><strong>EasyTier</strong><span>${running ? 'virtual LAN' : 'offline'}</span></div>
        <div class="diagram-node right">${icon('monitor')}<span>PC</span></div>
        ${running ? '<span class="diagram-dot d1"></span><span class="diagram-dot d2"></span>' : ''}
      </div>
      <div class="status-stats">
        <div class="status-stat"><div class="k">节点</div><div class="v">${running ? '2' : '0'}</div></div>
        <div class="status-stat"><div class="k">延迟</div><div class="v">${running ? '18 ms' : '—'}</div></div>
        <div class="status-stat"><div class="k">丢包</div><div class="v">${running ? '0.0%' : '—'}</div></div>
      </div>
    </section>

    <section class="card remote-manual">
      <div class="manual-label"><strong>手动添加主机</strong><span>PC 端 EasyTier 使用 .2</span></div>
      <button class="fake-input focusable" data-action="open-add" data-focus="remote-manual-input" data-nav-up="remote-copy" data-nav-right="remote-manual-add">10.0.0.2<span class="caret"></span></button>
      <button class="btn primary focusable" data-action="open-add" data-focus="remote-manual-add" data-nav-left="remote-manual-input" data-nav-up="remote-qr">${icon('plus')}添加</button>
    </section>
  </div>`;
  return `<div class="ui-shell">
    ${railTemplate()}
    <div class="main">
      ${appBar('远程', 'EasyTier · 虚拟局域网', [
        { icon: 'refresh', action: 'refresh', focus: 'remote-refresh', label: '刷新网络状态', down: 'remote-toggle' },
      ])}
      <div class="content">${content}</div>
      ${modeBar({ label: '手柄：下屏', hint: 'B 操作 · A 返回', keys: [['B', '操作'], ['A', '返回']] })}
    </div>
  </div>`;
}

function settingRow(row, i, group) {
  const action = row.toggle ? 'toggle-setting' : (row.options ? 'cycle-setting' : 'setting-info');
  const value = row.toggle ? (row.on ? '开' : '关') : row.value;
  const up = i > 0 ? `setting-${group.id}-${i - 1}` : `cat-${group.id}`;
  const down = i < group.rows.length - 1 ? `setting-${group.id}-${i + 1}` : '';
  const self = `setting-${group.id}-${i}`;
  return `<button class="setting-row focusable" data-action="${action}" data-group="${group.id}" data-index="${i}" data-focus="${self}" data-nav-left="cat-${group.id}" data-nav-right="${self}" data-nav-up="${up}"${down ? ` data-nav-down="${down}"` : ''}>
    <span class="setting-copy"><span class="setting-title">${row.title}</span><span class="setting-desc">${row.desc}</span></span>
    ${row.toggle ? `<span class="switch ${row.on ? 'on' : ''}"></span>` : `<span class="setting-value">${value}</span>${row.options ? icon('chevron-right') : ''}`}
  </button>`;
}

function settingsTemplate() {
  const group = settingGroup();
  const content = `<div class="settings-layout">
    <nav class="card settings-cats">
      ${settingsGroups.map((g, i) => {
        const up = i > 0 ? `cat-${settingsGroups[i - 1].id}` : 'rail-settings';
        const down = i < settingsGroups.length - 1 ? `cat-${settingsGroups[i + 1].id}` : '';
        return `<button class="setting-cat focusable ${i === state.settingsCat ? 'active' : ''}" data-action="select-cat" data-cat="${g.id}" data-focus="cat-${g.id}" data-nav-right="setting-${g.id}-0" data-nav-up="${up}"${down ? ` data-nav-down="${down}"` : ''}>${icon(g.icon)}<span>${g.name}</span></button>`;
      }).join('')}
    </nav>
    <section class="card settings-panel">
      <div class="settings-panel-head"><h3>${group.name}</h3><span>即时保存</span></div>
      <div class="settings-rows">${group.rows.map((r, i) => settingRow(r, i, group)).join('')}</div>
    </section>
  </div>`;
  return `<div class="ui-shell">
    ${railTemplate()}
    <div class="main">
      ${appBar('设置', '下屏 UI · 深色', [])}
      <div class="content">${content}</div>
      ${modeBar({ label: '手柄：下屏', hint: 'B 调整 · A 返回', keys: [['Q/E', '分类'], ['B', '调整']] })}
    </div>
  </div>`;
}

/* ----------------------------- stream ----------------------------- */

function streamHeader(h) {
  return `<header class="appbar">
    <div class="stream-heading">
      <button class="icon-btn focusable" data-action="exit-stream" data-focus="stream-exit" data-nav-right="mode-pill" data-nav-down="${state.streamMode === 'local' ? 'pane-console' : 'enter-local'}" aria-label="退出串流">${icon('arrow-left')}</button>
      <div>
        <h1>${h ? esc(h.name) : '串流'}</h1>
        <div class="stream-stat"><span class="dot green" style="display:inline-block;margin-right:4px"></span>1080p60 · 18 ms · 稳定</div>
      </div>
    </div>
    <button class="mode-pill ${state.streamMode} focusable" data-action="toggle-mode" data-focus="mode-pill" data-nav-left="stream-exit" data-nav-down="${state.streamMode === 'local' ? 'pane-console' : 'enter-local'}"><span class="dot"></span>${state.streamMode === 'local' ? '本地' : '直通'}</button>
  </header>`;
}

function streamRowTemplate(row, i) {
  const value = row.action === 'toggle-mode' ? (state.streamMode === 'local' ? '本地' : '直通') : row.value;
  const rows = streamRows[state.streamTab] || [];
  const up = i > 0 ? `srow-${state.streamTab}-${i - 1}` : `tab-${state.streamTab}`;
  const down = i < rows.length - 1 ? `srow-${state.streamTab}-${i + 1}` : state.streamTab === 'connection' ? 'stream-disconnect' : `tab-${state.streamTab}`;
  const self = `srow-${state.streamTab}-${i}`;
  return `<button class="stream-row focusable" data-action="${row.action || 'stream-row'}" data-row="${i}" data-focus="${self}" data-nav-left="stream-menu" data-nav-right="${self}" data-nav-up="${up}" data-nav-down="${down}">
    <span class="copy"><span class="title">${row.title}</span><span class="desc">${row.desc}</span></span>
    <span class="value">${value}</span>
    ${row.options ? icon('chevron-right') : ''}
  </button>`;
}

const statusLatency = [18, 18, 19, 18, 17, 18, 19, 20, 22, 21, 19, 18, 17, 18, 18, 19, 18, 17, 16, 17, 18, 18, 19, 20, 19, 18, 17, 18, 18, 19, 18, 18];
const statusBitrate = [42, 43, 42, 41, 42, 44, 43, 42, 40, 38, 39, 41, 42, 43, 42, 42, 43, 44, 43, 42, 41, 42, 42, 43, 42, 41, 42, 43, 42, 42, 43, 42];

function statusSpark(values, cls) {
  const h = 28;
  const max = Math.max(...values, 1) * 1.18;
  const pts = values.map((v, i) => `${(i / (values.length - 1) * 100).toFixed(1)},${(h - (v / max) * h).toFixed(1)}`).join(' ');
  return `<polyline class="${cls}" points="${pts}" fill="none" vector-effect="non-scaling-stroke"/>`;
}

function statusTemplate() {
  return `<div class="status-panel" aria-label="串流状态，只读">
    <div class="status-head">
      <div><div class="eyebrow">STREAM HEALTH</div><h3>串流状态</h3></div>
      <div class="status-head-right">
        <span class="pill green"><span class="status-dot online"></span>良好</span>
        <span class="status-time">LIVE · 12:34</span>
        <span class="pill gold">${icon('lock', 'sm')}只读</span>
      </div>
    </div>
    <div class="status-kpis">
      <div class="status-kpi"><div class="status-kpi-label">帧率</div><div class="status-kpi-value">60<small>fps</small></div><div class="status-kpi-sub">接收 60 · 丢帧 0</div></div>
      <div class="status-kpi"><div class="status-kpi-label">网络延迟</div><div class="status-kpi-value">18<small>ms</small></div><div class="status-kpi-sub">抖动 2.1 ms · 丢包 0.0%</div></div>
      <div class="status-kpi"><div class="status-kpi-label">实测码率</div><div class="status-kpi-value">42<small>Mbps</small></div><div class="status-kpi-sub">设置 50 Mbps</div></div>
    </div>
    <div class="status-chart">
      <div class="status-chart-head"><span>最近 60 秒</span><span class="status-legend"><i class="gold"></i>延迟<i class="cyan"></i>码率</span></div>
      <svg class="status-spark" viewBox="0 0 100 28" preserveAspectRatio="none" aria-hidden="true">
        <line class="status-grid" x1="0" y1="9" x2="100" y2="9"/>
        <line class="status-grid" x1="0" y1="18" x2="100" y2="18"/>
        ${statusSpark(statusLatency, 'spark-latency')}
        ${statusSpark(statusBitrate, 'spark-bitrate')}
      </svg>
    </div>
    <div class="status-matrix">
      <div class="status-cell">
        <div class="status-cell-head">${icon('monitor', 'sm')}<span>画面</span></div>
        <div class="status-cell-main">1080p60</div>
        <div class="status-cell-sub" title="HEVC · HDR 关">HEVC · HDR 关</div>
      </div>
      <div class="status-cell status-net">
        <div class="status-cell-head">${icon('wifi', 'sm')}<span>链路</span></div>
        <div class="status-cell-main">Wi-Fi 5</div>
        <div class="status-cell-sub" title="-58 dBm · EasyTier 直连">-58 dBm · EasyTier 直连</div>
      </div>
      <div class="status-cell status-device">
        <div class="status-cell-head">${icon('gamepad', 'sm')}<span>本机</span></div>
        <div class="status-cell-main">42°C</div>
        <div class="status-cell-sub" title="电量 76% · 解码 6 ms">电量 76% · 解码 6 ms</div>
      </div>
      <div class="status-cell">
        <div class="status-cell-head">${icon('host', 'sm')}<span>主机</span></div>
        <div class="status-cell-main">i7-13700K</div>
        <div class="status-cell-sub" title="RTX 4070 · 负载未提供">RTX 4070 · 负载未提供</div>
      </div>
    </div>
    <div class="status-hint">${icon('info', 'sm')}<span>网络抖动 2.1 ms，解码正常；主机实时负载未提供，不伪造。</span></div>
    <div class="status-foot"><span class="status-dot online"></span>示例数据 · 客户端实测 + 主机静态信息 · 自动刷新</div>
  </div>`;
}

function streamTabContent() {
  if (state.streamTab === 'status') return statusTemplate();
  const rows = streamRows[state.streamTab] || [];
  const notes = {
    video: '需要重连的项会在行尾标记；原型中即时切换。',
    audio: '音频输出与空间音频取决于主机和设备能力。',
    input: '本地菜单中 A 在根层恢复直通，不退出串流。',
    connection: 'EasyTier 走虚拟 IP；同一局域网可绕过。',
  };
  return `<div class="tab-content">
    <div class="tab-note">${notes[state.streamTab]}</div>
    ${rows.map(streamRowTemplate).join('')}
    ${state.streamTab === 'connection' ? `<button class="btn danger wide focusable" style="margin-top:10px" data-action="exit-stream" data-focus="stream-disconnect" data-nav-up="srow-connection-5" data-nav-left="stream-menu" data-nav-right="stream-disconnect">${icon('x')}断开串流</button>` : ''}
  </div>`;
}

function streamSwitchTemplate() {
  const tools = state.streamPane === 'tools';
  return `<div class="stream-switch">
    <div class="pane-tabs">
      <button class="pane-tab focusable ${tools ? '' : 'active'}" data-action="switch-pane" data-pane="console" data-focus="pane-console" data-nav-right="pane-tools" data-nav-down="tab-${state.streamTab}" data-nav-up="mode-pill">${icon('sliders', 'sm')}控制台</button>
      <button class="pane-tab focusable ${tools ? 'active' : ''}" data-action="switch-pane" data-pane="tools" data-focus="pane-tools" data-nav-left="pane-console" data-nav-down="tool-tab-${state.toolTab}" data-nav-up="mode-pill">${icon('mouse', 'sm')}工具</button>
    </div>
    <span class="pane-hint">Y 切换 · 工具全宽</span>
  </div>`;
}

function streamConsoleGrid() {
  const local = state.streamMode === 'local';
  return `<div class="stream-grid">
    <div class="stream-left">
      <section class="stream-card">
        <div class="card-head"><h3 class="card-title">连接摘要</h3><span class="pill green">稳定</span></div>
        <div class="stream-stats">
          <div class="stream-stat-cell"><div class="k">分辨率</div><div class="v">1920<small> × 1080</small></div></div>
          <div class="stream-stat-cell"><div class="k">帧率</div><div class="v">60<small> fps</small></div></div>
          <div class="stream-stat-cell"><div class="k">码率</div><div class="v">50<small> Mbps</small></div></div>
          <div class="stream-stat-cell"><div class="k">延迟</div><div class="v">18<small> ms</small></div></div>
        </div>
      </section>
      <section class="stream-card stream-quick">
        <div class="card-head"><h3 class="card-title">快捷</h3><span class="card-sub">长按调整</span></div>
        <div class="quick-row"><span class="k">${icon('volume', 'sm')} 音量</span><span class="bar"><span></span></span><span class="v">80%</span></div>
        <div class="quick-row"><span class="k">HDR</span><span class="v">关</span></div>
        <div class="quick-row"><span class="k">输入</span><span class="v">${local ? '本地' : '直通'}</span></div>
        <div class="quick-row"><span class="k">丢包</span><span class="v">0.0%</span></div>
      </section>
      <button class="btn ${local ? 'primary' : 'ghost'} menu-btn focusable" data-action="${local ? 'resume-game' : 'enter-local'}" data-focus="stream-menu" data-nav-right="tab-${state.streamTab}" data-nav-up="pane-console">${icon(local ? 'gamepad' : 'sliders')}${local ? '返回游戏' : '本地菜单'}</button>
    </div>
    <section class="stream-right">
      <div class="stream-tabs">
        ${streamTabs.map((t, i) => {
          const left = i > 0 ? `tab-${streamTabs[i - 1].id}` : 'stream-menu';
          const right = i < streamTabs.length - 1 ? `tab-${streamTabs[i + 1].id}` : 'mode-pill';
          const down = t.id === state.streamTab ? (t.id === 'status' ? `tab-${t.id}` : `srow-${t.id}-0`) : '';
          return `<button class="stream-tab focusable ${state.streamTab === t.id ? 'active' : ''}" data-action="stream-tab" data-tab="${t.id}" data-focus="tab-${t.id}" data-nav-left="${left}" data-nav-right="${right}" data-nav-up="pane-console"${down ? ` data-nav-down="${down}"` : ''}>${t.name}</button>`;
        }).join('')}
      </div>
      ${streamTabContent()}
    </section>
  </div>`;
}

function toolFirstFocus(tab = state.toolTab) {
  if (tab === 'touchpad') return 'pad-surface';
  if (tab === 'keyboard') return 'kb-0-0';
  return 'sc-0';
}

function toolTabsTemplate() {
  return `<div class="tool-tabs">
    ${toolTabs.map((t, i) => {
      const left = i > 0 ? `tool-tab-${toolTabs[i - 1].id}` : 'pane-tools';
      const right = i < toolTabs.length - 1 ? `tool-tab-${toolTabs[i + 1].id}` : `tool-tab-${t.id}`;
      const down = t.id === state.toolTab ? ` data-nav-down="${toolFirstFocus(t.id)}"` : '';
      return `<button class="tool-tab focusable ${state.toolTab === t.id ? 'active' : ''}" data-action="tool-tab" data-tab="${t.id}" data-focus="tool-tab-${t.id}" data-nav-left="${left}" data-nav-right="${right}" data-nav-up="pane-tools"${down}>${icon(t.icon, 'sm')}${t.name}</button>`;
    }).join('')}
    <span class="tool-tab-hint">Q/E 切换 · A 返回控制台</span>
  </div>`;
}

function touchpadTemplate() {
  return `<div class="touchpad-panel">
    <div class="touchpad-surface focusable" data-pad-surface="1" data-action="pad-click" data-button="1" data-focus="pad-surface" data-nav-up="tool-tab-touchpad" data-nav-down="pad-left" tabindex="0" role="button" aria-label="触摸板">
      <span class="pad-mode">${icon('mouse', 'sm')}触摸板 · 相对移动</span>
      <span class="pad-sens">${state.padSensitivity.toFixed(1)}×</span>
      <span class="pad-cursor"></span>
      <span class="pad-hint">单指移动 · 点击左键 · 双指滚动 · 双指点击右键 · 长按拖拽</span>
    </div>
    <div class="pad-actions">
      <button class="pad-btn primary focusable" data-action="pad-click" data-button="1" data-focus="pad-left" data-nav-up="pad-surface" data-nav-right="pad-middle">左键</button>
      <button class="pad-btn focusable" data-action="pad-click" data-button="16" data-focus="pad-middle" data-nav-left="pad-left" data-nav-right="pad-right" data-nav-up="pad-surface">中键</button>
      <button class="pad-btn focusable" data-action="pad-click" data-button="2" data-focus="pad-right" data-nav-left="pad-middle" data-nav-right="pad-drag" data-nav-up="pad-surface">右键</button>
      <button class="pad-btn focusable ${state.padDragLock ? 'active' : ''}" data-action="pad-drag" data-focus="pad-drag" data-nav-left="pad-right" data-nav-right="pad-sens-btn" data-nav-up="pad-surface">${icon('mouse', 'sm')}拖拽锁定</button>
      <button class="pad-btn icon-only focusable" data-action="pad-sensitivity" data-focus="pad-sens-btn" data-nav-left="pad-drag" data-nav-up="pad-surface" aria-label="触摸板灵敏度">${icon('sliders', 'sm')}</button>
    </div>
  </div>`;
}

function keyboardKey(k, r, c) {
  const active = (k.t === 'mod' && k.k !== 'symbols' && state.kbMods.includes(k.k)) ||
    (k.k === 'symbols' && state.kbSymbols);
  const action = k.k === 'symbols' ? 'toggle-symbols' : (k.t === 'mod' ? 'toggle-key-mod' : 'send-key');
  return `<button class="tool-key focusable ${k.t || ''} ${active ? 'active' : ''}" style="flex:${k.w}" data-action="${action}" data-key="${esc(k.k)}" data-label="${esc(k.l)}" data-focus="kb-${r}-${c}"${r === 0 ? ' data-nav-up="tool-tab-keyboard"' : ''}>${k.l}</button>`;
}

function keyboardTemplate() {
  const rows = state.kbSymbols ? kbSymbolRows : kbMainRows;
  return `<div class="keyboard-panel">
    <div class="kb-status">
      <span class="kb-state"><span class="dot gold"></span>ASCII 直发 · 无本地拼音</span>
      <span id="kb-status-text" class="kb-last">${esc(state.kbLast || '就绪')}</span>
      <button class="btn ghost kb-system focusable" data-action="system-ime" data-focus="kb-system" data-nav-up="tool-tab-keyboard">系统</button>
    </div>
    <div class="keyboard">${rows.map((row, r) => `<div class="kb-row">${row.map((k, c) => keyboardKey(k, r, c)).join('')}</div>`).join('')}</div>
    <div class="kb-foot">B 发送 · Ctrl / Alt / Win / Shift 可粘滞 · 离开页面自动释放</div>
  </div>`;
}

function shortcutsTemplate() {
  const actions = [
    ['本地键盘', 'keyboard'],
    ['主机键盘', 'keyboard'],
    ['Windows HDR', 'hd'],
    ['休眠', 'lock'],
    ['退出串流', 'x'],
  ];
  return `<div class="shortcuts-panel">
    <div class="action-strip">
      ${actions.map(([label, ic], i) => `<button class="action-chip focusable" data-action="stream-action" data-message="动作：${label}（模拟）" data-focus="sa-${i}" data-nav-right="sa-${i + 1}">${icon(ic, 'sm')}${label}</button>`).join('')}
    </div>
    <div class="shortcut-grid">
      ${shortcuts.map((sc, i) => {
        const col = i % 4;
        const row = Math.floor(i / 4);
        const left = col > 0 ? `sc-${i - 1}` : 'tool-tab-shortcuts';
        const right = col < 3 ? `sc-${i + 1}` : `sc-${i}`;
        const up = row > 0 ? `sc-${i - 4}` : 'tool-tab-shortcuts';
        const down = row < 2 ? `sc-${i + 4}` : `sc-${i}`;
        return `<button class="shortcut-card focusable ${sc.danger ? 'danger' : ''}" data-action="shortcut" data-index="${i}" data-label="${esc(sc.label)}" data-name="${esc(sc.name)}" data-focus="sc-${i}" data-nav-left="${left}" data-nav-right="${right}" data-nav-up="${up}" data-nav-down="${down}">
          <span class="sc-keys">${sc.label}</span>
          <span class="sc-name">${sc.name}</span>
          ${sc.danger ? '<span class="sc-hold">长按确认</span>' : ''}
        </button>`;
      }).join('')}
    </div>
    <div class="shortcut-foot">组合键按顺序发送并自动释放；离开页面或断流时释放所有按键。HDR 动作只切 Windows HDR。</div>
  </div>`;
}

function streamToolPanel() {
  const body = state.toolTab === 'keyboard'
    ? keyboardTemplate()
    : state.toolTab === 'shortcuts' ? shortcutsTemplate() : touchpadTemplate();
  return `<div class="tool-panel">
    ${toolTabsTemplate()}
    <div class="tool-content">${body}</div>
  </div>`;
}

function streamTemplate() {
  const h = hostAt();
  const local = state.streamMode === 'local';
  const tools = state.streamPane === 'tools';
  return `<div class="stream-shell ${local ? 'local' : 'passthrough'}">
    ${streamHeader(h)}
    <div class="stream-body">
      <div class="stream-console">
        <div class="stream-view">
          ${streamSwitchTemplate()}
          <div class="stream-view-body">${tools ? streamToolPanel() : streamConsoleGrid()}</div>
        </div>
      </div>
      ${local ? '' : `<div class="passthrough-cover"><button class="enter-local focusable" data-action="enter-local" data-focus="enter-local" data-nav-up="mode-pill" data-nav-left="stream-exit">点击进入本地操作 · R</button></div>`}
    </div>
    ${local
      ? (tools
        ? modeBar({ dot: 'gold', label: '手柄：本地', hint: 'A 返回控制台', keys: [['Q/E', '工具'], ['Y', '控制台']] })
        : modeBar({ dot: 'gold', label: '手柄：本地', hint: 'A 返回直通', keys: [['Y', '工具'], ['Q/E', '页签'], ['R', '直通']] }))
      : modeBar({ dot: 'cyan', label: '手柄：直通电脑', hint: 'R / RETURN 本地菜单', keys: [['R', '本地']] })}
  </div>`;
}

/* ----------------------------- overlays ----------------------------- */

const qrSvg = () => {
  const n = 21;
  const cells = [];
  const finder = (x, y) => {
    for (let r = 0; r < 7; r++) {
      for (let c = 0; c < 7; c++) {
        if (r === 0 || r === 6 || c === 0 || c === 6 || (r >= 2 && r <= 4 && c >= 2 && c <= 4)) {
          cells.push(`<rect x="${x + c}" y="${y + r}" width="1" height="1"/>`);
        }
      }
    }
  };
  finder(0, 0); finder(14, 0); finder(0, 14);
  for (let i = 8; i < 13; i++) {
    if (i % 2 === 0) cells.push(`<rect x="${i}" y="6" width="1" height="1"/><rect x="6" y="${i}" width="1" height="1"/>`);
  }
  for (let y = 0; y < n; y++) {
    for (let x = 0; x < n; x++) {
      const inFinder = (x < 7 && y < 7) || (x > 13 && y < 7) || (x < 7 && y > 13);
      if (inFinder) continue;
      if ((x * 7 + y * 13 + x * y * 3) % 7 < 3) cells.push(`<rect x="${x}" y="${y}" width="1" height="1"/>`);
    }
  }
  return `<svg viewBox="0 0 21 21" fill="#0f0f0f" shape-rendering="crispEdges" aria-label="QR placeholder">${cells.join('')}</svg>`;
};

function pinRow() {
  return `<div class="pin-row">${state.pin.split('').map((d) => `<span class="pin-box">${d}</span>`).join('')}</div>`;
}

function overlayTemplate() {
  const h = hostAt();
  const keys = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '.', '0', 'back'];
  const keyId = (k) => `key-${k === '.' ? 'dot' : k}`;
  if (state.overlay === 'pair') {
    return `<div class="overlay">
      <div class="overlay-panel">
        <div class="overlay-head">
          <div><h2 class="overlay-title">配对 ${h ? esc(h.name) : '主机'}</h2><p class="overlay-desc">在电脑上的 Foundation Sunshine 输入此 PIN。5 分钟内有效。</p></div>
          <span class="pill gold">4 位 PIN</span>
        </div>
        ${pinRow()}
        <div class="pin-caption">PIN 只显示在下屏，不写入日志<br>${h ? esc(h.addr) : ''}</div>
        <div class="overlay-actions">
          <button class="btn primary focusable" data-action="pair-qr" data-focus="pair-qr" data-nav-right="overlay-cancel">${icon('qr')}显示二维码</button>
          <button class="btn ghost focusable" data-action="close-overlay" data-focus="overlay-cancel" data-nav-left="pair-qr">取消</button>
        </div>
      </div>
    </div>`;
  }
  if (state.overlay === 'add') {
    return `<div class="overlay">
      <div class="overlay-panel">
        <div class="overlay-head">
          <div><h2 class="overlay-title">${state.editingHost === null ? '添加主机' : '编辑主机'}</h2><p class="overlay-desc">输入 PC 的局域网地址或 EasyTier 虚拟 IP。</p></div>
          <span class="pill">手动</span>
        </div>
        <div class="field-row">
          <div class="field wide"><label for="addIp">IP 地址</label><input id="addIp" class="proto-input" value="${esc(state.addIp)}" inputmode="decimal" autocomplete="off" spellcheck="false"></div>
          <div class="field" style="flex:0 0 88px"><label for="addPort">端口</label><input id="addPort" class="proto-input" value="47989" inputmode="numeric" autocomplete="off"></div>
        </div>
        <div class="keypad">
          ${keys.map((k, i) => {
            const col = i % 4;
            const row = Math.floor(i / 4);
            const left = col > 0 ? keyId(keys[i - 1]) : '';
            const right = col < 3 ? keyId(keys[i + 1]) : '';
            const up = row > 0 ? keyId(keys[i - 4]) : '';
            const down = row < 2 ? keyId(keys[i + 4]) : (col < 2 ? 'add-cancel' : 'add-submit');
            return `<button class="focusable" data-action="keypad" data-key="${k}" data-focus="${keyId(k)}"${left ? ` data-nav-left="${left}"` : ''}${right ? ` data-nav-right="${right}"` : ''}${up ? ` data-nav-up="${up}"` : ''} data-nav-down="${down}">${k === 'back' ? '⌫' : k}</button>`;
          }).join('')}
        </div>
        <div class="hint-line">可直接点击输入框键入；手柄用方向键 + B 输入。示例 PC 虚拟 IP：10.0.0.2</div>
        <div class="overlay-actions" style="margin-top:12px">
          <button class="btn ghost focusable" data-action="close-overlay" data-focus="add-cancel" data-nav-right="add-submit" data-nav-up="key-0">取消</button>
          <button class="btn primary focusable" data-action="add-submit" data-focus="add-submit" data-nav-left="add-cancel" data-nav-up="key-back">${state.editingHost === null ? '添加' : '保存'}</button>
        </div>
      </div>
    </div>`;
  }
  if (state.overlay === 'qr') {
    const pairQr = state.overlayReturn === 'pair';
    return `<div class="overlay">
      <div class="overlay-panel">
        <div class="overlay-head">
          <div><h2 class="overlay-title">${pairQr ? '配对二维码' : 'PC 配置二维码'}</h2><p class="overlay-desc">${pairQr ? '用相机扫描，或回到 PIN 界面在电脑上输入。' : '在 PC 端 EasyTier 导入同一网络配置。'}</p></div>
          <span class="pill gold">${pairQr ? '配对' : '配置'}</span>
        </div>
        <div class="qr-wrap">
          <div class="qr-code">${qrSvg()}</div>
          <div class="qr-caption">${pairQr ? 'moonlight://pair?host=…&amp;port=47989&amp;pin=…' : '网络名 family-net · PC IP 10.0.0.2/24'}</div>
          <div class="qr-warning">原型占位图 · ${pairQr ? '不含真实证书' : '不包含真实密钥'}</div>
        </div>
        <div class="overlay-actions">
          <button class="btn ghost focusable" data-action="qr-copy" data-focus="qr-copy" data-nav-right="qr-close">${icon('copy')}${pairQr ? '复制链接' : '复制配置'}</button>
          <button class="btn primary focusable" data-action="close-overlay" data-focus="qr-close" data-nav-left="qr-copy">关闭</button>
        </div>
      </div>
    </div>`;
  }
  if (state.overlay === 'exit') {
    return `<div class="overlay">
      <div class="overlay-panel danger">
        <div class="overlay-head">
          <div><h2 class="overlay-title">退出串流？</h2><p class="overlay-desc">连接会断开，上屏回到主机预览。游戏内进度由 PC 保留。</p></div>
          <span class="confirm-icon">${icon('x')}</span>
        </div>
        <div class="overlay-actions">
          <button class="btn ghost focusable" data-action="close-overlay" data-focus="exit-cancel" data-nav-right="exit-confirm">继续串流</button>
          <button class="btn danger focusable" data-action="confirm-exit" data-focus="exit-confirm" data-nav-left="exit-cancel">退出</button>
        </div>
      </div>
    </div>`;
  }
  if (state.overlay === 'lock') {
    return `<div class="overlay">
      <div class="overlay-panel danger">
        <div class="overlay-head">
          <div><h2 class="overlay-title">锁定电脑？</h2><p class="overlay-desc">Win + L 会立即锁定远端 Windows，需要重新登录。该快捷键不会被普通点击直接发送。</p></div>
          <span class="confirm-icon">${icon('lock')}</span>
        </div>
        <div class="overlay-actions">
          <button class="btn ghost focusable" data-action="close-overlay" data-focus="lock-cancel" data-nav-right="lock-confirm">取消</button>
          <button class="btn danger focusable" data-action="confirm-lock" data-focus="lock-confirm" data-nav-left="lock-cancel">锁定</button>
        </div>
      </div>
    </div>`;
  }
  if (state.overlay === 'delete') {
    const h = hostAt();
    return `<div class="overlay">
      <div class="overlay-panel danger">
        <div class="overlay-head">
          <div><h2 class="overlay-title">删除主机？</h2><p class="overlay-desc">将从列表移除 ${h ? esc(h.name) : '这台主机'}。不会卸载 PC 端 Sunshine，也不会删除已安装游戏。</p></div>
          <span class="confirm-icon">${icon('trash')}</span>
        </div>
        <div class="overlay-actions">
          <button class="btn ghost focusable" data-action="close-overlay" data-focus="delete-cancel" data-nav-right="delete-confirm">取消</button>
          <button class="btn danger focusable" data-action="confirm-delete" data-focus="delete-confirm" data-nav-left="delete-cancel">删除</button>
        </div>
      </div>
    </div>`;
  }
  if (state.overlay === 'app-actions') {
    const a = appAt();
    const h = hostAt();
    return `<div class="overlay">
      <div class="overlay-panel">
        <div class="overlay-head">
          <div><h2 class="overlay-title">${esc(a.name)}</h2><p class="overlay-desc">${h ? esc(h.name) : '未选择主机'} · 选择本次启动方式或管理应用。</p></div>
          <span class="pill gold">应用操作</span>
        </div>
        <div class="action-list">
          <button class="action-row focusable primary-row" data-action="app-action" data-message="已选择：启动" data-focus="app-a-launch" data-nav-down="app-a-last">${icon('play')}启动</button>
          <button class="action-row focusable" data-action="app-action" data-message="已选择：使用上次设置启动" data-focus="app-a-last" data-nav-up="app-a-launch" data-nav-down="app-a-quitstart">${icon('sliders')}使用上次设置启动<small>1920×1080 · 60 · 20 Mbps · HDR 关</small></button>
          <button class="action-row focusable" data-action="app-action" data-message="已选择：退出当前应用并启动" data-focus="app-a-quitstart" data-nav-up="app-a-last" data-nav-down="app-a-hide">${icon('refresh')}退出当前应用并启动</button>
          <button class="action-row focusable" data-action="app-action" data-message="已选择：隐藏应用" data-focus="app-a-hide" data-nav-up="app-a-quitstart" data-nav-down="app-a-info">${icon('eye')}隐藏应用</button>
          <button class="action-row focusable" data-action="app-action" data-message="已选择：应用详情" data-focus="app-a-info" data-nav-up="app-a-hide" data-nav-down="app-a-shortcut">${icon('info')}应用详情</button>
          <button class="action-row focusable" data-action="app-action" data-message="已选择：创建桌面快捷方式" data-focus="app-a-shortcut" data-nav-up="app-a-info">${icon('plus')}创建桌面快捷方式</button>
        </div>
        <div class="overlay-actions">
          <button class="btn ghost focusable" data-action="close-overlay" data-focus="app-a-cancel" data-nav-up="app-a-shortcut">取消</button>
        </div>
      </div>
    </div>`;
  }
  return '';
}

/* ----------------------------- single screen ----------------------------- */

function singleStatusTemplate() {
  return `<div class="single-quick">
    <div class="single-kv"><span class="k">帧率</span><span class="v">60 fps</span></div>
    <div class="single-kv"><span class="k">延迟</span><span class="v">18 ms</span></div>
    <div class="single-kv"><span class="k">码率</span><span class="v">42 Mbps</span></div>
    <div class="single-kv"><span class="k">丢包</span><span class="v">0.0%</span></div>
    <div class="single-kv"><span class="k">本机</span><span class="v">42°C · 76%</span></div>
    <div class="single-kv"><span class="k">主机</span><span class="v">负载未提供</span></div>
  </div>
  <div class="single-tabbody single-status-body">
    <div class="single-status-line"><span class="k">画面</span><span class="v">1080p60 · HEVC</span></div>
    <div class="single-status-line"><span class="k">链路</span><span class="v">Wi-Fi 5 · 直连</span></div>
    <div class="single-status-line"><span class="k">状态</span><span class="v">只读 · 示例数据</span></div>
  </div>`;
}

function singleConsoleBody() {
  if (state.singleTab === 'status') return singleStatusTemplate();
  return `<div class="single-quick">
    <div class="single-kv"><span class="k">分辨率</span><span class="v">1920×1080</span></div>
    <div class="single-kv"><span class="k">帧率</span><span class="v">60</span></div>
    <div class="single-kv"><span class="k">码率</span><span class="v">50M</span></div>
    <div class="single-kv"><span class="k">延迟</span><span class="v">18ms</span></div>
    <div class="single-kv"><span class="k">丢包</span><span class="v">0.0%</span></div>
    <div class="single-kv"><span class="k">音量</span><span class="v">80%</span></div>
  </div>
  <div class="single-tabbody">${singleRows()}</div>`;
}

function singleRows() {
  const rows = (streamRows[state.singleTab] || []).slice(0, 3);
  return rows.map((r, i) => {
    const value = r.action === 'toggle-mode' ? (state.streamMode === 'local' ? '本地' : '直通') : r.value;
    const up = i > 0 ? `single-row-${state.singleTab}-${i - 1}` : `single-tab-${state.singleTab}`;
    const down = i < rows.length - 1 ? `single-row-${state.singleTab}-${i + 1}` : '';
    const self = `single-row-${state.singleTab}-${i}`;
    return `<button class="single-row focusable" data-action="single-row" data-row="${i}" data-focus="${self}" data-nav-right="${self}" data-nav-up="${up}"${down ? ` data-nav-down="${down}"` : ''}>
      <span>${r.title}</span><span class="v">${value}</span>${icon('chevron-right')}
    </button>`;
  }).join('');
}

function singleTemplate() {
  const h = hostAt();
  const collapsed = state.singleCollapsed;
  return `<div class="single-meta"><span>单屏 · 视频全屏 + 控制台抽屉</span><span>832 × 468</span></div>
  <div class="single-frame ${collapsed ? 'collapsed' : ''}">
    ${videoScene()}
    <div class="single-drawer">
      <div class="single-drawer-head">
        <span class="name">${h ? esc(h.name) : '串流'}</span>
        <span class="meta">1080p60 · 18 ms</span>
        <span class="right"><span class="pill ${state.streamMode === 'local' ? 'gold' : 'cyan'}">${state.streamMode === 'local' ? '本地' : '直通'}</span><button class="icon-btn focusable" data-action="single-collapse" data-focus="single-collapse" data-nav-down="single-tab-${state.singleTab}" aria-label="收起控制台">${icon('chevron-right')}</button></span>
      </div>
      <div class="single-tabs">
        ${singleTabs.map((t, i) => {
          const left = i > 0 ? `single-tab-${singleTabs[i - 1].id}` : 'single-collapse';
          const right = i < singleTabs.length - 1 ? `single-tab-${singleTabs[i + 1].id}` : 'single-collapse';
          const down = t.id === state.singleTab ? (t.id === 'status' ? `single-tab-${t.id}` : `single-row-${t.id}-0`) : '';
          return `<button class="single-tab focusable ${state.singleTab === t.id ? 'active' : ''}" data-action="single-tab" data-tab="${t.id}" data-focus="single-tab-${t.id}" data-nav-left="${left}" data-nav-right="${right}" data-nav-up="single-collapse"${down ? ` data-nav-down="${down}"` : ''}>${t.name}</button>`;
        }).join('')}
      </div>
      <div class="single-drawer-body">${singleConsoleBody()}</div>
      <div class="single-modebar"><span class="dot" style="background:${state.streamMode === 'local' ? 'var(--accent)' : 'var(--signal)'}"></span><span>手柄：${state.streamMode === 'local' ? '本地' : '直通'}</span><span>·</span><span>${state.streamMode === 'local' ? 'A 返回直通' : 'RETURN 本地菜单'}</span><span class="keys">Q/E 切页签</span></div>
    </div>
    <button class="single-float focusable" data-action="single-expand" data-focus="single-expand">${icon('sliders', 'sm')}控制台</button>
  </div>`;
}

/* ----------------------------- render ----------------------------- */

function pageTemplate() {
  if (state.page === 'apps') return appsTemplate();
  if (state.page === 'remote') return remoteTemplate();
  if (state.page === 'settings') return settingsTemplate();
  if (state.page === 'stream') return streamTemplate();
  return hostsTemplate();
}

function render() {
  topScreen.innerHTML = topTemplate();
  bottomScreen.innerHTML = pageTemplate() + overlayTemplate();
  if (state.view === 'single') {
    dualView.hidden = true;
    singleView.hidden = false;
    singleView.innerHTML = singleTemplate();
  } else {
    dualView.hidden = false;
    singleView.hidden = true;
    singleView.innerHTML = '';
  }
  updateChrome();
  restoreFocus();
}

function updateChrome() {
  $$('.proto-nav button').forEach((b) => {
    const page = b.dataset.page;
    const isPair = b.dataset.action === 'open-pair';
    const pairOpen = state.overlay === 'pair' || (state.overlay === 'qr' && state.overlayReturn === 'pair');
    const active = pairOpen ? isPair : state.view === 'single' ? page === 'stream' : page === state.page;
    b.classList.toggle('active', !!active);
  });
  viewToggle.textContent = state.view === 'single' ? '双屏预览' : '单屏预览';
}

/* ----------------------------- focus ----------------------------- */

function activeScope() {
  return state.view === 'single' ? singleView : bottomScreen;
}

function overlayDefault() {
  if (state.overlay === 'pair') return 'pair-qr';
  if (state.overlay === 'add') return 'key-1';
  if (state.overlay === 'qr') return 'qr-close';
  if (state.overlay === 'exit') return 'exit-cancel';
  if (state.overlay === 'delete') return 'delete-cancel';
  if (state.overlay === 'lock') return 'lock-cancel';
  return null;
}

function pageDefault(page = state.page) {
  if (page === 'stream') {
    if (state.streamMode === 'local' && state.streamPane === 'tools') return `tool-tab-${state.toolTab}`;
    return state.streamMode === 'local' ? `tab-${state.streamTab}` : 'enter-local';
  }
  if (page === 'apps') return `app-${state.appIndex}`;
  if (page === 'remote') return 'remote-toggle';
  if (page === 'settings') return `cat-${settingGroup().id}`;
  return hosts.length ? `host-${state.hostIndex}` : 'host-add';
}

function defaultFocusId() {
  if (state.view === 'single') return state.singleCollapsed ? 'single-expand' : `single-tab-${state.singleTab}`;
  if (state.overlay) return overlayDefault();
  return pageDefault();
}

function focusables() {
  const scope = activeScope();
  const root = state.overlay ? $('.overlay', scope) : scope;
  if (!root) return [];
  let els = $$('.focusable', root);
  els = els.filter((el) => !el.disabled && el.offsetParent !== null && !el.closest('[hidden]'));
  if (!state.overlay && state.page === 'stream' && state.streamMode === 'passthrough') {
    els = els.filter((el) => !el.closest('.stream-console'));
  }
  return els;
}

function setFocus(el, scroll = true) {
  if (!el) return;
  $$('.focused').forEach((n) => n.classList.remove('focused'));
  el.classList.add('focused');
  state.focusId = el.dataset.focus || null;
  try { el.focus({ preventScroll: true }); } catch (_) { /* noop */ }
  if (!scroll) return;
  const scroller = el.closest('.scroll, .settings-rows, .tab-content, .host-list, .app-grid');
  if (!scroller) return;
  const r = el.getBoundingClientRect();
  const sr = scroller.getBoundingClientRect();
  if (r.top < sr.top + 2) scroller.scrollTop -= (sr.top - r.top) + 8;
  else if (r.bottom > sr.bottom - 2) scroller.scrollTop += (r.bottom - sr.bottom) + 8;
}

function restoreFocus() {
  const scope = activeScope();
  const list = focusables();
  if (!list.length) return;
  const wanted = state.focusId ? $(`[data-focus="${state.focusId}"]`, state.overlay ? $('.overlay', scope) || scope : scope) : null;
  const fallback = $(`[data-focus="${defaultFocusId()}"]`, state.overlay ? $('.overlay', scope) || scope : scope);
  const target = (wanted && !wanted.disabled && wanted.offsetParent !== null) ? wanted : (fallback || list[0]);
  if (target) setFocus(target, false);
}

function commitFocusTarget(target) {
  if (target.dataset.action === 'stream-tab') {
    state.streamTab = target.dataset.tab;
    state.focusId = target.dataset.focus;
    render();
    return true;
  }
  if (target.dataset.action === 'single-tab') {
    state.singleTab = target.dataset.tab;
    state.focusId = target.dataset.focus;
    render();
    return true;
  }
  if (target.dataset.action === 'select-cat') {
    const idx = settingsGroups.findIndex((g) => g.id === target.dataset.cat);
    if (idx >= 0) {
      state.settingsCat = idx;
      state.focusId = target.dataset.focus;
      render();
      return true;
    }
  }
  return false;
}

function moveFocus(dir) {
  const els = focusables();
  if (!els.length) return;
  let cur = els.find((el) => el.classList.contains('focused')) || els[0];
  const navKey = 'nav' + dir[0].toUpperCase() + dir.slice(1);
  const navId = cur.dataset[navKey];
  if (navId) {
    const target = els.find((el) => el.dataset.focus === navId);
    if (target) {
      if (commitFocusTarget(target)) return;
      setFocus(target);
      return;
    }
  }
  const cr = cur.getBoundingClientRect();
  const cx = cr.left + cr.width / 2;
  const cy = cr.top + cr.height / 2;
  let best = null;
  let bestScore = Infinity;
  els.forEach((el) => {
    if (el === cur) return;
    const r = el.getBoundingClientRect();
    const x = r.left + r.width / 2;
    const y = r.top + r.height / 2;
    const dx = x - cx;
    const dy = y - cy;
    let primary;
    let secondary;
    if (dir === 'left') { primary = -dx; secondary = Math.abs(dy); }
    else if (dir === 'right') { primary = dx; secondary = Math.abs(dy); }
    else if (dir === 'up') { primary = -dy; secondary = Math.abs(dx); }
    else { primary = dy; secondary = Math.abs(dx); }
    if (primary <= 2) return;
    const score = primary + secondary * 1.65;
    if (score < bestScore) { bestScore = score; best = el; }
  });
  if (best) {
    if (commitFocusTarget(best)) return;
    setFocus(best);
  }
}

function moveFocusTab(delta) {
  const els = focusables();
  if (!els.length) return;
  const cur = els.find((el) => el.classList.contains('focused')) || els[0];
  const i = els.indexOf(cur);
  const next = els[(i + delta + els.length) % els.length];
  setFocus(next);
}

document.addEventListener('focusin', (e) => {
  const el = e.target.closest && e.target.closest('.focusable');
  if (!el) return;
  if (!el.closest('#bottomScreen, #singleView')) return;
  if (!el.classList.contains('focused')) setFocus(el, false);
  else state.focusId = el.dataset.focus || state.focusId;
});

/* ----------------------------- navigation / actions ----------------------------- */

function setPage(page, opts = {}) {
  state.page = page;
  state.view = 'dual';
  state.overlay = null;
  state.overlayReturn = null;
  state.overlayOrigin = null;
  if (page === 'stream') {
    state.streamMode = 'local';
    state.streamPane = 'console';
    state.toolTab = 'touchpad';
  }
  state.focusId = opts.focus || (opts.fromRail ? `rail-${page}` : pageDefault(page));
  render();
}

function setView(view) {
  state.view = view;
  state.overlay = null;
  state.overlayReturn = null;
  state.overlayOrigin = null;
  state.focusId = null;
  render();
}

function openOverlay(name, returnTo = null) {
  const wasSingle = state.view === 'single';
  state.view = 'dual';
  if (wasSingle) state.focusId = null;
  if (!state.overlay) state.overlayOrigin = state.focusId;
  state.overlayReturn = returnTo || state.overlay || null;
  state.overlay = name;
  state.focusId = overlayDefault();
  render();
}

function closeOverlay() {
  if (state.overlayReturn) {
    state.overlay = state.overlayReturn;
    state.overlayReturn = null;
    state.focusId = overlayDefault();
    render();
    return;
  }
  state.overlay = null;
  state.overlayReturn = null;
  state.focusId = state.overlayOrigin || defaultFocusId();
  state.overlayOrigin = null;
  render();
}

function toggleMode() {
  state.streamMode = state.streamMode === 'local' ? 'passthrough' : 'local';
  if (state.streamMode === 'passthrough') { state.kbMods = []; state.kbSymbols = false; }
  state.focusId = state.streamMode === 'local'
    ? (state.streamPane === 'tools' ? `tool-tab-${state.toolTab}` : `tab-${state.streamTab}`)
    : 'enter-local';
  render();
  showToast(state.streamMode === 'local' ? '本地操作：焦点环可见' : '手柄已交还电脑；按 R 或触摸进入本地');
}

function cycleValue(row, onDone) {
  if (!row.options || !row.options.length) {
    showToast(`${row.title}：原型中暂未展开`);
    return;
  }
  const idx = row.options.indexOf(row.value);
  row.value = row.options[(idx + 1) % row.options.length];
  if (onDone) onDone();
  showToast(`${row.title} → ${row.value}`);
}

function runAction(action, el) {
  const h = hostAt();
  switch (action) {
    case 'select-host': {
      state.hostIndex = Number(el.dataset.index);
      state.focusId = `host-${state.hostIndex}`;
      render();
      return;
    }
    case 'connect-host': {
      if (!h || !online(h)) { showToast('主机离线，先检查网络'); return; }
      if (!h.paired) { openOverlay('pair'); return; }
      state.page = 'stream';
      state.streamMode = 'local';
      state.streamPane = 'console';
      state.toolTab = 'touchpad';
      state.kbMods = [];
      state.kbSymbols = false;
      state.kbLast = '';
      state.streamTab = 'status';
      state.overlay = null;
      state.overlayReturn = null;
      state.focusId = 'tab-status';
      render();
      showToast('开始串流（模拟）');
      return;
    }
    case 'pair-host': openOverlay('pair'); return;
    case 'network-test': showToast('网络测试（模拟）：18 ms · 0.0% 丢包 · 建议 20 Mbps'); return;
    case 'host-info': showToast('主机详情（模拟）：Foundation Sunshine'); return;
    case 'host-more': showToast('更多操作（模拟）：唤醒 / 休眠 / WebUI / IPv6'); return;
    case 'open-app-actions': openOverlay('app-actions', 'app-actions'); return;
    case 'app-action': closeOverlay(); showToast(el.dataset.message || '已选择（模拟）'); return;
    case 'host-apps': state.focusId = `app-${state.appIndex}`; setPage('apps', { focus: `app-${state.appIndex}` }); return;
    case 'edit-host': {
      if (!h) return;
      state.editingHost = state.hostIndex;
      state.addIp = h.addr;
      openOverlay('add');
      return;
    }
    case 'delete-host': openOverlay('delete'); return;
    case 'confirm-delete': {
      if (!hosts.length) return;
      hosts.splice(state.hostIndex, 1);
      state.hostIndex = Math.max(0, Math.min(state.hostIndex, hosts.length - 1));
      state.overlay = null;
      state.overlayReturn = null;
      state.focusId = hosts.length ? `host-${state.hostIndex}` : 'host-add';
      render();
      showToast('主机已移除（模拟）');
      return;
    }
    case 'open-add': state.editingHost = null; state.addIp = '10.0.0.2'; openOverlay('add'); return;
    case 'add-submit': {
      const input = $('#addIp');
      const ip = (input ? input.value : state.addIp).trim();
      if (!/^\d{1,3}(\.\d{1,3}){3}$/.test(ip)) { showToast('IP 格式不正确'); return; }
      if (state.editingHost !== null && hosts[state.editingHost]) {
        hosts[state.editingHost].addr = ip;
        state.hostIndex = state.editingHost;
        state.editingHost = null;
        state.overlay = null;
        state.overlayReturn = null;
        state.focusId = `host-${state.hostIndex}`;
        render();
        showToast('主机已保存（模拟）');
        return;
      }
      hosts.push({ name: '手动主机', addr: ip, state: 'online', paired: false, tag: '手动', last: '刚刚', gpu: '待检测', os: 'Windows' });
      state.hostIndex = hosts.length - 1;
      state.overlay = null;
      state.overlayReturn = null;
      state.page = 'hosts';
      state.focusId = `host-${state.hostIndex}`;
      render();
      showToast(`已添加 ${ip}（模拟）`);
      return;
    }
    case 'keypad': {
      const input = $('#addIp');
      if (!input) return;
      const key = el.dataset.key;
      if (key === 'back') input.value = input.value.slice(0, -1);
      else input.value += key;
      state.addIp = input.value;
      return;
    }
    case 'select-app': {
      state.appIndex = Number(el.dataset.index);
      state.focusId = `app-${state.appIndex}`;
      render();
      return;
    }
    case 'launch-app': {
      if (!h || !online(h)) { showToast('主机离线，无法启动'); return; }
      state.page = 'stream';
      state.streamMode = 'local';
      state.streamPane = 'console';
      state.toolTab = 'touchpad';
      state.kbMods = [];
      state.kbSymbols = false;
      state.kbLast = '';
      state.streamTab = 'status';
      state.overlay = null;
      state.overlayReturn = null;
      state.focusId = 'tab-status';
      render();
      showToast('启动串流（模拟）');
      return;
    }
    case 'toggle-easytier': {
      state.easyRunning = !state.easyRunning;
      state.focusId = 'remote-toggle';
      render();
      showToast(state.easyRunning ? 'EasyTier 已启动（模拟）' : 'EasyTier 已停止（模拟）');
      return;
    }
    case 'copy-pc': showToast('PC 配置已复制（模拟，不包含真实密钥）'); return;
    case 'open-qr': openOverlay('qr', state.overlay === 'pair' ? 'pair' : null); return;
    case 'pair-qr': openOverlay('qr', 'pair'); return;
    case 'qr-copy': showToast(state.overlayReturn === 'pair' ? '配对链接已复制（模拟）' : '配置已复制（模拟）'); return;
    case 'select-cat': {
      const idx = settingsGroups.findIndex((g) => g.id === el.dataset.cat);
      if (idx < 0) return;
      state.settingsCat = idx;
      state.focusId = `setting-${settingsGroups[idx].id}-0`;
      render();
      return;
    }
    case 'toggle-setting': {
      const g = settingsGroups.find((x) => x.id === el.dataset.group);
      const i = Number(el.dataset.index);
      const row = g && g.rows[i];
      if (!row) return;
      row.on = !row.on;
      row.value = row.on ? '开' : '关';
      state.focusId = `setting-${g.id}-${i}`;
      render();
      showToast(`${row.title} → ${row.value}`);
      return;
    }
    case 'cycle-setting': {
      const g = settingsGroups.find((x) => x.id === el.dataset.group);
      const i = Number(el.dataset.index);
      const row = g && g.rows[i];
      if (!row) return;
      state.focusId = `setting-${g.id}-${i}`;
      cycleValue(row, () => render());
      return;
    }
    case 'setting-info': {
      const g = settingsGroups.find((x) => x.id === el.dataset.group);
      const row = g && g.rows[Number(el.dataset.index)];
      if (row) showToast(`${row.title}：${row.value}`);
      return;
    }
    case 'stream-tab': {
      state.streamTab = el.dataset.tab;
      state.focusId = `tab-${state.streamTab}`;
      render();
      return;
    }
    case 'stream-row': {
      const i = Number(el.dataset.row);
      const row = streamRows[state.streamTab][i];
      if (!row) return;
      state.focusId = `srow-${state.streamTab}-${i}`;
      cycleValue(row, () => render());
      return;
    }
    case 'switch-pane': {
      state.streamPane = el.dataset.pane;
      if (state.streamPane === 'console') { state.kbMods = []; state.kbSymbols = false; }
      state.focusId = state.streamPane === 'tools' ? `tool-tab-${state.toolTab}` : 'pane-console';
      render();
      showToast(state.streamPane === 'tools' ? '工具：触摸板 / 键盘 / 快捷键' : '返回控制台');
      return;
    }
    case 'tool-tab': {
      state.toolTab = el.dataset.tab;
      if (state.toolTab !== 'keyboard') { state.kbMods = []; state.kbSymbols = false; }
      state.focusId = `tool-tab-${state.toolTab}`;
      render();
      return;
    }
    case 'pad-click': {
      const button = el.dataset.button === '16' ? '中键' : el.dataset.button === '2' ? '右键' : '左键';
      showToast(`${button}（模拟）`);
      return;
    }
    case 'pad-drag': {
      state.padDragLock = !state.padDragLock;
      state.focusId = 'pad-drag';
      render();
      showToast(state.padDragLock ? '拖拽锁定：开（模拟）' : '拖拽锁定：关');
      return;
    }
    case 'pad-sensitivity': {
      const values = [0.8, 1.0, 1.2, 1.5];
      const i = values.indexOf(state.padSensitivity);
      state.padSensitivity = values[(i + 1 + values.length) % values.length];
      state.focusId = 'pad-sens-btn';
      render();
      showToast(`触摸板灵敏度 ${state.padSensitivity.toFixed(1)}×`);
      return;
    }
    case 'send-key': {
      const label = el.dataset.label || el.dataset.key;
      const mods = state.kbMods.slice();
      const text = mods.length ? `${mods.join(' + ')} + ${label}` : label;
      state.kbLast = `已发送 ${text}（模拟）`;
      const status = document.getElementById('kb-status-text');
      if (status) status.textContent = state.kbLast;
      return;
    }
    case 'toggle-key-mod': {
      const key = el.dataset.key;
      state.kbMods = state.kbMods.includes(key) ? state.kbMods.filter((k) => k !== key) : [...state.kbMods, key];
      state.focusId = el.dataset.focus;
      render();
      return;
    }
    case 'toggle-symbols': {
      state.kbSymbols = !state.kbSymbols;
      state.focusId = el.dataset.focus;
      render();
      return;
    }
    case 'system-ime': {
      showToast('系统输入法由 Android 绘制；M0.5 验证下屏 IME');
      return;
    }
    case 'shortcut': {
      if (el.dataset.label === 'Win + L') { openOverlay('lock'); return; }
      showToast(`已发送 ${el.dataset.label}（模拟）`);
      return;
    }
    case 'stream-action': {
      showToast(el.dataset.message || '动作（模拟）');
      return;
    }
    case 'confirm-lock': {
      state.overlay = null;
      state.overlayReturn = null;
      state.focusId = 'sc-11';
      render();
      showToast('已发送 Win + L（模拟）');
      return;
    }
    case 'toggle-mode': toggleMode(); return;
    case 'enter-local': {
      state.streamMode = 'local';
      state.focusId = state.streamPane === 'tools' ? `tool-tab-${state.toolTab}` : `tab-${state.streamTab}`;
      render();
      showToast('进入本地操作');
      return;
    }
    case 'resume-game': {
      state.streamMode = 'passthrough';
      state.kbMods = [];
      state.kbSymbols = false;
      state.focusId = 'enter-local';
      render();
      showToast('手柄已交还电脑');
      return;
    }
    case 'exit-stream': openOverlay('exit'); return;
    case 'confirm-exit': {
      state.overlay = null;
      state.overlayReturn = null;
      state.kbMods = [];
      state.page = 'apps';
      state.focusId = `app-${state.appIndex}`;
      render();
      showToast('已断开串流（模拟）');
      return;
    }
    case 'close-overlay': closeOverlay(); return;
    case 'single-tab': {
      state.singleTab = el.dataset.tab;
      state.focusId = `single-tab-${state.singleTab}`;
      render();
      return;
    }
    case 'single-collapse': {
      state.singleCollapsed = true;
      state.focusId = 'single-expand';
      render();
      return;
    }
    case 'single-expand': {
      state.singleCollapsed = false;
      state.focusId = `single-tab-${state.singleTab}`;
      render();
      return;
    }
    case 'single-row': {
      const i = Number(el.dataset.row);
      const row = (streamRows[state.singleTab] || [])[i];
      if (!row) return;
      state.focusId = `single-row-${state.singleTab}-${i}`;
      cycleValue(row, () => render());
      return;
    }
    case 'refresh': showToast('已刷新（模拟）'); return;
    case 'open-pair': openOverlay('pair'); return;
    default: showToast('原型未实现此操作');
  }
}

/* ----------------------------- keyboard / back ----------------------------- */

function activateFocused() {
  const el = $('.focused', activeScope()) || focusables()[0];
  if (!el) return;
  if (el.dataset.page) { setPage(el.dataset.page, { fromRail: true }); return; }
  if (el.dataset.action) { runAction(el.dataset.action, el); return; }
  el.click();
}

function back() {
  if (state.overlay) { closeOverlay(); return; }
  if (state.view === 'single') { setView('dual'); return; }
  if (state.page === 'stream') {
    if (state.streamPane === 'tools') {
      state.streamPane = 'console';
      state.kbMods = [];
      state.kbSymbols = false;
      state.focusId = 'pane-tools';
      render();
      showToast('已返回控制台');
      return;
    }
    if (state.streamMode === 'local') {
      state.streamMode = 'passthrough';
      state.focusId = 'enter-local';
      render();
      showToast('已恢复直通；再按返回进入本地');
      return;
    }
    state.streamMode = 'local';
    state.focusId = 'pane-console';
    render();
    showToast('进入本地操作');
    return;
  }
  if (state.page !== 'hosts') { setPage('hosts', { focus: hosts.length ? `host-${state.hostIndex}` : 'host-add' }); return; }
  showToast('已在根页面');
}

function handleKeydown(e) {
  const tag = (e.target.tagName || '').toLowerCase();
  const isText = tag === 'input' || tag === 'textarea';
  if (isText) {
    if (e.key === 'Escape') { e.preventDefault(); e.target.blur(); closeOverlay(); }
    return;
  }
  const k = e.key;
  if (k === 'ArrowLeft' || k === 'ArrowRight' || k === 'ArrowUp' || k === 'ArrowDown') {
    e.preventDefault();
    moveFocus({ ArrowLeft: 'left', ArrowRight: 'right', ArrowUp: 'up', ArrowDown: 'down' }[k]);
    return;
  }
  if (k === 'Enter' || k === ' ') { e.preventDefault(); activateFocused(); return; }
  if (k === 'Escape' || k === 'Backspace') { e.preventDefault(); back(); return; }
  if (k === 'Tab') { e.preventDefault(); moveFocusTab(e.shiftKey ? -1 : 1); return; }
  const lower = k.toLowerCase();
  if (state.overlay && ['q', 'e', 'r', 'y', '1', '2'].includes(lower)) return;
  if (lower === 'y') {
    e.preventDefault();
    if (state.page === 'stream' && state.streamMode === 'local' && state.view === 'dual') {
      state.streamPane = state.streamPane === 'tools' ? 'console' : 'tools';
      if (state.streamPane === 'console') { state.kbMods = []; state.kbSymbols = false; }
      state.focusId = state.streamPane === 'tools' ? `tool-tab-${state.toolTab}` : 'pane-console';
      render();
      showToast(state.streamPane === 'tools' ? '工具：触摸板 / 键盘 / 快捷键' : '返回控制台');
    } else {
      showToast(state.view === 'single' ? 'Y：单屏工具在 P1 接入' : 'Y：串流本地模式打开工具');
    }
    return;
  }
  if (lower === 'q' || lower === 'e') {
    e.preventDefault();
    const step = lower === 'q' ? -1 : 1;
    if (state.view === 'single') {
      if (state.singleCollapsed) { showToast('先展开控制台'); return; }
      const ids = singleTabs.map((t) => t.id);
      const i = ids.indexOf(state.singleTab);
      state.singleTab = ids[(i + step + ids.length) % ids.length];
      state.focusId = state.singleTab === 'status' ? 'single-tab-status' : `single-row-${state.singleTab}-0`;
      render();
      return;
    }
    if (state.page === 'stream') {
      if (state.streamMode === 'passthrough') {
        showToast('直通中：按 R 或触摸进入本地操作');
        return;
      }
      if (state.streamPane === 'tools') {
        const ids = toolTabs.map((t) => t.id);
        const i = ids.indexOf(state.toolTab);
        state.toolTab = ids[(i + step + ids.length) % ids.length];
        if (state.toolTab !== 'keyboard') { state.kbMods = []; state.kbSymbols = false; }
        state.focusId = toolFirstFocus(state.toolTab);
        render();
        return;
      }
      const ids = streamTabs.map((t) => t.id);
      const i = ids.indexOf(state.streamTab);
      state.streamTab = ids[(i + step + ids.length) % ids.length];
      state.focusId = state.streamTab === 'status' ? 'tab-status' : `srow-${state.streamTab}-0`;
      render();
      return;
    }
    if (state.page === 'settings') {
      const i = state.settingsCat;
      const next = (i + step + settingsGroups.length) % settingsGroups.length;
      state.settingsCat = next;
      state.focusId = `setting-${settingsGroups[next].id}-0`;
      render();
      return;
    }
    return;
  }
  if (lower === 'r') {
    e.preventDefault();
    if (state.page === 'stream') toggleMode();
    else showToast('R：只在串流页切换直通/本地');
    return;
  }
  if (lower === '1' || lower === '2') {
    e.preventDefault();
    showToast('L2 / R2 二级页签将在生产版接入');
  }
}

/* ----------------------------- events ----------------------------- */

function handleUiClick(e) {
  const overlay = e.target.closest('.overlay');
  if (overlay && e.target === overlay) { closeOverlay(); return; }
  const pageEl = e.target.closest('[data-page]');
  if (pageEl) {
    e.preventDefault();
    setPage(pageEl.dataset.page, { fromRail: !!pageEl.closest('.rail') });
    return;
  }
  const actionEl = e.target.closest('[data-action]');
  if (actionEl) {
    e.preventDefault();
    const focusEl = actionEl.closest('.focusable');
    if (focusEl) setFocus(focusEl, false);
    runAction(actionEl.dataset.action, actionEl);
    return;
  }
  const focusEl = e.target.closest('.focusable');
  if (focusEl) setFocus(focusEl, false);
}

bottomScreen.addEventListener('click', handleUiClick);
singleView.addEventListener('click', handleUiClick);

function movePadCursor(e) {
  const pad = e.target.closest && e.target.closest('[data-pad-surface]');
  if (!pad) return;
  const r = pad.getBoundingClientRect();
  if (!r.width || !r.height) return;
  const x = Math.min(Math.max(e.clientX - r.left, 0), r.width);
  const y = Math.min(Math.max(e.clientY - r.top, 0), r.height);
  const dot = pad.querySelector('.pad-cursor');
  if (dot) {
    dot.style.left = `${(x / r.width) * 100}%`;
    dot.style.top = `${(y / r.height) * 100}%`;
  }
}
bottomScreen.addEventListener('pointermove', movePadCursor);
bottomScreen.addEventListener('pointerdown', movePadCursor);
document.addEventListener('keydown', handleKeydown);

$$('.proto-nav [data-page]').forEach((b) => {
  b.addEventListener('click', () => setPage(b.dataset.page));
});
$('.proto-nav [data-action="open-pair"]').addEventListener('click', () => {
  state.focusId = null;
  openOverlay('pair');
});
viewToggle.addEventListener('click', () => setView(state.view === 'dual' ? 'single' : 'dual'));

/* ----------------------------- toast ----------------------------- */

const toastEl = document.createElement('div');
toastEl.className = 'toast';
document.body.appendChild(toastEl);

function showToast(message) {
  toastEl.textContent = message;
  toastEl.classList.add('show');
  clearTimeout(state.toastTimer);
  state.toastTimer = setTimeout(() => toastEl.classList.remove('show'), 2200);
}

/* ----------------------------- boot ----------------------------- */

const boot = new URLSearchParams(window.location.search);
const bootPage = boot.get('page');
if (['hosts', 'apps', 'remote', 'settings', 'stream'].includes(bootPage)) state.page = bootPage;
if (boot.get('view') === 'single') state.view = 'single';
if (boot.get('overlay') && ['pair', 'add', 'qr', 'exit', 'delete', 'lock'].includes(boot.get('overlay'))) state.overlay = boot.get('overlay');
if (boot.get('mode') === 'passthrough' || boot.get('mode') === 'local') state.streamMode = boot.get('mode');
const bootTab = boot.get('tab') === 'performance' ? 'status' : boot.get('tab');
if (streamTabs.some((t) => t.id === bootTab)) state.streamTab = bootTab;
if (boot.get('pane') === 'tools' || boot.get('pane') === 'console') state.streamPane = boot.get('pane');
if (toolTabs.some((t) => t.id === boot.get('tool'))) state.toolTab = boot.get('tool');
if (singleTabs.some((t) => t.id === boot.get('singleTab'))) state.singleTab = boot.get('singleTab');
state.focusId = defaultFocusId();
render();

