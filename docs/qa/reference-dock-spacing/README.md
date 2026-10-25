# Dock 高度与离底间距

- 按用户要求把 Dock 稍微抬高，同时略增自身高度。
- 系统导航栏 inset 之外的底部留白从 10dp→18dp，上方外边距维持10dp；内侧垂直留白从3dp→5dp，使默认栏体高度54dp→58dp。图标、文字、触摸项、宽度和位置动画未改。
- 内边距与玻璃放大采样偏移共用常量，自动保持背景对齐；列表尾部避让继续使用 Scaffold 实测底栏高度。
- assembleDebug、assembleDebugAndroidTest、lintDebug 通过。DockGlassTest 三项通过，覆盖玻璃实时更新/模糊、白底/浅灰底分层、320dp窄屏英文150%字号。
- Computer Use 刷新独立模拟器，检查首页 Dock 与系统手势条间距并点击设置入口。home.png 为真实截图，dock.png 包含底部手势条，便于检查离底距离。
- APK：Clash-Android-0.2.15-dev-dock-spacing-preview-arm64.apk，0.2.15-dev / versionCode17，本地 debug 签名，SHA-256 `367a4f1e75b88a231bfd77f935e4bc09558344c4dcb0e2a3247225391ae4c893`。未在实体手机复查。
