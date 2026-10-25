# 系统浅色与深色外观验证 — 2026-10-24

- 版本：0.2.14-dev（versionCode 16），arm64 调试包。
- 仅保留跟随系统、浅色和深色；新安装跟随系统。旧版 plain/sage/pearl 偏好和备份迁移为 light。
- 浅色沿用素白布局；深色背景 #000000、卡片 #232323，统一主页面、子页与 Dock。
- 本地 assembleDebug、14 项单元测试、lintDebug 通过。
- 独立 Android 16 arm64 模拟器 emulator-5562：5 项仪器测试通过，覆盖设置持久化/重建、备份恢复、旧主题迁移、VPN 启停与代理选择、大字号英文，以及深色五个主页面和系统夜间模式自动切换。
- Computer Use 通过仅绑定本机的 UI 树/截图桥接操作外观切换和设置入口，复查实际深色卡片、图标、Dock，以及三个外观选项。
- 首次扩展测试被共享模拟器上的其他仪器测试打断，最终验证改用独立模拟器。
- 截图：dark-settings.png、dark-appearance.png、system-light.png。
- 未验证真机；未发布。
