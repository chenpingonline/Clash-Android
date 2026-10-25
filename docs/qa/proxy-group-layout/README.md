# 代理组紧凑布局验证 — 2026-10-24

用户截图中的按钮直接作为 Page 纵向容器的子项，每个按钮独占一行且附带整页间距，导致节点列表被挤至屏幕下方。改为标题与 FlowRow 组成的统一区域，水平间隔8dp，保留控件触摸高度，按可用宽度自动换行。

Android 16 arm64 独立模拟器上运行 ProxyLayoutTest，2项通过（见 instrumentation.txt）。测试直接渲染生产 ProxyList 组件，使用无凭据的本地 AppState 数据，不启动VPN或访问订阅：

- 9个代理组：AI和Apple处于同一行，节点区域首屏可见；点击GitHub，选中状态及对应节点一起变化。
- 深色主题、1.5倍字号及长中文组名：所有名称均在视口内，长文本换行，切换后的节点仍可滚动访问。

截图为组件测试宿主，不包含应用主导航栏：[普通字号](compact.png)、[大字号与长名称](large-font.png)。已人工检查截图。Computer Use 无法绑定此Android模拟器（Invalid app），设备交互由Compose instrumentation执行。真实手机截图对应环境尚未复测。

assembleDebug、assembleDebugAndroidTest、testDebugUnitTest和lintDebug通过；APK签名及16KiB zip对齐检查通过。未修改下载或内核逻辑。
