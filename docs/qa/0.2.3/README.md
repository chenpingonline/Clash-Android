# 0.2.3-dev Dock 优化

2026-10-24，Android 16 arm64 模拟器。底部导航替换为独立圆角 Dock：16dp 外边距、24dp 圆角、轻阴影/描边、图标胶囊选中状态；保留五个文字入口和 Tab 语义。Scaffold 为内容预留完整底栏高度，处理系统导航安全区；宽屏居中并限制最大520dp。

使用 adb/UIAutomator 根据实际 UI 树操作五个入口、外观设置和字号滑块，检查选中状态，截图查看实际渲染。Computer Use 可用应用中没有模拟器入口，使用设备工具完成验证。未调用语义判断工具。

- [入口切换记录](navigation.log)：五个入口均选中并切换页面。
- [浅色中文](dock-light.png)、[深色英文大字号](dock-dark-large.png)。
- [窄屏320dp、大字号英文](dock-narrow-large.png)：发现初版英文拆字换行，修复为标签单行自动适配后复查通过。
- [宽屏](dock-wide.png)：Dock 保持居中限宽。
- [构建与Lint](build.log)、[APK签名](signature.log)：通过；16KiB zipalign检查通过。

测试后恢复原屏幕尺寸、中文、100%字号和浅色主题。仅调整UI，保留0.2.2的VPN保护代码和原生库，本轮没有重复VPN功能验收。真机显示与系统三键导航仍可安装后检查。

APK：dist/Clash-Android-0.2.3-dev-arm64.apk，versionCode 5；本地debug签名，未发布。
