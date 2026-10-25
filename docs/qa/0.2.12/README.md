# 0.2.12-dev 子页操作行

- 素白子页通用主操作由右侧实心蓝胶囊改为全宽白色分组内的蓝色文字行，54dp最小高度，禁用文字显示灰色。
- VPN启停改为状态卡内的TextButton，保留48dp触摸高度；导入保存按钮也改为白底蓝字。其他主题与外层页面保留。
- EditorInteractionTest与PlainThemeTest共3项通过，验证保存、校验、禁用状态、主题切换与英文150%字号；assembleDebug、assembleDebugAndroidTest、lintDebug通过。原始输出 instrumentation.txt。导入按钮最终调整仅涉及颜色/圆角，未重复执行系统文件选择器流程。
- Computer Use检查新版启动VPN、设置导航与VPN页，演示VPN已恢复运行，预览留在VPN页。实体手机未验证。
- 外层参考、用户修改前截图和修改后实际截图同一输入对比：button-comparison.png。新版截图 vpn-live.png。
- Clash-Android-0.2.12-dev-arm64.apk，versionCode14，本地debug签名，88810080字节，SHA-256 `7386fbdc5eb0883ea73a7be774c611932119d6defdab0f1bbeed9ed943d086ad`。
