# 雾绿磨砂主题验证 — 2026-10-24

基于第 2 张生成方案实现为原生 Android 的第四个主题（sage），从首页调色盘或设置 → 外观与语言选择。主题为固定浅色雾绿；不随系统深色变化，原来的「跟随系统」仍保留。

## 检查结果

- assembleDebug、lintDebug、testDebugUnitTest 通过；最终视觉微调后 assembleDebug、lintDebug 再次通过。
- SageThemeTest.selectRecreateAndRestoreTheme：在 UI 选中雾绿磨砂，检查偏好值、Activity 重建、备份恢复，依次切回深色、浅色、跟随系统，全部通过。
- Android 16 / Pixel 9 Pro 模拟器：使用 adb UI 树定位操作，检查首页、代理、外观、中英文及强制停止后重新启动。选择保留，未见控件文字截断。
- Computer Use 无法绑定 qemu 原生窗口，改用 Android 专用 adb 工作流。
- 模拟器安装一度空间不足：清理可回收缓存；最终更新使用 pm uninstall -k 保留数据后重新安装成功。未清空用户数据。

## 视觉边界

这是原生功能界面的主题适配，不是将概念图嵌入界面。磨砂效果由预先柔化的背景位图、半透明表面、浅色细边框实现，不对滚动内容进行实时 backdrop blur。继续保留 VPN 状态、检测等现有功能，首页可滚动；系统栏由 Android 渲染。

参考有已导入配置，而当前模拟器无配置，因此截图显示「导入配置」且电源按钮禁用，这是原有安全条件。未为截图伪造配置或开启 VPN。真机 GPU/内存和运行中 VPN 切换主题未单独测试。

## 素材

背景：app/src/main/res/drawable-nodpi/sage_mist.png，内置 ImageGen 生成；应用中以 0.6 alpha 显示以减弱背景对比。

生成提示：Generate a background texture asset for a native mobile app, 9:20 portrait. Full bleed soft abstract sage green fog on off-white ivory. Extremely defocused subtle botanical shadow silhouettes at top right and bottom left, soft pale gray-green light in middle, diffuse matte frosted mist. Very low contrast, mostly light ivory, no blue, no cyan. NO text, UI, buttons, icons, frame or objects in focus. Background only for crisp dark UI and translucent white panels on top. Elegant quiet light sage glass visual.
