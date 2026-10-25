# Dock 对齐页面内容宽度

- 用户要求 Dock 再宽一点，与上方卡片等宽。代理/配置/活动页内容边距为 12dp，Dock 原边距 16dp，且存在 440dp 最大宽度限制。
- Dock 跟随当前页内容边距：代理/配置/活动页使用 12dp，首页/设置页使用 16dp；移除额外最大宽度限制。现有高度、图文尺寸、玻璃和切换动画保持。
- assembleDebug、assembleDebugAndroidTest、lintDebug 通过。DockMotionTest 通过；因为不同页面的 Dock 宽度随内容边距变化，视觉动画断言改为按截图宽度归一化比较位置与伸缩宽度。
- Computer Use 刷新独立模拟器实时镜像，检查首页边缘并点击代理入口复查对应页面的 Dock 宽度。当前模拟器没有用户订阅，列表为空，未宣称验证了用户手机中的节点列表。
- APK：Clash-Android-0.2.14-dev-dock-width-preview-arm64.apk，0.2.14-dev / versionCode 16，本地 debug 签名，SHA-256 `0bb8527fd359f7d1391ae941db0d7e2704e5cb95de84f0b2994625ddcd255588`。实体手机尚未复查。
