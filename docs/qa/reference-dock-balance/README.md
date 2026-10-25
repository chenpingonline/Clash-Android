# 扁平 Dock 的尺寸平衡

- 按用户整页截图反馈，重新平衡扁平栏体、图标、文字和留白：栏体 52dp→56dp，选中块默认高度 40dp→44dp；外/内圆角 22/18dp→20/16dp，保持偏扁的形状。
- 图标 20dp→19dp，素白文字上限 10sp→10.5sp，行高 12sp→14sp，图文间距 1dp→2dp，统一 Medium 字重；触摸项最小高度 50dp。横向留白 20dp→22dp，大屏最大宽度 420dp→400dp。
- Row 内边距与放大背景采样共享常量，避免改变几何比例后背景位置偏移。玻璃透色、分层轮廓、阴影和双端弹簧保留。
- assembleDebug、assembleDebugAndroidTest、lintDebug 通过。DockGlassTest 两项、DockMotionTest、MotionNavigationTest 共四项在独立 emulator-5560 上通过。
- Computer Use 实际查看首页整页比例、点击代理入口检查落位，并返回首页；home.png / proxies.png 为真实截图。comparison.png 左为此前扁平版，右为本次调整，整页缩放后并排呈现。
- APK：Clash-Android-0.2.14-dev-dock-balanced-preview-arm64.apk，0.2.14-dev，versionCode 16，本地 debug 签名。SHA-256 `7f6d4782f5cfd1e3b15d3da862dfde7f1c2085ed0b7b59b3fe575afce544d88b`。未在实体手机复查。
