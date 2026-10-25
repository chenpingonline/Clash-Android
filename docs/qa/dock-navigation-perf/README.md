# Dock 动效一致性与运行中代理切页

## 修正内容

1. Dock 原先用两端不同刚度弹簧，并叠加点击宽度脉冲；边界对第一/最后项的扩张进行钳制，造成中间项和两端视觉不一致。改为单个临界阻尼位置动画，形变仅随移动进度出现（最多约10%），落位时归零；移除点击额外宽度膨胀、纵向挤压与到位后回弹。玻璃和点击变浅保留。
2. 代理工具栏和列表原先各自解析同一份 JSON，页面重新进入时重新准备行/测速目标、展开默认组并构建初始可见卡片。现在共享后台目录快照；单/双列行、测试目标与叶节点去重在后台目录建立时准备。默认组在首帧即展开。
3. 使用 movableContentOf 保留当前代理页的 lazy viewport，在 Home 等页时仅对已就绪的运行中代理视口做屏外预备，进入时复用 composition、已测量节点和滚动位置；不构建全部卡片。隐藏内容清除语义并移出命中区域；配置/运行配置变化时重新建立该页面。

## 功能验证

- assembleDebug、assembleDebugAndroidTest、lintDebug 通过，最终标准包 applicationId 为 cn.nascp.clash.dev，versionName 0.2.15-dev / versionCode 17。
- 11 项设备测试通过：DockMotionTest 2（涵盖两端/中间、LTR/RTL、落位无二次拉伸与快速改点），MotionNavigationTest 1，ProxyNavigationTest 1（400节点滚动到末尾后 Home 往返仍保留位置，隐藏代理页不出现在首页语义树），ProxyLayoutTest 6，ProxyRuntimeTest 1（真实内核选择与测速）。结果 regression-tests.txt。
- Computer Use 通过独立实时镜像实际操作“运行中 Home→代理→Home→代理”，前后各观察同一400节点/5个命名策略组及 GLOBAL。截图 after/proxies.png。

## 性能采样

- 设备：独立只读 Clash_Plain_QA，emulator-5560，Android 16 / API36，arm64，debug，Skia OpenGL；宿主另有模拟器活动，绝对帧率噪声较大。
- 原版：本地 0.2.14-dev Dock-width APK，包名 cn.nascp.clash.dev。修复版：当前源码生成的 cn.nascp.clash.dockqa 独立 QA 包，避免并行 connected-test 的卸载流程中断采集；只通过临时 Gradle init script 更换 applicationId，未改变正式包配置。最终交付为标准包。
- 实际运行 Mihomo，400个命名 direct 测试节点，5个命名策略组加 GLOBAL；MockWebServer 仅提供本地测试端点，非真实订阅或代理线路。
- 第一组为人工 Computer Use 切页，各45秒 Perfetto。主线程 AndroidOwner:measureAndLayout 最长 107.09→46.40ms，Record View#draw() 116.04→54.94ms，Recomposer:recompose 62.37→35.33ms。详见 before/after/main-slices.csv 和 navigation.pftrace。手动点击节奏/trace覆盖不同，因此不将两份 trace 的事件总量当作帧率对比，也不把布局切片时长等同于某一个卡片函数耗时。
- 为降低单次样本波动，补充 before/after 各3轮 UiAutomator 自动复测：数据就绪后等待1500ms，每轮回 Home、等待900ms并 reset gfxinfo，然后 Proxy→Home→Proxy，每步等待900ms，采集独立 gfxinfo。测试入口 ProxyManualFixtureTest（manualFixture=true, benchmarkFlow=true, nodeCount=400），两侧固定操作一致。

|阶段|轮次|帧数|P50 ms|P90 ms|P95 ms|P99 ms|Jank %|
|---|---:|---:|---:|---:|---:|---:|---:|
|before|0|43|57|133|150|200|65.12|
|before|1|52|53|77|121|150|57.69|
|before|2|51|57|65|101|125|60.78|
|after|0|56|61|81|89|105|58.93|
|after|1|56|57|73|81|89|53.57|
|after|2|55|57|73|77|81|56.36|

- 三轮 P95 均降低，101–150ms→77–89ms；但 P50 未明显改善（53–57ms→57–61ms），不能宣称整体帧率已提升或卡顿完全消失。结果支持减少长帧和切页重建负担，仍需在用户实体手机和真实订阅上复查。
- 单次人工 gfxinfo 的中位数也有波动，已保留完整原始数据，不仅摘取改善项。三轮原始文件为 before/run-*.txt、after/run-*.txt，汇总 metrics.csv / metrics.json。

## 交付

Clash-Android-0.2.15-dev-navigation-fix-arm64.apk，本地 debug 签名，SHA-256 `f67c02bf42e6e33d3f2ee7112676454676718a8f1d78346a36a6a079f68b2d12`。未提交、推送或发布。

工具依据：[Perfetto Trace Processor](https://perfetto.dev/docs/analysis/trace-processor)。Perfetto/gfxinfo 是上述结论使用的实际采集工具；未运行 Simpleperf。
