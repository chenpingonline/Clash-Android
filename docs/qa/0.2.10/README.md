# 0.2.10-dev 素白配置与活动页

## 修改

- 素白主题使用蓝色 primary，主操作 MainButton 使用浅蓝 FilledTonalButton；直接 Button 使用蓝底白字，危险操作保留红色。此配色也作用于素白首页和子页面。
- 配置页使用单层 LazyColumn 与紧凑卡片，来源、日期、运行/选择状态和操作归组；大字号将日期/操作分行。VPN 运行期间显示停止后切换的提示，选中配置仍可进入管理。
- 活动页使用单层 LazyColumn，去除 Page+嵌套列表；整合搜索、计数、关闭连接、日志暂停/继续和导出。连接显示数据在 Default 后台解析，按 ID 复用行。
- 搜索无匹配与完全空列表分别提示；暂停日志保留完整快照，显示最近 100 条匹配日志，导出仍使用完整快照/当前日志。关闭连接确认记录打开时的筛选 ID，新连接不加入该次操作。
- 共用紧凑样式改名 LocalPlainCompact，由素白子页、配置和活动页显式提供。其余主题继续走原页面实现。

## 验证

Android 16 ARM64 模拟器 Clash_Feedback_QA_20261024，1170×2532，debug 构建；未连接实体手机。

- testDebugUnitTest 13 项通过；assembleDebug、assembleDebugAndroidTest、lintDebug 通过。
- PlainTabsTest 3 项：英文 150% 字号下选择/管理配置和添加入口；模拟连接的搜索、无匹配、详情导航、关闭确认取消与清除搜索；日志暂停快照/继续及无匹配。连接测试使用构造的 UI 状态，未实际关闭远程连接。
- PlainThemeTest 2 项与 FeedbackUiTest#inlineDeleteCanBeCancelledAndOnlyDeletesAfterConfirmation 通过，覆盖主题切换/重建/恢复、主页与代理基本流程、删除确认取消与提交。以上共 6 项界面回归通过，原始输出 instrumentation.txt。
- Computer Use 在修改前观察旧配置页/活动连接页，修改后通过实时镜像验证 4 个配置的显示、当前配置管理/返回、活动连接空状态、日志切换与暂停。截图 profiles.png、activity-connections.png、activity-logs-paused.png、overview.png。
- 本次未通过系统文件选择器完成日志导出文件保存，也未作真机性能/公网节点测试。

## 安装包

- Clash-Android-0.2.10-dev-arm64.apk，versionCode 12，cn.nascp.clash.dev，本地 debug 签名。
- 88635516 字节。
- SHA-256 `86880cde65abeff7e57a7988c80e1982ba53d7103afe08ce6b4c5ca3bc9ce5e8`。
