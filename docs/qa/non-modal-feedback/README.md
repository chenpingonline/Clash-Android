# 非模态操作反馈与页面内确认

2026-10-24，0.2.6-dev，Android 16 arm64独立模拟器Clash_Feedback_QA_20261024。

## 变化

- 应用内AlertDialog全部替换：普通操作反馈显示为顶部提示条，5秒收起；错误保留到关闭。提示不拦截页面操作或导航，长错误可在条内滚动。
- 明确区分普通反馈和错误。新操作开始时清除旧提示；递增消息编号与条件清除防止旧计时器误删新消息。初始化、VPN授权失败、启动失败及页面读取错误使用错误类型。
- 删除配置、历史恢复、备份恢复、恢复原始项、批量运行规则及筛选连接关闭在页面内二次确认，保留取消步骤；卡片出现后滚动至可见范围，忙碌时禁用卡片动作。危险操作使用主题的error/onError颜色。
- 开源许可在当前页面展开，有独立滚动区域和关闭按钮。
- VPN启动不附带请求POST_NOTIFICATIONS；可在自动化页的通知设置中主动开启。Android首次VPN授权、系统设置和文件选择流程保留。

## 验证

- JDK17编译及lintDebug通过；11项单元测试通过。
- 8项设备UI回归全部通过：FeedbackUiTest（3）、SubscriptionUiTest（2）、EditorInteractionTest、DocumentFlowTest和VpnConsentTest。
- 验证普通反馈自动收起、错误持续显示、旧消息清除不影响新错误、提示期间仍能导航、浅深色删除确认可取消及明确确认后才删除、许可展开无模态窗口、拒绝通知权限仍能启动VPN且无自动通知授权窗。
- 编辑回归允许错误提示保持显示时直接修正并重新保存；备份导出/文件选择/原位恢复和首次VPN拒绝后重新授权均通过。
- 最终按钮颜色及忙碌状态调整后，重新执行3项FeedbackUiTest，通过。通知权限测试须在运行前准备为未授权状态，避免Android在撤销既有权限时终止测试进程。
- [完整8项记录](instrumentation.txt) · [最终专项记录](instrumentation-final.txt)。
- Computer Use无法绑定独立模拟器窗口，返回Invalid app；实际交互由Compose设备测试及UiAutomator执行，截图已作视觉复核。
- 原模拟器空间不足，使用独立12GiB数据分区验证，没有删除原模拟器中的其他应用或数据。
- APK通过apksigner verify和zipalign -c -P 16 4；dist产物及SHA-256已更新。未提交、推送或发布。

## 截图

- [普通反馈，可继续导航](info-light.png)
- [错误反馈，不要求点击知道了](error-light.png)
- [页面内删除确认：浅色](delete-light.png)
- [页面内删除确认：深色](delete-dark.png)
- [未授权通知时直接运行VPN](vpn-without-notification-prompt.png)
