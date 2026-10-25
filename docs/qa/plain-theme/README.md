# 素白主题验证 · 2026-10-24

- 新建专用 Clash_Plain_QA Android 16 arm64 模拟器（1170×2532、480dpi，390×844dp）。原测试模拟器空间不足，未清理其用户数据。
- 构建、lint 通过；11 项单元测试通过。主题选择/持久化/Activity 重建/备份恢复与旧主题回切测试通过；运行操作覆盖 VPN 启停、实际策略组节点选择、搜索筛选和二级页面导航。PlainThemeTest 两项、PearlThemeTest 一项，共 3 项通过。
- 英文与 1.5 倍字体下列表内容可换行、可滚动，底栏保留全部五个入口。截图发现英文连接状态单词内换行后，已将大字号连接按钮下移，新增单行布局断言；复验PlainThemeTest两项与lint全部通过，见responsive-validation.log。
- 使用 Computer Use 经本机 Android 屏幕镜像实际操作外观入口、选择素白、回到首页、切换设置/代理并进入和退出网络设置。镜像显示的是 adb 获取的原生应用截图，不是重写的网页界面。
- Native crash buffer 无崩溃记录。

首轮视觉检查发现代理页筛选区占高过多，已将辅助控制收进筛选与排序、批量测速移动到列表下方，复查见 comparison.png 与 comparison-settings-detail.png。

早期测试失败分别是原模拟器安装空间不足、测试未选定所断言的策略组、搜索词与节点名同时命中选择器；已在专用模拟器与修正后的测试中复验通过。不是以失败记录替代通过结果。

实际节点数据使用本地直连验证配置（DIRECT/REJECT），没有写入效果图中的远程节点或虚构速率。连接状态与外网连通性分别表述。原主题及用户数据备份逻辑保留。

截图：preview.png 为三页组合，home-running.png/settings.png/proxies.png 为原始截图；*-en-large.png 为英文大字号。视觉对比详见项目根 design-qa.md。

本地 APK：dist/Clash-Android-0.2.6-dev-plain-arm64.apk，debug 签名；仅验证模拟器，不代表真机 UI/VPN 兼容性。
