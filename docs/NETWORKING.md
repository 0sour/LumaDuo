# 配对、EasyTier 与远程连接

## 1. 两条独立链路

不要把“配对”和“组网”混在一起：

1. **配对**：客户端与 Foundation Sunshine 交换证书/PIN，建立可信串流关系。
2. **组网**：客户端与 PC 加入同一个 EasyTier 虚拟网络，让彼此 IP 可达。

配对成功不等于远程可达；EasyTier 通了也不等于已配对。

## 2. 配对

### 2.1 PIN（主路径）

1. 客户端添加主机（发现或手动 IP）。
2. 客户端生成 4 位 PIN，显示在下屏。
3. 用户在 Foundation Sunshine 网页输入 PIN。
4. 客户端完成 GameStream 配对，保存服务器证书。
5. 拉取应用列表。

- PIN 会话默认 5 分钟。
- PIN 只显示一次；不写日志。
- Foundation `/api/pairing/pending` 可让主机 UI 显示设备名/截止时间；客户端不需要调用。
- 失败状态：PIN 错误、已在配对、主机离线、证书错误；每种都给“重试/返回”。

### 2.2 QR（可选）

Foundation 网页可生成：

```text
moonlight://pair?host=<host>&port=<47989>&pin=<pin>&name=<device>
```

客户端用 ZXing 扫描 → 解析 host/port/pin → 添加主机 → 配对。

限制：

- RP Duo / Thor 可能没有摄像头；**没有摄像头就不显示扫码入口**。
- 无摄像头时可支持“粘贴 pairing 链接”。
- QR 里的 PIN 短期有效，仍按敏感信息处理。
- 不要把 QR 截图发到公开 Issue。

### 2.3 手动添加

- 支持 `IP`、`IP:port`、`[IPv6]:port`、主机名。
- 默认端口 47989。
- 添加前做连通性检查；失败给出“主机离线 / 端口不对 / 不在同一网络”。
- 最近连接、收藏、重命名、删除。

## 3. EasyTier

### 3.1 角色

- **客户端**：V+ 内置 EasyTier JNI + Android VpnService。
- **PC**：单独安装 EasyTier Windows 客户端；首发不自动部署。
- **不内置中继**；使用 EasyTier 公共 peer 或用户自己的 peer。

> 2026-11-20 M2b-3：下屏远程页已读真实 TOML，显示网络名、虚拟 IP、Peer、监听数，密钥只显示“已设置/未设置 · 已遮罩”；运行状态如实显示“未启动”。启动/停止仍在旧版 `PcView`，M4 迁入下屏。

### 3.2 客户端默认配置

V+ 默认：

```toml
instance_name = "Default"
hostname = "moonlight-V+"
ipv4 = "10.0.0.1/24"
dhcp = false
listeners = ["tcp://0.0.0.0:11010", "udp://0.0.0.0:11010", "wg://0.0.0.0:11011"]
rpc_portal = "0.0.0.0:0"

[network_identity]
network_name = "easytier"
network_secret = ""

[[peer]]
uri = "tcp://public.easytier.top:11010"

[flags]
```

LumaDuo 把它做成结构化表单：

- 网络名
- 密钥（掩码，显示/复制需确认）
- 本机虚拟 IP（默认 10.0.0.1）
- peers（一行一个）
- 高级 flags（折叠）
- 启动/停止 VPN
- 状态：虚拟 IP、peer、延迟、NAT、错误

### 3.3 PC 配置生成

PC 不能再用 `10.0.0.1`，否则与 Android 冲突。生成：

```toml
instance_name = "LumaDuo"
hostname = "<PC_NAME>"
ipv4 = "10.0.0.2/24"
dhcp = false
listeners = ["tcp://0.0.0.0:11010", "udp://0.0.0.0:11010", "wg://0.0.0.0:11011"]
rpc_portal = "0.0.0.0:0"

[network_identity]
network_name = "<same as client>"
network_secret = "<same as client>"

[[peer]]
uri = "tcp://public.easytier.top:11010"

[flags]
```

- 多台 PC 用 `.2`、`.3`…，不能重复。
- 如果用户有自己的 peer，替换公共 peer。
- 复制 TOML + 可选二维码；二维码内容是同一配置或 `lumaduo://easytier?...` 链接。
- 二维码/复制面板必须提示“包含网络密钥，勿公开”。
- PC 装好 EasyTier 后，Foundation 的“网络概览”应出现 `mesh: easytier` 和虚拟 IP。

### 3.4 虚拟 IP 手动添加

首发流程：

1. Android EasyTier 启动，记下本机虚拟 IP。
2. PC EasyTier 导入配置/启动，记下 PC 虚拟 IP（如 10.0.0.2）。
3. 在 LumaDuo 主机页“手动添加”填 PC 虚拟 IP。
4. 配对/串流。

后续可选：

- 读取 Foundation `/api/network/overview` 自动获取 `mesh: easytier` 地址。
- 需要 Foundation Web UI 认证；MVP 不做自动抓取。

### 3.5 安全

- `network_secret` 是网络密钥，等同于私钥；默认掩码。
- 复制/二维码前二次确认；不写日志、不上报。
- 配对 PIN 短期有效，也不写日志。
- 公共 peer 只负责发现/转发；敏感流量仍由 EasyTier 加密。
- 不把公网 IP、token、证书贴到公开 Issue。

## 4. 网络状态与错误

| 状态 | UI |
|---|---|
| EasyTier 未启动 | “未加入网络” + 启动 |
| VPN 权限拒绝 | “需要 VPN 权限” + 重试/去设置 |
| 已启动，无 peer | “等待节点” + 显示网络名/本机 IP |
| 已连接 | 显示虚拟 IP、peer、延迟 |
| 主机不可达 | “PC 不在线” + 检查 IP/防火墙/EasyTier |
| 配对失败 | PIN 错误/已配对/证书错误，分别文案 |
| 串流断线 | 重连面板；保留主机/应用 |

文案原则：原因 + 下一步，不显示原始堆栈/证书/IP 列表。

## 5. Foundation 能力

- 主机发现、配对、应用列表、串流：Moonlight/GameStream 协议。
- 网络概览：`GET /api/network/overview`，接口返回 interfaces、mesh 类型、虚拟地址。
- 配对 pending：`GET /api/pairing/pending`，长轮询；用于主机网页。
- QR 生成：`POST /api/qr-pair`，返回 `url/pin/expires_in`。
- 主机静态信息：`GET /api/system/info`，返回 CPU 型号/核心、GPU 型号/VRAM、NIC、OS、hostname。
- 主机实时负载：`GET /api/runtime/system`，返回 `cpu_percent`、`memory_total/available/percent`、`gpu_percent`、`gpu_name`；**当前实现要求 localhost**，远程客户端不能直接调用。
- 会话状态：`GET /api/runtime/sessions`，返回会话 FPS/码率/实测码率等。
- 这些 Web UI API 需要认证；Moonlight 配对证书不等于 Web UI 会话。状态页默认使用客户端实测数据，不依赖 Foundation 实时负载；主机静态/实时信息是可选补充，不能阻塞串流主路径。
- 远程实时 CPU/GPU/内存需要 Foundation 增加已配对客户端可读的只读端点，或独立的 LumaDuo Host Bridge；默认状态页不要求这些，主机负载不可得时明确显示“未提供”。

## 6. 测试

- 局域网：不启 EasyTier，配对 + 串流。
- EasyTier：Android `.1` + PC `.2` + 公共 peer；ping 通；Foundation 识别 `easytier`；Moonlight 手动 IP 可连。
- 错误：PC 离线、错误密钥、错误网络名、端口占用、VPN 权限拒绝。
- 安全：密钥不出现在 logcat/截图/崩溃报告。
