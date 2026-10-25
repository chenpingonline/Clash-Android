# 开发版验证记录

此页保留 0.1.0 历史记录；当前实现与验证请看 [0.2.0 记录](0.2.0/README.md)。

日期：2026-10-23。对象：0.1.0-dev，Android 16/API 36，Pixel 9 Pro arm64 模拟器，4 KiB 页大小。非真机验收。

## 已执行

- Gradle `:app:assembleDebug :app:lintDebug` 成功；lint 无阻断错误，仍有依赖版本更新、SharedPreferences KTX 建议等警告。
- `go test -race ./... clashmanager/configyaml` 通过；包含私有配置路径、移动端网络边界、非法配置拒绝与共享增强包原有测试。macOS 链接器有 LC_DYSYMTAB 警告，测试仍成功。
- 安装实际 APK，导入内置“直连验证”YAML，保存、重启应用后仍可读取。
- 接受 Android VPN 系统授权，实际启动、停止、再次启动成功；修复私有配置路径、gVisor 编译标志、非 Root packages.xml 访问及旧 Service 销毁竞态。
- 从模拟器 shell 发出 TCP HTTP 请求到宿主本地测试服务（10.0.2.2:18080），返回 200。将默认策略切为 REJECT 后同一请求无 HTTP 响应，切回 DIRECT 后恢复 200；可观察真实内核流量。此验证不等于远程代理节点验证。
- 浅色切换到深色，再切回浅色，VPN 保持运行；重启应用保留主题。修复深色系统栏图标对比度、底部导航选中色及节点行点击范围。
- 最终版本节点行点击可将 REJECT 切回 DIRECT，HTTP 200 恢复。测试后主动停止 VPN，关闭临时 HTTP 服务。

模拟器操作通过 Android QA 的 adb/UI 树完成，截图核对实际渲染。Computer Use 未能选择模拟器窗口，未将其计为成功的界面测试。

## 视觉核对

沿用第 2 组的蓝色强调、圆角分组卡片和五栏导航。当前真实数据不会填入设计图中的示例节点、延迟、订阅剩余量。基础页面已渲染，复杂编辑页还没有达到设计稿的完整控件与交互；不能称逐页设计验收完成。

- [浅色首页](home-light.png)
- [深色首页](home-dark.png)
- [深色主题设置](appearance-dark.png)
- [深色代理列表](proxy-dark.png)

## 未完成的验证

尚未验证真实订阅服务、远程代理协议、独立 DNS/UDP 测试、IPv6、分应用完整覆盖、网络切换、后台长期运行、系统回收、其他 API/厂商真机、16 KiB 页设备。外网 example.com 探测未取得有效 HTTP 响应，不能计为外网连通成功。完整 DNS、网络、远程控制等高级编辑页仍为标记清楚的本地草稿，未计入可用功能。

APK 为本地 debug 签名、arm64 构建，未发布。正式发布前需完成上述验证、传递依赖许可和源代码交付清单。
