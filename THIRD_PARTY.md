# 第三方来源

- Mihomo v1.19.30，https://github.com/MetaCubeX/mihomo ，GPL-3.0；原始许可保留在 licenses/Mihomo-GPL-3.0.txt。Android 接入改动在 scripts/prepare-core.py 中可审查和复现：VpnService 文件描述符适配与新建 TUN 流的系统 UID 查询回调。
- Clash-Manager configyaml 与 ruleimport，https://github.com/chenpingonline/Clash-Manager ，固定提交 261050fe9b6c1d64ae387be5900228429b9816bd，GPL-3.0；原始许可证在 LICENSE。完整实际远程地址以 scripts/prepare-shared.py 为准。
  - configyaml 原样导出；ruleimport 仅将原模块 internal/configyaml 导入路径改为本地独立模块 clashmanager/configyaml，适配代码位于 scripts/prepare-shared.py。
- 出口 IP 检测参考 Clash Verge Rev 的六服务配置、字段映射、单服务超时/重试及首页 5 分钟刷新机制；Android 网络请求与 Compose 界面独立实现。参考源码：https://github.com/clash-verge-rev/clash-verge-rev/blob/dev/src/services/api.ts 、https://github.com/clash-verge-rev/clash-verge-rev/blob/dev/src/components/home/ip-info-card.tsx 。
- AndroidX WorkManager 2.10.1（Apache-2.0）、OkHttp 4.12.0（Apache-2.0）分别用于持久任务与远程 WebSocket；版本在 app/build.gradle.kts 固定。
- 应用图标源自现有 Clash-Manager web/public/icon-current-256.png，未修改。
- Jetpack / Kotlin / Coroutines 等依赖由 Gradle 声明，Go 依赖完整锁定在 core/go.mod 与 core/go.sum。发布前还需生成完整传递依赖许可清单及可复现源代码包。

当前 APK 仅为本地开发验证产物，尚未发布。
