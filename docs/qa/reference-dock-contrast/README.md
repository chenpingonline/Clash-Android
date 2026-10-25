# Dock 与页面背景分层

- 用户提供参考文件管理器和本应用的手机截图，指出 Dock 与白色列表背景差异不足。此次保留实时模糊、透色和双端弹簧，增强可辨识的玻璃底色、选中项明暗差、外沿亮边/灰色下沿与柔和投影。
- 底部选中块从主题色容器向前景色混合 11%，适配当前统一的深浅配色，避免固定成只适用于浅色的颜色。光照轮廓采用上亮下暗的渐变，不使用整圈厚黑边。
- 增加白底/浅灰底截图检查：Dock 胶囊与页面的亮度差、选中块与胶囊的亮度差均达到了测试阈值，图片 on-white.png / on-grey.png。已有实时模糊、背景更新、前景清晰、伸缩与导航回归保留。
- assembleDebug、assembleDebugAndroidTest、lintDebug 通过；独立 emulator-5560 上 DockGlassTest 两项、DockMotionTest、MotionNavigationTest 共四项通过，见 instrumentation.txt。中途界面主题合并改变了首页文字，导航测试改用当前存在的“当前配置”验证首页返回；动画测试按实际选中区亮度采样。
- Computer Use 实际打开设置列表，检查白卡片延伸到 Dock 下方时的轮廓、阴影和选中项，settings.png 为真实截图。comparison.png 上半为上一版、下半为此次同位置截取，未经改色。
- 当前 APK：Clash-Android-0.2.14-dev-dock-contrast-preview-arm64.apk，versionName 0.2.14-dev / versionCode 16，本地 debug 签名。SHA-256 `4c279a1b9728bd400c1f068a7fb57ad389b4d1954802a6067cff79fa70fd148a`。当前工作目录同时包含统一深浅主题调整，此包基于当前源码构建；未在实体手机复查。
