# Dock 栏体切页伸缩修正

- 用户再次报告：首页切到中间任意项时，整条 Dock 左右伸缩；切到首尾项没有。根因是 pageInset 按 route 在首页/设置的16dp与代理/配置/活动的12dp之间切换，导致栏体与所有入口同时移动。上次仅修正选中胶囊，未覆盖此栏体变化。
- 本次所有页面固定12dp边距，保留此前代理页加宽的尺寸。栏体、文字、图标的坐标不再随页面改变；选中胶囊继续移动。上一轮的高度和底部间距保留。
- 新增 wholeBarAndAllTabCellsKeepTheirBoundsAcrossEveryTabSwitch：读取实际渲染边界，覆盖五个入口正反切换，在16/80/320/720ms检查栏体与全部入口坐标完全不变。
- 同一新增用例在用户拿到的旧标准0.2.15 APK上明确失败：Home边界 left=16dp/right=374dp，切Proxy后 left=12dp/right=378dp，栏体总宽度增加8dp。完整输出 old-package-regression.txt。
- 修复0.2.16 APK上 DockMotionTest 3项、DockGlassTest 3项、MotionNavigationTest 1项，共7项通过，见regression-tests.txt。构建、lint、v2签名、zipalign 16KiB检查、ZIP完整性通过。
- Computer Use 复查最终版本的首页→代理，截图用于核对相同栏体位置。实体手机尚未复查。
- APK：Clash-Android-0.2.16-dev-arm64.apk，versionName0.2.16-dev / versionCode18，arm64核心，本地debug签名，SHA-256 `77b510b0c4d48efbd759726d494be6cdc4911545c8a2b3942cc23ea946ddc81f`。未提交/推送/发布。
