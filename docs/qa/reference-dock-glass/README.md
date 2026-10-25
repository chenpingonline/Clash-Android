# Dock 实时玻璃背景

- 用户要求继续优化参考录屏中的玻璃质感。在原有双端弹簧动画上，加入实时页内容采样、局部 GPU 模糊、半透明白色遮罩、渐变亮边和低强度阴影；选中胶囊以 1.06 倍采样已模糊背景，前景文字/图标单独绘制保持清晰。
- 使用 Compose GraphicsLayer 记录页内容，不含 Dock，避免递归采样；仅将 Dock 附近带余量的窄条离屏模糊，不使用 CPU 屏幕截图轮询。源内容更新可直接刷新背景。
- Android 12+、目前支持内容延伸至 Dock 下方的素白/珍珠主题启用实时模糊；低版本或独立无背景源组件使用半透明/渐变降级。旧 Android 未实机验证。
- assembleDebug、assembleDebugAndroidTest、lintDebug 通过。独立 emulator-5560（API 36）的 DockGlassTest、DockMotionTest、MotionNavigationTest 共三项通过。测试证明背景透色、条纹边缘被模糊、换色后实时更新、前景清晰像素保留，并回归拉伸/回缩和快速导航/返回。
- DockMotionTest 改为识别图标/标签间隙中最长的浅灰选中区域，适配半透明渐变；初次阈值覆盖到了外胶囊抗锯齿角，已修正。最终输出 instrumentation.txt。
- Computer Use 实际点击设置并上下滚动列表，观察到下方蓝色图标形成柔和色晕、滚走后消失，Dock 自身图标文字清晰。settings.png / settings-before-scroll.png 为真实页面，glass-scroll.mp4 / gif 为真实屏幕录制裁切（包含静止间隔）。glass-green.png / glass-magenta.png 为条纹背景换色测试截图。
- APK：Clash-Android-0.2.13-dev-dock-glass-preview-arm64.apk，本地 debug 签名，SHA-256 `8bdf0f74e0afcf5cecbea9b6507ed63287e73212512417795fd9f641b7f70039`。尚未在实体手机确认性能或与系统玻璃逐像素一致。

实现依据：Android 官方 [GraphicsLayer](https://developer.android.com/reference/kotlin/androidx/compose/ui/graphics/layer/GraphicsLayer) 与 [Graphics modifiers](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers) 文档。
