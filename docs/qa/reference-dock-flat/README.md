# Dock 扁平比例

- 按用户“参考偏扁、当前偏圆”的反馈调整几何比例：栏体默认视觉高度 60dp→52dp；外圆角从自动半圆改为 22dp；选中块默认高度 52dp→40dp，使用 18dp 圆角。左右留白 24dp→20dp，增强横向舒展感。
- 触摸项最小高度保留 48dp。图标 21dp→20dp，图标容器 24dp→22dp，图文间距 2dp→1dp，文字行高 14sp→12sp；大字号可自然增高。
- 背景亮边圆角、局部放大背景的裁切和采样偏移同步调整，避免外沿或放大背景与扁平形状错位。保留前一版玻璃层次、阴影与双端弹簧动画。
- assembleDebug、assembleDebugAndroidTest、lintDebug 通过。独立 API 36 模拟器上的 DockGlassTest 两项、DockMotionTest、MotionNavigationTest 共四项通过，包含 48dp 最小触摸区、背景换色与模糊、白底/浅灰底分层、伸缩及导航返回。
- Computer Use 在独立实时镜像中检查首页的新比例、实际点击设置入口并确认选中块落位。settings.png 为实际截图；comparison.png 上半为此前较圆版本，下半为本次扁平版本，使用相同屏幕区域裁切。
- APK：Clash-Android-0.2.14-dev-dock-flat-preview-arm64.apk，versionName 0.2.14-dev / versionCode 16，本地 debug 签名。SHA-256 `76767c3e49aa0a97676f47e962a5103d306ca14b18fe7a2d517b1a740be7021e`。未在实体手机复查。
