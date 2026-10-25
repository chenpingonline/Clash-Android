# 0.2.7-dev 节点列表滚动优化

2026-10-24。本轮定位的是源码中的刷新与渲染开销，未连接用户手机进行性能采样，不能确认其全部卡顿原因。

## 修改

- 将内核 JSON 转为只包含名称、协议、成员和当前选择的不可变显示快照，在 Dispatchers.Default 解析；忽略历史延迟和 alive 变化，避免每 1.5 秒刷新导致列表整体重建。
- 缓存筛选、排序、双列分行及测速目标集合，移出 LazyColumn 内容构建过程；节点行指定独立 contentType，允许相同布局复用。
- 测速使用 SnapshotStateMap 更新单个节点结果，避免每个结果复制整张 Map；只有按延迟排序时才订阅整个结果集合。

## 验证

- testDebugUnitTest、assembleDebug、assembleDebugAndroidTest、lintDebug 通过。
- ProxyLayoutTest 与 ProxyRuntimeTest 共 7 项通过，含 400 节点懒加载、末项滚动、历史数据不改变快照、当前选择/协议变化仍更新、双列/大字体单列、筛选、并发/取消、实际本地内核选择及测速。原始输出：scroll/instrumentation.txt。
- Computer Use 通过实时模拟器镜像操作代理页、连续滚动、滚动后选中节点。截图：scroll/after-selection.png。

## 滚动帧数据

Android 16 ARM64 模拟器 Clash_Feedback_QA_20261024，1170×2532，debug 构建；本地内核夹具，5 个策略组，每组 400 个命名 Direct 节点，另有 GLOBAL。不是实际订阅或远程代理性能。

修复前后各一次：进入代理页，初步滚动，再清空 gfxinfo，执行相同的 12 次 350ms 手势（6 次向下、6 次向上，手势间隔 200ms），内核轮询保持开启。

| 指标 | 修改前 | 修改后 |
| --- | --- | --- |
| 渲染帧数 | 415 | 416 |
| Janky frames | 12 / 2.89% | 10 / 2.40% |
| P95 | 21ms | 17ms |
| P99 | 32ms | 18ms |
| Slow UI thread | 3 | 0 |

原始数据：scroll/before-gfxinfo.txt、scroll/after-gfxinfo.txt。这是单轮模拟器对比，存在宿主负载、JIT、GPU 等噪声，不能据此推算真机提升比例或承诺消除全部卡顿；未采集 Perfetto/CPU 栈，也不声称这些源码隐患已获 CPU 采样归因。安装 0.2.7-dev 后需用用户的节点规模、手机与测速状态复查。

## 安装包

- dist/Clash-Android-0.2.7-dev-arm64.apk
- applicationId cn.nascp.clash.dev，versionCode 9，debug 签名。
- SHA-256: 0fb1347bb4751b24af7ad9ac71c29bb0c3c9e501dfc29130f0451594e37c5101
