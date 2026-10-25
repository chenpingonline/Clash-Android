# 按用户圈注参考统一 Dock 比例

- 用户给出裁切后的具体参考图，希望扁长外壳与足够大的图文并存；红圈仅用于标注，不加入产品。
- 本次默认栏体 54dp，图标 22dp，标签最大 13sp / 行高 16sp，图文间距 2dp；条目至少 48dp 触摸高度。外层 27dp 圆角、选中背景 23dp 圆角，形成横向较宽的胶囊；选中背景约 46dp 高，紧贴图文而不缩小标签。
- 保留 16dp 页面边距、440dp 大屏宽度上限、玻璃背景、轮廓层次和双端弹簧伸缩。文字按可用宽度测量并缓存字号，支持大字号下的单行标签。
- assembleDebug、assembleDebugAndroidTest、lintDebug 通过。DockGlassTest 三项（含 320dp、英文150%字号）、DockMotionTest、MotionNavigationTest 共五项通过。
- Computer Use 刷新独立模拟器镜像，查看完整首页与 Dock 的比例，实际点击代理入口验证切换。home.png 为真实整页截图，dock.png 为该截图中 Dock 的等比裁切，不含红色标注。
- APK：Clash-Android-0.2.14-dev-dock-reference-preview-arm64.apk，0.2.14-dev / versionCode 16，本地 debug 签名。SHA-256 `e40e446c4c7bd8a4e9457618fdf1b8f9feb9c628b8f5b395cf30ede69cd91348`。未在实体手机复查。
