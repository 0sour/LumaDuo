# 安全策略

## 1. 报告问题

如果你发现安全问题，请不要公开提交 Issue。通过仓库维护者提供的私密渠道联系，或在 GitHub 使用 Security Advisory。

请提供：

- 受影响版本/提交
- 复现步骤
- 影响范围（配对、EasyTier、串流、密钥、权限）
- 是否已在真机验证

## 2. 敏感数据

以下内容不得写入日志、截图、Issue、崩溃报告：

- Foundation 配对 PIN
- EasyTier `network_secret`
- 设备证书/私钥
- 账号 token、Cookie
- 公网 IP 列表、完整主机地址

UI 中默认掩码；复制/二维码前二次确认。

## 3. 状态页与主机数据

- 状态页只读：不调用 Foundation 的写接口，不修改分辨率、码率、HDR 或主机设置。
- 不要求用户把 Foundation 管理员密码存到掌机；没有凭据时主机静态信息显示“未提供”。
- 若接入可选 Host Bridge：只开放只读指标，使用独立 token/证书认证，绑定 EasyTier/局域网，不把 token 写入日志。
- 状态页只保留最近 60s 的内存数据用于趋势；不记录完整主机地址、密钥或证书。

## 4. 网络

- 不在客户端内置中继/穿透服务。
- 不关闭 TLS 校验。
- 不信任自签名证书，除非用户明确导入并确认。
- 公共 EasyTier peer 只做发现/转发；敏感流量由 EasyTier 加密。
- 配对 QR 短期有效，仍按敏感信息处理。

## 5. 权限

- VPN 权限仅用于 EasyTier。
- 不请求 root。
- Shizuku 相关功能（如后续启用）必须显式授权；默认不依赖。
- 摄像头权限仅在用户打开 QR 扫描时请求；无摄像头设备不显示入口。

## 6. 供应链

- 固定 V+ baseline tag、audio-haptics commit。
- 不提交 keystore、google-services.json、secrets.properties。
- 第三方库升级需检查许可证与来源。
- 发布包移除遥测；默认不接 Firebase/Crashlytics。

## 7. 支持范围

- 首发：RP Duo；Thor 第二。
- 只支持未 root 的 stock/官方固件；第三方 ROM 记录已知问题。
- 不提供破解、注入、绕过 DRM 的支持。
