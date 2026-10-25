# 0.2.9-dev 素白子页面优化

## 范围

- Secondary 包装层为素白子页设置独立的 CompositionLocal，主页/其他主题维持原布局。共用 Panel 圆角 16dp、内边距 12dp、控件间隔 6dp；子页卡片间距 10dp。
- 去除素白子页开关的独立描边，压缩提示、复选行和入口的留白；按钮和开关行保留至少 48dp 触摸高度。输入框保留 Material 默认高度，随文字换行增长。
- VPN 状态使用用户可读文案，与启动/停止按钮并排；MTU、路由开关、排除网段归入一个参数卡片。外观页将语言、缩放、系统字体入口归组；内核测速地址和超时归入同一卡片。

## 验证

- assembleDebug、assembleDebugAndroidTest、lintDebug 通过。
- PageCoverageTest#secondaryPagesRenderInBothThemes 使用 plainOnly=true，遍历 32 个子页，分别中文 100% 与英文 150%，共 64 次页面渲染/截图，验证返回导航存在。此遍历不证明所有业务操作均执行。
- EditorInteractionTest 验证真实控件编辑/保存、无效输入不覆盖，以及 VPN 开关/参数保存。PlainThemeTest 2 项验证主题切换、重建、备份恢复及主页/代理基本流程。以上共 4 项测试通过，输出见 instrumentation.txt。
- 抽查 VPN、网络端口、DNS、外观、内核、备份、后台更新、订阅信息的中英文截图，见 overview-zh.png、overview-en-large.png 及 screens/。
- Computer Use 在 Android 16 ARM64 模拟器实时镜像中，先观察旧 VPN 页，再复查新版 VPN 卡片、开关状态变化、返回、外观分组。截图 manual-vpn.png、manual-appearance.png。未连接实体手机。

## 安装包

- Clash-Android-0.2.9-dev-arm64.apk
- versionName 0.2.9-dev，versionCode 11，cn.nascp.clash.dev，本地 debug 签名。
- 大小 88423368 字节。
- SHA-256 `bdecf97e0402dea1e050a0ca56e4dbeb1a45bd805d50d428b2b28d8fc87089e9`。
