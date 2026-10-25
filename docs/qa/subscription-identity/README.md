# 每订阅 User-Agent 与 HTTP 错误诊断

2026-10-24，0.2.6-dev，本地 Android 16 / Pixel 9 Pro arm64 模拟器。

## 实现

- 链接导入及订阅信息页提供默认 `Clash.Meta`、`ClashForAndroid/2.5.12`、`ClashMetaForAndroid/2.11.36` 和自定义 User-Agent。
- 自定义值限制为1–512个可打印 ASCII 字符，拒绝空白、控制字符及换行注入。请求及每一跳使用同一值；手动更新及实际 SubscriptionWorker 使用配置保存的值。
- 字段随配置 AES-GCM 加密保存；修改原始 YAML、备份（含不含应用设置）、事务恢复和历史恢复保留字段，旧数据默认 `Clash.Meta`。
- HTTP 非成功响应包含状态码、跳转次数和固定类别摘要。至多读取4 KiB错误正文，只输出固定分类文本，并标注关键词识别、未经核实；原始正文、URL、查询参数、Cookie和任意反射内容不输出到诊断。网络异常也不回显底层URL。
- 当前订阅最近HTTP失败在订阅信息页显示，诊断ZIP包含全局最近HTTP失败及时间。成功下载清除当前地址失败记录，全局最近失败保留作为历史。

## 验证

- JDK 17：`:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest` 通过；SubscriptionHttpTest共6项通过。
- `:app:lintDebug` 通过。
- `ExternalServicesTest` 共5项、`SubscriptionUiTest` 共1项联合执行通过：UA跳转传递、加密存储、手动更新、真实后台Worker、备份与旧备份恢复、403跳转计数、错误正文凭据脱敏、成功清除当前失败记录，以及导入/编辑/重新打开/无效输入不覆盖原值。
- 界面截图：[自定义导入](import-custom.png)、[保存后重新打开CFA设置](profile-cfa.png)。自定义输入截图包含模拟器输入法工具栏；没有修改应用布局。
- 尝试通过Computer Use绑定独立模拟器窗口，但工具返回Invalid app；实际界面操作由Compose设备测试执行，截图已作视觉复核。
- APK通过apksigner verify及zipalign -c -P 16 4，更新本地dist产物与校验和。

没有使用真实服务商订阅验证403；没有改动Go内核、自动轮换UA或发布。
