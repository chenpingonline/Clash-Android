# Dock 动画逐帧修正

- 重新逐帧查看用户提供的 Screenrecorder-2026-10-24-18-52-53-979.mp4。前一版仅等宽平移，遗漏选中块移动时的横向拉长/回缩，以及目标图标、文字短暂变浅的反馈。
- 选中块前后端改用不同刚度的弹簧，产生拉长后收拢；远距离切换限制最大拉伸，打断动画保留当前速度；增加细亮边与轻微纵向收缩。按压及切换初期图标/文字变浅，关闭默认圆形涟漪。
- 已用独立只读 Clash_Plain_QA 模拟器 emulator-5560 运行 DockMotionTest 和 MotionNavigationTest：2 项通过。覆盖相邻/反向的宽度增加与恢复、快速转向及页面导航/返回。assembleDebug、assembleDebugAndroidTest、lintDebug 通过。
- Computer Use 通过独立实时镜像实际点击首页→代理→首页，核对正反切换后的目标状态。dock-motion.mp4 / dock-motion.gif 是实际录屏裁切的 Dock 正反切换，非模拟渲染；stretch-forward.png、stretch-reverse.png 为测试冻结时钟的 80ms 中间帧。
- APK：Clash-Android-0.2.13-dev-dock-elastic-preview-arm64.apk，本地 debug 签名；SHA-256 `aff2dfa098295225a74c6cb5e8864c4f7f9706f1ac4b77603c39d9bae4a4f69f`。与系统参考的底层玻璃折射/模糊并非完全相同，实体手机帧率未验证。
