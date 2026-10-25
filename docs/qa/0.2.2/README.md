# 0.2.2-dev VPN 阻断与重启恢复验证

日期：2026-10-24（Asia/Shanghai）。设备：现有 Pixel 9 Pro arm64 Android 16 / API 36 模拟器，4 KiB 页。仅使用合成订阅配置与宿主 loopback 测试服务器，未访问用户订阅。

## 修复

- 取消把 Clash 自身 UID 排除在 VPN 外的做法。仅选中模式也加入自身 UID，使订阅等应用请求可经过 VPN；不能依靠 UID 排除维持阻断模式联网。
- Android 专用 Go 适配通过 Mihomo DefaultSocketHook 在 TCP connect、UDP socket 建立时调用 VpnService.protect。JNI 缓存类/方法，正确附着和分离 Go 线程；回调不获取 NativeCore.invoke 的锁，保护失败返回错误，不继续建立回环连接。
- 服务在建立 TUN 前注册保护对象，停止/失败/销毁时释放。网络回调监听非 VPN 底层网络。
- 连续启停回归发现 ForegroundServiceDidNotStartInTimeException：每次启动先满足前台服务要求，停止/失败清理在主线程完成后再公布状态，避免快速重试的生命周期竞态。修复后两次网络回归通过。
- 中英文 VPN/分应用说明移除“自身始终绕过”，明确阻断模式下排除应用/网段的联网限制。

## 实测结果

| 项目 | 证据 | 结果 |
|---|---|---|
| TCP/UDP/DNS、显式 HTTP、SOCKS5、REJECT、分应用、非法 MTU 恢复、重复启停 | [初次修复后回归](network.log)、[最终设备回归](final-device.log) | 通过 |
| 实际系统 UI 开启 Always-on 与 Block connections without VPN | [系统截图](lockdown-settings.png)、[系统状态](lockdown/system.log)、[设置值](lockdown/settings.log) | 两个系统开关开启 |
| 阻断模式内真实流量 | [TCP 与 SOCKS 计数](lockdown/tcp.log)、[UDP](lockdown/udp.log)、[DNS](lockdown/dns.hex) | 通过 |
| 仅始终开启，实际 adb reboot，不手动打开应用 | [系统恢复](reboot-always-on-system.log)、[请求及 SOCKS 计数](reboot-always-on-tcp.log) | 自动恢复，TCP 经 SOCKS5 |
| VPN 被强行停止、底层网络仍在 | [连接被拒绝与 VPN 状态](lockdown-stopped.log) | 新建 shell TCP 连接被系统拒绝；不是仅以断开 Wi-Fi 模拟阻断 |
| 始终开启＋阻断，实际重启 | [系统状态](reboot-lockdown/system.log)、[设置保留](reboot-lockdown/settings.log)、[TCP](reboot-lockdown/tcp.log)、[UDP](reboot-lockdown/udp.log)、[DNS](reboot-lockdown/dns.hex) | 无需手动打开应用，代理和解析恢复 |
| 最终构建覆盖安装及再次重启 | [安装后探测](final-apk-lockdown/tcp.log)、[重启探测](final-apk-reboot-lockdown/tcp.log)、[系统状态](final-apk-reboot-lockdown/system.log) | TCP 经 SOCKS5、UDP、DNS 均通过 |
| 受影响页面 | [设备结果](final-device.log)、[截图目录](screens/) | VPN/分应用两页 × 中文常规/英文150% × 浅色/深色，共8张；抽查实际渲染 |
| 静态、单元测试与打包 | [Gradle](build.log)、[Go race及共享包](go.log)、[签名](signature.log)、[对齐](alignment.log) | 通过；Go 链接器仍有已有建议警告 |

首次切换阻断开关时观察到瞬时 Permission denied，系统切换稳定后探测通过。首次重启尝试发生在测试夹具尚未结束、系统设置尚未稳定时，未计入通过证据；随后在测试结束、系统设置保存后重新验证。没有将普通联网成功等同代理成功：TCP 探测要求本地 SOCKS5 上游转发计数增加，UDP 规则单独指定 DIRECT，DNS 返回确定的 203.0.113.7。

模拟器空间不足曾阻止覆盖安装；清理系统缓存后仍不足，临时将模拟器存储预留阈值改为1%完成安装，随后删除该临时设置恢复默认。没有卸载其他项目或清除用户数据。测试结束关闭系统始终开启和阻断开关，停止 VPN，避免停止夹具后影响模拟器联网。

## 复现

仅在专用测试模拟器执行，准备步骤会持久保存合成配置：

1. 运行 `python3 scripts/qa/network-fixture.py`。
2. 构建并安装应用及 androidTest APK。
3. `adb shell am instrument -w -e class cn.nascp.clash.AlwaysOnTest -e alwaysOnFixture true cn.nascp.clash.dev.test/androidx.test.runner.AndroidJUnitRunner`，等测试完成。
4. 在实际系统 VPN 设置中启用始终开启和阻断，等待系统切换完成。
5. `python3 scripts/qa/check-lockdown.py /tmp/clash-lockdown-result`。脚本验证 TCP 经 SOCKS5、UDP、DNS，并保存系统状态。
6. 系统设置保存后 `adb reboot`，等启动完成，不启动应用，重复步骤5。
7. 关闭阻断和始终开启后运行普通网络回归。页面专项可传 `-e screenIds vpn,apps` 并指定 `PageCoverageTest#secondaryPagesRenderInBothThemes`。

## 边界

本轮不是厂商真机验证。仍需真实订阅/多种远程代理协议、公网 IPv6、Wi-Fi/移动网络切换、OEM 省电策略、过夜待机，以及有安全锁屏设备首次解锁场景。阻断检查只证明测试 UID 的新建连接被拒绝，不构成完整的 DNS/全流量泄漏审计。未增加开机解锁前读取加密配置的能力。

Android 的保护套接字与始终开启机制参考 [VpnService 官方文档](https://developer.android.com/reference/android/net/VpnService)，实际结论以以上设备证据为准。

产物：`dist/Clash-Android-0.2.2-dev-arm64.apk`，versionCode 4，debug 签名；SHA-256 见同名 `.sha256`。未提交、推送或发布。
