# 0.2.13-dev Dock 与主页面切换

- 参考用户提供的 2026-10-24 文件管理器录屏，保留五个 Clash 入口。Dock 改为悬浮胶囊，52dp 最小触摸高度，选中块覆盖图标与文字；素白主题使用中性灰选中块与实心图标，其他主题使用原有配色。
- 选中块以 320ms 缓动平移，取消图标放大。主页面内容沿入口顺序以 360ms 整页平移，不再交叉淡入淡出；处理 RTL 方向并裁切页面边界。子页进入/返回保持原有动效。
- assembleDebug、assembleDebugAndroidTest、lintDebug 通过。DockMotionTest、MotionNavigationTest、PlainThemeTest 共四项测试通过，包括中途转向、96ms 页面位移、连续切换、子页返回、主题恢复及英文 150% 字号。
- 首轮导航测试因仍使用旧的“VPN 与分应用”名称失败，已明确设置素白主题并更新为“VPN 设置”，随后完整四项通过。结果见 validation.txt、test-results.xml。
- 本地 debug APK：Clash-Android-0.2.13-dev-arm64.apk，versionCode 15，SHA-256 `05bd41c67494f185ace2c34ed8acf262eee9ba41c728f91daf351917aab76430`。未在实体手机验证。

- Computer Use 在实时镜像中确认首页→设置的选中项一致、VPN 子页 Dock 收起。返回操作后另一组设备测试开始，未将后续页面误作返回验证；返回一致性由 MotionNavigationTest 覆盖。截图 home.png / preview.png 为实际新版首页。
