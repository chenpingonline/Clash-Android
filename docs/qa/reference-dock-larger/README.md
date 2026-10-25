# Dock 与整页的整体尺度

- 用户明确指出 Dock 整体比上方页面小，而非 Dock 内部比例有问题。本轮纠正先前缩小图标的方向，整体放大底部导航。
- 图标 19dp→24dp，默认文字 10.5sp→12.5sp，行高 16sp、图文间距 3dp；触摸项 58dp，栏体默认高度 56dp→64dp，选中块约 52dp。横向边距 22dp→16dp，与页面卡片对齐；最大宽度 440dp。外圆角 20dp、内圆角 16dp 保留，避免随高度放大成圆形。
- 保留玻璃透色、分层轮廓、阴影、选中块双端弹簧。标签改为按当前宽度/语言/字号预先测量并缓存单行字号，避免长英文被截断；极窄约束下以省略号兜底。
- assembleDebug、assembleDebugAndroidTest、lintDebug 通过。DockGlassTest 三项（含 320dp 窄屏、英文、150% 字号）、DockMotionTest、MotionNavigationTest 共五项通过。新增压力检查最初使用 300dp，发现原自动字号方式截断长英文；最终在 320dp 验证完整单行显示，未声称任意更窄屏幕均可完整显示。
- Computer Use 刷新独立实时镜像，确认当前安装的放大版与首页卡片的尺度，并点击设置入口复查实际切换。home.png 为真实截图；comparison.png 左为上一版、右为本次放大版整页缩放对比；english-large.png 为英文150%字号测试截图。
- APK：Clash-Android-0.2.14-dev-dock-larger-preview-arm64.apk，0.2.14-dev / versionCode 16，本地 debug 签名。SHA-256 `5e5581c290b9466a0ee5cf612df17f5a1045d889f810ee20f255ae81b24e5bc0`。未在实体手机复查。

参考 Android 官方 [TextAutoSize](https://developer.android.google.cn/reference/kotlin/androidx/compose/foundation/text/TextAutoSize) 的单行溢出行为说明；实际截断及修正结论依据本地截图与测试。
