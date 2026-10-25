# 0.2.0-dev 实现与验收记录

此页为历史记录；新增验证与修复见 [0.2.1](../0.2.1/README.md)。

2026-10-23；Pixel 9 Pro arm64 模拟器，Android 16 / API 36，4 KiB 页；本地 debug APK。以下区分代码接线、模拟器验证、尚未验证，不将页面存在视为功能全部通过。

## 本轮检查结果

- Go `go test -race ./... clashmanager/configyaml clashmanager/ruleimport` 通过；含配置增强、导入规划、移动端边界、端口与 MATCH 顺序校验、日志脱敏。macOS 链接器 LC_DYSYMTAB 警告不影响测试结果。
- Gradle assembleDebug、lintDebug、testDebugUnitTest 通过。Lint 无阻断错误，保留依赖更新等建议。
- 三项设备集成测试通过，0 失败、0 跳过，见 [JUnit 结果](instrumentation.xml)。
- ManagementIntegrationTest：保存增强、非法引用拒绝、原始订阅刷新后增强保留、网络字段、备份校验与恢复、未知备份版本拒绝。
- NetworkIntegrationTest：真实 VPN 的 TCP HTTP、UDP 回显、DNS A 记录、显式 HTTP 代理、模式切换、规则启停、REJECT 阻断与 DIRECT 恢复、停止后重启。使用宿主本地固定服务，无外网依赖；测试以 appops 预授予 VPN 权限，不能替代系统授权弹窗验收。
- PageCoverageTest：进入关于目录，再逐个进入其余 31 个二级页面，分别切换浅色和深色并返回，保存 62 张实际设备截图。关于页面作为导航枢纽在两种主题下使用，但未单独截图。网络表单与 YAML 编辑器等截图已人工核对渲染。
- 修复了重复启动时代理端口未释放问题，并在网络集成测试中覆盖。编辑器加入行号与语法着色；网络等表单使用统一保存与布尔开关。

## 需求覆盖

“已接线”表示实际调用持久化/内核/API，无通用草稿占位表单；不等于以下所有外部环境均已验收。

| 需求 | 已接线实现 | 验证与限制 |
|---|---|---|
| R01 首次使用/首页 | 配置、VPN、流量、模式、路由出口探测 | 本地网络集成通过；公网出口与真机授权拒绝/撤销未全面复测 |
| R02 节点与组 | 搜索/筛选/排序、批量测速、编辑/顺序/重置、组选择 | 增强与引用校验通过；实际远程协议与延迟服务未验证 |
| R03 订阅 | URL/文件/文本、配额响应头、自动下载与暂存、应用 | 本地管理事务通过；真实 HTTPS 订阅未提供，调度长周期待验 |
| R04 增强 | 固定共享 Go 合并链，全局/单配置覆写与脚本 | 共享 Go 测试和 Android 管理集成通过 |
| R05 规则/规则集 | 永久编辑、运行时批量开关、导入预览、更新 | 共享解析/规划与运行时开关通过；远端资源下载待验 |
| R06 YAML/历史 | 行号、着色、搜索、校验错误、合并预览、草稿、差异恢复 | 配置拒绝/恢复测试通过；错误位置使用内核返回的行号文本，不提供点击跳行；历史记录配置本身及其增强，全局增强单独管理 |
| R07 活动 | 连接过滤/详情/批量关闭、规则链、日志暂停/导出 | 网络集成与页面通过；无法取得所属应用时明确未知，未做性能基准 |
| R08 VPN/分应用 | IPv6、MTU、路由排除、LAN 绕过、应用列表 | IPv4 CIDR 单元测试与 VPN 网络通过；真机 IPv6/工作资料/撤销待验 |
| R09 网络/DNS | 显式端口、监听、LAN、认证、DNS/Hosts | TCP/UDP/DNS/显式 HTTP 通过；LAN 跨设备与实际 DoH/DoT 待验 |
| R10 内核/GEO | 版本、重启、级别、测速参数、资源状态/来源/更新 | 启停和级别接线；实际 GEO 下载/自动更新周期待验 |
| R11 自动化/集成 | WorkManager、通知、磁贴、Always-on 入口、网络回调 | 代码与页面验证；系统回收、长后台和网络切换待真机验收 |
| R12 外观 | 双主题/系统、中文/English、字号，无额外转场 | 32 路由双主题遍历通过；大字体全矩阵与英文动态错误未全面验证 |
| R13 备份/关于/诊断 | 范围选择、预览还原、事务恢复日志、版本/许可、脱敏 ZIP | 备份恢复通过；没有 Android 发布源，不能在线下载安装升级 |
| R14 远程 | 加密实例、HTTPS API、节点/模式/规则/连接/WSS 日志 | 页面和客户端代码接线；无实际对端，联调未验证；不模拟 Manager 扩展能力 |

## 网络测试复现

在单独终端运行 `python3 scripts/qa/network-fixture.py`，然后：

```sh
./scripts/build-core.sh
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.networkFixture=true
```

夹具仅监听宿主 loopback：HTTP 18080、UDP 18081、DNS 18053；模拟器通过 10.0.2.2 访问。完成后停止夹具。测试会保存并恢复自己的配置；Gradle 设备测试可能在结束后卸载测试应用，不应用于有用户数据的设备。截图复制至 `/data/local/tmp/clash-uiqa` 后拉取到此目录。未提供夹具参数时，网络测试明确跳过。

## 发布前仍需验收

实际订阅、远程代理/控制器、GEO 下载；真机 IPv6、网络切换、Always-on/阻断、系统回收、长后台；其他 Android 版本/厂商、16 KiB 页设备、升级迁移和性能；完整传递依赖许可/源码交付。当前产物只覆盖 arm64 开发验证。未提交、推送或发布。

## 页面与双主题截图

以下截图是测试配置的实际渲染，不含真实凭据。页面覆盖测试验证进入/返回和渲染；业务操作证据见上方测试范围。

| 页面 | 浅色 | 深色 |
|---|---|---|
| 首次使用 | [查看](clash-uiqa/light-setup.png) | [查看](clash-uiqa/dark-setup.png) |
| 代理组详情 | [查看](clash-uiqa/light-group.png) | [查看](clash-uiqa/dark-group.png) |
| 节点编辑 | [查看](clash-uiqa/light-node-edit.png) | [查看](clash-uiqa/dark-node-edit.png) |
| 代理组编辑 | [查看](clash-uiqa/light-group-edit.png) | [查看](clash-uiqa/dark-group-edit.png) |
| 添加配置 | [查看](clash-uiqa/light-import.png) | [查看](clash-uiqa/dark-import.png) |
| 订阅详情 | [查看](clash-uiqa/light-profile.png) | [查看](clash-uiqa/dark-profile.png) |
| 订阅信息 | [查看](clash-uiqa/light-profile-info.png) | [查看](clash-uiqa/dark-profile-info.png) |
| 路由规则 | [查看](clash-uiqa/light-rules.png) | [查看](clash-uiqa/dark-rules.png) |
| 编辑规则 | [查看](clash-uiqa/light-rule-edit.png) | [查看](clash-uiqa/dark-rule-edit.png) |
| 导入规则集 | [查看](clash-uiqa/light-rule-import.png) | [查看](clash-uiqa/dark-rule-import.png) |
| 规则集详情 | [查看](clash-uiqa/light-rule-set.png) | [查看](clash-uiqa/dark-rule-set.png) |
| 扩展覆写 | [查看](clash-uiqa/light-override.png) | [查看](clash-uiqa/dark-override.png) |
| 扩展脚本 | [查看](clash-uiqa/light-script.png) | [查看](clash-uiqa/dark-script.png) |
| YAML 配置 | [查看](clash-uiqa/light-yaml.png) | [查看](clash-uiqa/dark-yaml.png) |
| 配置历史 | [查看](clash-uiqa/light-history.png) | [查看](clash-uiqa/dark-history.png) |
| 连接详情 | [查看](clash-uiqa/light-connection.png) | [查看](clash-uiqa/dark-connection.png) |
| VPN 设置 | [查看](clash-uiqa/light-vpn.png) | [查看](clash-uiqa/dark-vpn.png) |
| 分应用代理 | [查看](clash-uiqa/light-apps.png) | [查看](clash-uiqa/dark-apps.png) |
| 网络与端口 | [查看](clash-uiqa/light-network.png) | [查看](clash-uiqa/dark-network.png) |
| DNS 与解析 | [查看](clash-uiqa/light-dns.png) | [查看](clash-uiqa/dark-dns.png) |
| DNS 服务器 | [查看](clash-uiqa/light-dns-servers.png) | [查看](clash-uiqa/dark-dns-servers.png) |
| Fake IP 与域名策略 | [查看](clash-uiqa/light-fakeip.png) | [查看](clash-uiqa/dark-fakeip.png) |
| DNS 回退过滤 | [查看](clash-uiqa/light-fallback.png) | [查看](clash-uiqa/dark-fallback.png) |
| Hosts 映射 | [查看](clash-uiqa/light-hosts.png) | [查看](clash-uiqa/dark-hosts.png) |
| 内核管理 | [查看](clash-uiqa/light-core.png) | [查看](clash-uiqa/dark-core.png) |
| GEO 数据 | [查看](clash-uiqa/light-geo.png) | [查看](clash-uiqa/dark-geo.png) |
| 自动化与后台运行 | [查看](clash-uiqa/light-automation.png) | [查看](clash-uiqa/dark-automation.png) |
| 外观与语言 | [查看](clash-uiqa/light-appearance.png) | [查看](clash-uiqa/dark-appearance.png) |
| 备份与恢复 | [查看](clash-uiqa/light-backup.png) | [查看](clash-uiqa/dark-backup.png) |
| 关于与更新 | 作为导航枢纽遍历 | 作为导航枢纽遍历 |
| 远程控制器 | [查看](clash-uiqa/light-remote.png) | [查看](clash-uiqa/dark-remote.png) |
| 诊断与导出 | [查看](clash-uiqa/light-diagnostics.png) | [查看](clash-uiqa/dark-diagnostics.png) |
