# 0.2.28-dev 真机回归 — 2026-10-25

设备：Xiaomi 2410DPN6CC / haotian，Android 17，1440×3200。仅操作 USB 真机，未使用同时在线的模拟器代替真机结果。

安装：原 0.2.26-dev/versionCode 28，使用 `adb install -r` 升级为 0.2.28-dev/versionCode 30，保留数据。APK：`dist/Clash-Android-0.2.28-dev-arm64.apk`，SHA-256 `f8ebca23ce773ab72991dd40a544a20babb5721152e735c3da7a0bbb3fbeef46`。

通过 adb UI 树定位点击、截图、录屏及日志完成回归；FFmpeg 对真机录屏抽帧观察。未修改产品代码、订阅内容或执行发布。

| 范围 | 实际结果 |
| --- | --- |
| 覆盖安装 | 成功，原配置和累计流量保留 |
| Dock 五入口 | 15 次连续长短距离切换完成，最终页面与选中入口一致；五个主页面的入口坐标完全一致 |
| Dock 运动画面 | 抽帧可见移动拉伸、收拢和背景透色/模糊，文字图标保持清晰；未观察到黑块或导航丢失。此结果不构成对每一帧折射算法或旧 Android 降级路径的验证 |
| 离线代理 | 未启动 VPN 时展示分组与节点；AI 分组由 Proxies 改选 Direct 后预选文字与勾选更新，再恢复 Proxies；折叠后后续分组可见 |
| 配置入口 | 全局覆写和全局扩展脚本分别展示；进入全局覆写显示“作用于所有配置”及 YAML 编辑/校验保存/预览入口。未保存更改 |
| 简化订阅导入 | 默认只展示链接、可选名称、地址、导入和高级选项；展开后显示默认/自定义 UA，默认值 clash-verge/v2.4.3 和手动诊断。未新增或更新真实订阅 |
| VPN 与活动 | 启动和断开成功，内核显示 RUNNING；活动页展示 24 条连接及实际收发计数。不能据此推断所有代理节点或外网目标可达 |
| 日志等级 | 运行时 info→warning→info，显示值随操作更新；停止后显示“未运行”和“启动 VPN 后可调整” |
| 流量 | 速率出现非零值，累计下载约 6.5→7.2 MB，上传约 124.9→125.0 MB；正常停止再强制结束应用并重启后累计值保留 |
| 出口信息 | 原规则模式最终显示“获取失败”；直连模式成功展示 IP、国家/地区、位置、时区。切回规则立即清除旧信息并查询，断开后清空为未连接。规则模式成功查询未通过，根因未定位 |
| 主题 | 深色即时生效，首页及 Dock 可读；显式浅色切换后恢复原“跟随系统” |
| 崩溃 | 测试时段 crash buffer 未发现该应用崩溃；旧 buffer 中其他应用/系统历史记录不计为本次失败 |

## 待确认

- 规则模式出口查询失败需区分当前节点/路由/服务可达性和应用查询逻辑，不能声称全部功能通过。
- 应用进程日志出现 AdrenoVK `Shader compilation failed` / `Pipeline create failed`，随后也有管线命中记录。实际截图与动画仍有渲染；当前证据不足以认定新 Dock 折射失效，也不足以排除 GPU 兼容性问题。
- 本次未覆盖新增订阅真实下载、增强保存与未保存内容预览、自动组失效回退、手机重启、长期后台、其他系统版本、精确折射像素比较。

## 收尾与证据

已恢复规则模式、AI 分组的原 Proxies 选择、info 日志等级及跟随系统主题，VPN 恢复测试前的未连接状态，停留首页。累计流量包含测试产生的流量。

原始 UI 树、截图、录屏、日志位于本地 `.cache/qa-device-0.2.28/`，其中可能含设备上的订阅名称、节点和网络信息，不对外发布。

主要证据：`start.png`、`proxy-selected.xml`、`proxy-restored.xml`、`proxy-collapsed.xml`、`global-override.xml`、`import-advanced.xml`、`log-warning.xml`、`log-restored.xml`、`core-stopped.xml`、`home-still.xml`（规则查询失败）、`direct-result.xml`（直连查询成功）、`rule-restored.xml`、`stopped.xml`、`restarted.xml`、`dark-home.png`、`dock.mp4`、`dock-motion.png`、`app-logcat.txt`、`crash-session.txt`。
