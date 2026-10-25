# 流光白 Dock 悬浮修复

2026-10-24。仅流光白一级页面改变底部布局；其他主题与二级页面仍保留原有安全区。

- Scaffold 的底部 padding 不再裁小流光白一级页面视口，改成 Page 滚动内容内部的尾部间距。间距取实际底栏测量高度，含系统导航区。
- assembleDebug、lintDebug 通过，见 build.log。
- Android 16 arm64 模拟器保留数据重装成功；adb 界面树定位滚动操作。home.png 确认原截断横带消失、内容可延伸到 Dock 后方；home-bottom.png 确认末尾提示和按钮能完整滚至 Dock 上方。
- 未验证真机帧率或不同系统导航模式。
