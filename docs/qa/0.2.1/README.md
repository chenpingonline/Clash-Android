# 0.2.1-dev 补测与修复

日期：2026-10-23。主要环境：Pixel 9 Pro arm64 模拟器，Android 16 / API 36，4 KiB 页。本轮在 0.2.0 基础上增加业务交互、外部接口协议、系统文件与授权流程验证，不等于真机全场景验收。

## 已完成的检查

| 范围 | 实际证据 | 结论 |
|---|---|---|
| 全部二级页面 | 32 页 × 中文常规/英文 150% 字号 × 浅/深色，128 张截图；另测无配置时进入/返回 | 自动遍历通过；抽查实际截图后修复大字号选项布局 |
| 编辑操作 | 真实控件新建代理组、编辑带 no-resolve 规则、保存端口、非法端口/MTU 拒绝与修正 | 保存结果和底层配置一致，非法输入不覆盖旧配置 |
| HTTPS 订阅 | 本地 TLS 服务：下载、配额响应头、普通更新、运行中暂存、非法 YAML、401、重定向、超过 8 MiB | 成功更新与失败保留原配置通过 |
| 自动任务 | 直接调用真实 SubscriptionWorker 入口，验证到期下载与执行记录 | 工作逻辑通过；不宣称 OS 长周期准点调度已验收 |
| 远程控制协议 | TLS 服务验证 Bearer、路径前缀、GET/PATCH/PUT/DELETE、204、401/404/500/302、WSS、日志脱敏 | 协议客户端通过；服务为受控夹具，并非真实用户控制器联调 |
| 配置/备份 | 增强保留、非法引用拒绝、备份恢复、排除设置的恢复、损坏设置拒绝、已删除配置拒绝迟到更新 | 管理事务回归通过 |
| 系统文件 | 系统选择器导出 JSON/YAML、选择 JSON、校验确认恢复；导出并解包诊断 ZIP | 系统 SAF 流程通过；诊断只含摘要和说明 |
| 本机 VPN | TUN TCP、UDP、DNS、显式 HTTP、真实 SOCKS5 上游转发计数、模式/规则切换、REJECT、重复启停 | 本地确定性夹具通过，非公网节点测试 |
| 异常恢复/应用路由 | 非法 MTU 启动失败后重试、IPv6 TUN 地址建立、仅选中/排除 shell 应用的实际流量 | 通过；IPv6 接口建立不代表公网 IPv6 通路通过 |
| 系统授权 | 实际系统授权弹窗取消、重新授权、从系统 VPN 设置断开 | 通过且配置保留；系统断开不等于取消持久授权 |
| GEO | Go 下载路径写入真实有效的 GeoIP/GeoSite protobuf 数据，再返回损坏数据 | 校验失败时原文件保留；MMDB/ASN 与公网 GEO 来源未在设备上验证 |
| 覆盖升级 | 独立 16 GiB 数据盘模拟器：安装 0.2.0、写入并持久化测试配置、install -r 0.2.1、读取校验 | 加密配置/增强/主题/语言/字号均保留，见 [升级记录](upgrade-verify.log) |
| 快捷磁贴/日志级别 | 通过系统 statusbar 调用真实磁贴启停；在内核页切换 debug 后读取内核级别；停止时拒绝更改 | 通过，见 [磁贴记录](tile-device.log) |
| 构建与静态检查 | Go race 测试、共享配置/规则导入包测试、Kotlin 单元测试、Gradle assemble/lint | 通过；Lint 有建议性警告，macOS Go 链接器有已知警告 |

主回归 [10 项设备测试结果](instrumentation.xml) 为 0 失败、0 跳过。诊断 ZIP 补测见 [设备日志](zip-device.log)。所有测试只使用合成配置和临时服务，未使用或探测用户订阅/远程实例。

## 本轮修复

- 新建代理组类型改为 select；修复原先误用 ss。
- 规则表单保留 no-resolve；YAML 切回表单时重载列表，移除/排序后清空过期编辑索引。
- 删除配置同时清理其更新间隔；已删除配置的迟到更新不再重新创建该配置。
- 不含设置的备份恢复保留当前主题、语言和应用设置；恢复前校验远程实例、VPN 参数及更新间隔。
- 远程日志与 HTTP 请求共用 HTTPS 地址校验，禁止日志连接自动跳转。
- 无配置时显示可理解的空状态，避免编辑页面一直显示加载条。
- 大字号/多选项使用自动换行，避免英文单词被挤成竖排。
- 快捷磁贴订阅运行状态并在启停中禁用重复操作；内核页读取实际日志等级，停止状态下不再接受无效的运行等级设置。
- 当前配置没有启用 GEO 资源时明确提示无需更新；关于页从 BuildConfig 读取版本号。

## 仍未覆盖的项目

没有可用真机、真实订阅凭据或实际远程控制器，本轮没有把测试夹具等同于这些环境。仍需：公网节点的各类协议和测速；公网 IPv6、Wi-Fi/移动网络切换；系统 Always-on/阻断、各厂商磁贴兼容性；OEM 电池策略、长时间后台/系统回收；其他 Android API/厂商、16 KiB 页运行环境；GEO 公网下载和周期更新；大规模节点/连接性能基准。原生库 ELF 段 16 KiB 对齐已经检查，但不是 16 KiB 设备运行证明。

## 复现

启动 `python3 scripts/qa/network-fixture.py`。夹具只监听宿主 loopback：HTTP 18080、UDP 18081、DNS 18053、SOCKS5 18082（仅允许转发到其自身 HTTP 服务）。

```sh
./scripts/build-debug.sh
./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=cn.nascp.clash.ManagementIntegrationTest,cn.nascp.clash.ExternalServicesTest,cn.nascp.clash.EditorInteractionTest,cn.nascp.clash.DocumentFlowTest,cn.nascp.clash.NetworkIntegrationTest,cn.nascp.clash.VpnConsentTest,cn.nascp.clash.PageCoverageTest \
  -Pandroid.testInstrumentationRunnerArguments.networkFixture=true
```

HTTPS 夹具证书只在测试进程临时信任，并在 finally 恢复；产品代码未添加信任所有证书或明文远程控制开关。Gradle 设备测试会安装/卸载开发包，应使用专用模拟器。原始证据：[构建与回归](qa-final-tests.log)、[Go 测试](qa-go.log)。

## 页面截图

截图证明该显示组合下的实际渲染，不代表每个外部服务已联调。所有页面均包含进入、返回检查；关键编辑/文件/授权另有业务操作测试。

| 页面 | 中文浅色 | 中文深色 | 英文大字号浅色 | 英文大字号深色 |
|---|---|---|---|---|
| 首次使用 | [查看](clash-uiqa/light-zh-setup.png) | [查看](clash-uiqa/dark-zh-setup.png) | [查看](clash-uiqa/light-en-setup.png) | [查看](clash-uiqa/dark-en-setup.png) |
| 代理组详情 | [查看](clash-uiqa/light-zh-group.png) | [查看](clash-uiqa/dark-zh-group.png) | [查看](clash-uiqa/light-en-group.png) | [查看](clash-uiqa/dark-en-group.png) |
| 节点编辑 | [查看](clash-uiqa/light-zh-node-edit.png) | [查看](clash-uiqa/dark-zh-node-edit.png) | [查看](clash-uiqa/light-en-node-edit.png) | [查看](clash-uiqa/dark-en-node-edit.png) |
| 代理组编辑 | [查看](clash-uiqa/light-zh-group-edit.png) | [查看](clash-uiqa/dark-zh-group-edit.png) | [查看](clash-uiqa/light-en-group-edit.png) | [查看](clash-uiqa/dark-en-group-edit.png) |
| 添加配置 | [查看](clash-uiqa/light-zh-import.png) | [查看](clash-uiqa/dark-zh-import.png) | [查看](clash-uiqa/light-en-import.png) | [查看](clash-uiqa/dark-en-import.png) |
| 订阅详情 | [查看](clash-uiqa/light-zh-profile.png) | [查看](clash-uiqa/dark-zh-profile.png) | [查看](clash-uiqa/light-en-profile.png) | [查看](clash-uiqa/dark-en-profile.png) |
| 订阅信息 | [查看](clash-uiqa/light-zh-profile-info.png) | [查看](clash-uiqa/dark-zh-profile-info.png) | [查看](clash-uiqa/light-en-profile-info.png) | [查看](clash-uiqa/dark-en-profile-info.png) |
| 路由规则 | [查看](clash-uiqa/light-zh-rules.png) | [查看](clash-uiqa/dark-zh-rules.png) | [查看](clash-uiqa/light-en-rules.png) | [查看](clash-uiqa/dark-en-rules.png) |
| 编辑规则 | [查看](clash-uiqa/light-zh-rule-edit.png) | [查看](clash-uiqa/dark-zh-rule-edit.png) | [查看](clash-uiqa/light-en-rule-edit.png) | [查看](clash-uiqa/dark-en-rule-edit.png) |
| 导入规则集 | [查看](clash-uiqa/light-zh-rule-import.png) | [查看](clash-uiqa/dark-zh-rule-import.png) | [查看](clash-uiqa/light-en-rule-import.png) | [查看](clash-uiqa/dark-en-rule-import.png) |
| 规则集详情 | [查看](clash-uiqa/light-zh-rule-set.png) | [查看](clash-uiqa/dark-zh-rule-set.png) | [查看](clash-uiqa/light-en-rule-set.png) | [查看](clash-uiqa/dark-en-rule-set.png) |
| 扩展覆写 | [查看](clash-uiqa/light-zh-override.png) | [查看](clash-uiqa/dark-zh-override.png) | [查看](clash-uiqa/light-en-override.png) | [查看](clash-uiqa/dark-en-override.png) |
| 扩展脚本 | [查看](clash-uiqa/light-zh-script.png) | [查看](clash-uiqa/dark-zh-script.png) | [查看](clash-uiqa/light-en-script.png) | [查看](clash-uiqa/dark-en-script.png) |
| YAML 配置 | [查看](clash-uiqa/light-zh-yaml.png) | [查看](clash-uiqa/dark-zh-yaml.png) | [查看](clash-uiqa/light-en-yaml.png) | [查看](clash-uiqa/dark-en-yaml.png) |
| 配置历史 | [查看](clash-uiqa/light-zh-history.png) | [查看](clash-uiqa/dark-zh-history.png) | [查看](clash-uiqa/light-en-history.png) | [查看](clash-uiqa/dark-en-history.png) |
| 连接详情 | [查看](clash-uiqa/light-zh-connection.png) | [查看](clash-uiqa/dark-zh-connection.png) | [查看](clash-uiqa/light-en-connection.png) | [查看](clash-uiqa/dark-en-connection.png) |
| VPN 设置 | [查看](clash-uiqa/light-zh-vpn.png) | [查看](clash-uiqa/dark-zh-vpn.png) | [查看](clash-uiqa/light-en-vpn.png) | [查看](clash-uiqa/dark-en-vpn.png) |
| 分应用代理 | [查看](clash-uiqa/light-zh-apps.png) | [查看](clash-uiqa/dark-zh-apps.png) | [查看](clash-uiqa/light-en-apps.png) | [查看](clash-uiqa/dark-en-apps.png) |
| 网络与端口 | [查看](clash-uiqa/light-zh-network.png) | [查看](clash-uiqa/dark-zh-network.png) | [查看](clash-uiqa/light-en-network.png) | [查看](clash-uiqa/dark-en-network.png) |
| DNS 与解析 | [查看](clash-uiqa/light-zh-dns.png) | [查看](clash-uiqa/dark-zh-dns.png) | [查看](clash-uiqa/light-en-dns.png) | [查看](clash-uiqa/dark-en-dns.png) |
| DNS 服务器 | [查看](clash-uiqa/light-zh-dns-servers.png) | [查看](clash-uiqa/dark-zh-dns-servers.png) | [查看](clash-uiqa/light-en-dns-servers.png) | [查看](clash-uiqa/dark-en-dns-servers.png) |
| Fake IP 与域名策略 | [查看](clash-uiqa/light-zh-fakeip.png) | [查看](clash-uiqa/dark-zh-fakeip.png) | [查看](clash-uiqa/light-en-fakeip.png) | [查看](clash-uiqa/dark-en-fakeip.png) |
| DNS 回退过滤 | [查看](clash-uiqa/light-zh-fallback.png) | [查看](clash-uiqa/dark-zh-fallback.png) | [查看](clash-uiqa/light-en-fallback.png) | [查看](clash-uiqa/dark-en-fallback.png) |
| Hosts 映射 | [查看](clash-uiqa/light-zh-hosts.png) | [查看](clash-uiqa/dark-zh-hosts.png) | [查看](clash-uiqa/light-en-hosts.png) | [查看](clash-uiqa/dark-en-hosts.png) |
| 内核管理 | [查看](clash-uiqa/light-zh-core.png) | [查看](clash-uiqa/dark-zh-core.png) | [查看](clash-uiqa/light-en-core.png) | [查看](clash-uiqa/dark-en-core.png) |
| GEO 数据 | [查看](clash-uiqa/light-zh-geo.png) | [查看](clash-uiqa/dark-zh-geo.png) | [查看](clash-uiqa/light-en-geo.png) | [查看](clash-uiqa/dark-en-geo.png) |
| 自动化与后台运行 | [查看](clash-uiqa/light-zh-automation.png) | [查看](clash-uiqa/dark-zh-automation.png) | [查看](clash-uiqa/light-en-automation.png) | [查看](clash-uiqa/dark-en-automation.png) |
| 外观与语言 | [查看](clash-uiqa/light-zh-appearance.png) | [查看](clash-uiqa/dark-zh-appearance.png) | [查看](clash-uiqa/light-en-appearance.png) | [查看](clash-uiqa/dark-en-appearance.png) |
| 备份与恢复 | [查看](clash-uiqa/light-zh-backup.png) | [查看](clash-uiqa/dark-zh-backup.png) | [查看](clash-uiqa/light-en-backup.png) | [查看](clash-uiqa/dark-en-backup.png) |
| 关于与更新 | [查看](clash-uiqa/light-zh-about.png) | [查看](clash-uiqa/dark-zh-about.png) | [查看](clash-uiqa/light-en-about.png) | [查看](clash-uiqa/dark-en-about.png) |
| 远程控制器 | [查看](clash-uiqa/light-zh-remote.png) | [查看](clash-uiqa/dark-zh-remote.png) | [查看](clash-uiqa/light-en-remote.png) | [查看](clash-uiqa/dark-en-remote.png) |
| 诊断与导出 | [查看](clash-uiqa/light-zh-diagnostics.png) | [查看](clash-uiqa/dark-zh-diagnostics.png) | [查看](clash-uiqa/light-en-diagnostics.png) | [查看](clash-uiqa/dark-en-diagnostics.png) |
