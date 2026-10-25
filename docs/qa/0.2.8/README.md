# 0.2.8-dev 紧凑节点卡片

- 节点卡片改为左右布局：左侧名称/协议，右侧测速。最小高度 64dp，内边距上下各 4dp，节点行间距 6dp。测速按钮至少 48×48dp，名称保留最多两行。选中标记位于协议行，避免挤压名称。
- assembleDebug、assembleDebugAndroidTest、lintDebug 通过；ProxyLayoutTest 共 6 项通过，覆盖大字号单列、双列、多组展开、筛选、400 节点懒加载与测速取消。原始输出 instrumentation.txt。
- Computer Use 通过 Android 16 ARM64 模拟器实时镜像复查 400 节点夹具的卡片密度、选中状态、节点切换与滚动。截图 compact-nodes.png。没有连接实体手机，本记录不证明真机显示与交互。
- 安装包 dist/Clash-Android-0.2.8-dev-arm64.apk，versionCode 10，debug 签名。
