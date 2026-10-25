# 0.2.6-dev 订阅下载与 HTTPS 重定向

2026-10-24，本地 arm64 debug 构建，versionCode 8。

## 变更

- 订阅请求使用 Clash.Meta User-Agent，按 HTTP 状态提示失败原因。
- 自动跟随 301/302/303/307/308 HTTPS 跳转，支持相对路径、查询参数和协议相对地址；最多 5 次。
- 拒绝循环、缺失或无效 Location、HTTPS 降级到 HTTP；读取最终响应配额，保持原始订阅链接。

## 验证

- 使用 JDK 21 执行 scripts/build-debug.sh：共享包导出、JNI 构建、APK 构建及 Lint 通过。
- testDebugUnitTest 通过。
- Android 16 Pixel 9 Pro 模拟器 ExternalServicesTest 共 4 项通过：订阅更新/暂存/失败、HTTPS 重定向链及异常边界、远程 HTTPS/WebSocket、备份设置恢复。
- aapt 核实 APK versionName 为 0.2.6-dev、versionCode 为 8。
- apksigner verify 验签通过；zipalign -c -P 16 4 检查通过。

产物：dist/Clash-Android-0.2.6-dev-arm64.apk；SHA-256 见同名 .sha256 及 dist/SHA256SUMS。

受控 HTTPS 测试不能证明真实订阅服务商的 403 已解决；未进行真机订阅验证。未发布。

后续每订阅User-Agent、HTTP错误摘要及新增回归见[补充验证](../subscription-identity/README.md)。

完整下载60秒超时、有限重试及失败保留原配置见[补充验证](../subscription-protection/README.md)。
