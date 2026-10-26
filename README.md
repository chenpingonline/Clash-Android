# Clash Android

基于 **Mihomo** 的原生 Android 客户端，使用 Kotlin 与 Jetpack Compose 构建，通过 Android VPN 接管流量，无需 Root。

支持订阅与配置管理、节点切换与测速、规则分流、连接查看、远程控制，以及跟随系统的浅色与深色主题。

**[下载最新正式版](https://github.com/chenpingonline/Clash-Android/releases/latest)** · **[更新记录](CHANGELOG.md)** · **[构建说明](#本地构建)** · **[第三方来源](THIRD_PARTY.md)**

## 下载与安装

当前正式版：**[0.2.45](https://github.com/chenpingonline/Clash-Android/releases/tag/v0.2.45)**。

| 项目 | 说明 |
| --- | --- |
| 系统要求 | Android 10 及以上 |
| 支持架构 | ARM64 / arm64-v8a |
| 正式包名 | `cn.chenpingonline.clash` |
| 安装包 | `Clash-Android-0.2.45-arm64.apk`，约 24.6 MiB |
| 构建方式 | release 构建，启用 R8 优化和资源裁剪，使用专用发行签名 |

在 [Releases](https://github.com/chenpingonline/Clash-Android/releases) 下载 APK，按 Android 提示允许安装并完成安装。每个正式 Release 同时提供 SHA-256 校验文件；重建已发布版本时，请使用该 Release 附带的 `-source.tar.gz` 对应源码快照。

### 从开发版迁移

正式版 `cn.chenpingonline.clash` 与旧开发版 `cn.nascp.clash.dev` 是两个独立应用，可以同时安装，不能互相覆盖。

1. 在开发版的「设置 → 备份与恢复」导出备份。
2. 安装正式版，在同一入口恢复备份。
3. 核对配置、偏好及 VPN 连接正常后，再决定是否移除开发版。

备份和 YAML 导出可能包含订阅地址与认证信息，请妥善保存。

## 开始使用

1. 打开「配置」，通过 **HTTPS 订阅链接、文件或粘贴 YAML** 导入配置。
2. 选择配置，在「代理」切换节点或策略组，按需测速。
3. 返回首页启动 VPN，首次启动按系统提示完成 VPN 授权。

目前接受 Clash YAML 格式，不转换通用 Base64 节点订阅。订阅默认 User-Agent 为 `mihomo`，可在高级选项中自定义。

需要控制中心快捷开关时，在「设置 → 系统集成与后台」添加「Clash VPN」磁贴，或在 Android 快捷设置的编辑界面手动添加。首次使用前，需在应用中选择配置并完成 VPN 授权。

## 主要功能

| 模块 | 功能 |
| --- | --- |
| 首页 | 实时速率、累计流量、当前配置与节点，以及按当前路由查询的出口 IP、地区和运营商信息 |
| 配置与订阅 | HTTPS / 文件 / YAML 导入、订阅身份设置、配额信息、手动与周期更新 |
| 节点与策略组 | 搜索、筛选、排序、节点选择、单个与批量测速，可视化编辑及顺序调整 |
| 规则与增强 | 配置规则和规则集管理、运行规则启停、规则导入预览、全局与单配置覆写、JavaScript 扩展 |
| YAML 编辑器 | 原始配置、生效预览、搜索、行号、语法着色、内核校验、加密草稿及历史差异恢复 |
| 网络与 DNS | 代理端口、LAN、认证、DNS、Hosts、GEO 数据来源及更新设置 |
| VPN 与系统集成 | 分应用代理、IPv6、MTU、局域网绕过、排除网段、快捷磁贴及系统始终开启 VPN 的设置入口 |
| 活动与诊断 | 连接筛选与详情、应用归属、单个与批量关闭连接、日志查看与导出、诊断 ZIP |
| 远程控制 | HTTPS 控制器管理、连接测试、节点与模式切换、规则、连接及 WebSocket 日志 |
| 数据与外观 | 备份恢复、Keystore 与 AES-GCM 本地保护、主题、中文 / English、字号偏好 |
| 应用更新 | 检查 GitHub Releases、查看更新说明、下载安装包、校验并交由系统安装 |

### 配置修改如何生效

- 原始订阅与用户增强分离，刷新订阅不会直接覆盖已保存的增强。
- 全局覆写和全局扩展脚本作用于所有配置；单配置增强只作用于对应配置。
- 配置保存前执行内核校验。运行中的当前配置需停止 VPN 后修改，并在下次启动时生效。
- 运行中下载的当前订阅更新会先暂存，停止 VPN 后可在订阅详情中应用。
- 活动页的运行规则操作作用于当前运行实例；持久规则定义在配置管理中编辑。

## 应用内更新

进入 **设置 → 关于应用 → 应用更新**，手动检查、下载并安装。

- 正式版只接收正式 Release；开发版的检查范围包含预发布版本。
- 下载支持进度显示与取消。下载后及安装前检查大小、SHA-256、包名、版本和签名。
- 更新需要相同包名和签名，并递增 `versionCode`。开发版与正式版之间请按上文迁移。
- 安装由 Android 系统确认；首次使用需允许本应用安装未知应用。
- 下载依赖应用进程，不支持断点续传或进程结束后恢复；重启应用后需重新检查、下载。

更新源为本仓库的 GitHub Releases，不内置访问令牌。网络失败或请求受限会显示错误，GitHub 及其下载服务的可达性取决于当前网络。

## 使用边界

- 目前只提供 ARM64 APK。应用通过 Android VPN 工作，不提供 Root、iptables/nftables 或热点透明代理；LAN 显式代理需由客户端单独配置。
- 应用自身的网络请求受当前 VPN 路由影响。内核出站套接字通过 `VpnService.protect` 防止回环；出口探测结果反映当前实际路由。
- 应用归属受 Android 包可见性和系统 UID 查询限制。无法识别时显示未知，共享 UID 不指认单个应用。
- 周期订阅更新最短间隔为 15 分钟，受系统网络、电量和后台调度限制，不保证准点，也不承诺永久保活。
- Mihomo 内核随 APK 一起升级；GEO 数据可按已加载配置中的来源更新。
- 已有模拟器及部分真机验证记录，但长期后台、网络切换、厂商系统兼容性和完整线上覆盖升级仍需持续验证。各次验证的范围以对应记录为准。

[正式发行验证](docs/qa/distribution-0.2.45/README.md) · [应用更新验证](docs/qa/app-update/README.md) · [验证记录目录](docs/qa/README.md)

## 本地构建

构建脚本目前面向 macOS，输出 ARM64 APK。环境基线：

| 工具 | 版本 |
| --- | --- |
| JDK | 21 |
| Go | 1.25.6 |
| Android SDK / NDK | 36 / 29.0.14206865 |
| Gradle / Android Gradle Plugin | 8.14 / 8.12.1 |
| Kotlin | 2.2.20 |

在 `local.properties` 设置 `sdk.dir`，或设置 `ANDROID_HOME`；NDK 路径可通过 `ANDROID_NDK_HOME` 指定。首次构建需要下载 Gradle、Maven 和 Go 依赖。

### 本地开发测试包

```sh
./scripts/build.sh
```

默认生成启用 R8 和资源裁剪的紧凑包，使用本机 debug 证书签名，以便覆盖已有开发测试版：

```text
dist/Clash-Android-<version>-compact-local-arm64.apk
```

包名为 `cn.nascp.clash.dev`，版本带 `-dev`。只有需要可调试构建时，才使用 `./scripts/build-debug.sh`。

### 正式发行包

在仓库外准备私有签名配置，再执行：

```sh
export CLASH_RELEASE_SIGNING_FILE="$HOME/.config/clash-android/signing/signing.properties"
./scripts/build-distribution.sh
```

私有配置包含四项：

| 配置项 | 含义 |
| --- | --- |
| `storeFile` | 密钥库的绝对路径 |
| `storePassword` | 密钥库密码 |
| `keyAlias` | 签名密钥别名 |
| `keyPassword` | 签名密钥密码 |

正式构建使用 `cn.chenpingonline.clash` 和不带 `-dev` 的版本名，生成 `dist/Clash-Android-<version>-arm64.apk`、SHA-256 校验文件及本地混淆映射。

两种紧凑构建都会运行 release 构建、Lint 和单元测试，并将同名 `.mapping.txt` 保存到 `dist/`，供对应版本排查崩溃使用。请备份发行密钥与签名配置，不要将它们或真实密码提交到仓库、上传到 Release。自行构建者需使用自己的密钥；官方包的覆盖更新需沿用官方发行签名。

构建脚本依次准备共享源码、构建 JNI、打包 APK；直接运行 Gradle 不会自动重建 Go。构建、代码提交、推送与 Release 发布分别执行，脚本不会自动发布。

### 测试

```sh
# Kotlin 单元测试
./gradlew :app:testDebugUnitTest

# 已完成源码准备后，在 core 目录运行原生与共享包测试
cd core
go test -race ./... clashmanager/configyaml clashmanager/ruleimport
```

Android 仪器测试需要 adb 设备或模拟器；部分测试还依赖本地网络夹具和启动参数，详见 [验证记录](docs/qa/README.md) 及 [网络测试说明](docs/qa/0.2.1/README.md)。

## 内核与开源来源

- **Mihomo v1.19.30**：启用 `with_gvisor`，由 `VpnService` 提供 TUN 文件描述符；Android 适配可在 [prepare-core.py](scripts/prepare-core.py) 中核对。
- **Clash-Manager**：复用 `configyaml` 与 `ruleimport`，固定提交 `261050fe9b6c1d64ae387be5900228429b9816bd`；准备流程见 [prepare-shared.py](scripts/prepare-shared.py)，可通过 `CLASH_MANAGER_SOURCE` 指定本地仓库。
- Kotlin、AndroidX、OkHttp 及其他依赖分别在 Gradle、`core/go.mod` 和 `core/go.sum` 中声明。

本项目采用 [GPL-3.0](LICENSE)。第三方来源与许可说明见 [THIRD_PARTY.md](THIRD_PARTY.md)。
