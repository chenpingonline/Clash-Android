# 实际订阅验证（脱敏）

2026-10-24，Clash Android 0.2.6-dev，Android 16 / Pixel 9 Pro arm64模拟器。

用户提供的订阅地址通过应用私有临时文件传入；没有写入源码、报告或截图。节点按订阅原始顺序匿名编号，未保存节点地址、密码、UUID或完整出口IP。

## 下载与校验

- CMFA下载成功：517,321字节，约2.19秒；宿主机独立请求也返回HTTP 200。
- 70个订阅节点条目：52个VLESS、16个Hysteria2、2个Shadowsocks；9个策略组、9,263条规则。
- 补齐GEO数据后，应用内校验及VPN启动成功。原始订阅未改写；临时覆写将模式设为global，并设置仅localhost监听的17890端口、error日志等级及清空代理端口认证。

## 节点延迟

- 逐个通过应用的Mihomo延迟接口请求HTTPS检测目标，3路并发，单节点上限8秒。67/70个返回有效延迟。
- 最低42ms，中位数191ms，最高1983ms。
- 未返回有效延迟：Node-046, Node-069, Node-070；不能仅据此断言节点永久离线。

## 真实访问复测

选择三个低延迟样本复测；每次切换前清空客户端连接池、删除内核旧连接，再检查GLOBAL所选节点和实际连接链。HTML页面读取前64KiB并检查预期内容；没有关闭HTTPS证书验证。

| 匿名节点 | 复测延迟 | Google页面 | YouTube页面 | Cloudflare trace | Wikipedia | 显式HTTP代理HTTPS检测 | 1MiB下载抽样 |
| --- | ---: | --- | --- | --- | --- | --- | ---: |
| Node-027 | 50ms | 200 | 200 | 200 | 403 | 204 | 13.93Mbps |
| Node-031 | 52ms | 200 | 200 | 200 | 403 | 204 | 14.95Mbps |
| Node-047 | 142ms | 200 | SSLHandshakeException | SSLHandshakeException | 403 | 204 | 5.48Mbps |

- 所有收到HTTP响应的复测访问均在核心连接记录中观察到所选节点；显式代理检测3/3返回204。Google与YouTube的200样本通过HTML内容检查，Cloudflare的200样本通过trace内容检查。
- 1MiB来自Cloudflare公开下载检测接口，耗时包含握手等开销，仅是小文件吞吐抽样，不代表持续带宽或套餐速率。
- 两个样本成功取得出口地址，但直连基线检测连接失败，无法比较出口变化；报告已移除实际IP。

## 发现的问题与限制

- 新装应用未准备GEO数据库，实际订阅首次校验失败。本次手工从现有Clash-Manager项目复制Country.mmdb、geoip.dat、geosite.dat后才继续；GEO初始化问题尚未修改生产代码。
- 初始模拟器继承DNS时公共域名解析失败，使用223.5.5.5/1.1.1.1显式DNS后恢复。宿主GPU模式下模拟器进程曾以139退出，改用冷启动、SwiftShader软件渲染及禁用Vulkan后完成测试。这些是环境调整，不是APK修复。
- Wikipedia三个样本均为403；Node-047对YouTube、Cloudflare trace和出口检测出现SSLHandshakeException，不能宣称全部网站或全部节点均可用。
- 本轮真实访问采用全局代理模式；没有逐条验收订阅规则、IPv6、真机、移动网络切换或长期后台。
- 测试结束已停止VPN、恢复应用原状态，并清理本轮新安装的测试应用及订阅私密输入。没有提交、推送或发布。

[下载与全部延迟数据](report-latency.json) · [全新连接访问数据](report-real-access.json)

可选测试类：LiveSubscriptionTest；默认跳过，只有显式传入liveSubscription=true并提供私有输入文件才会运行。nodeIndexes参数可限制复测原始节点编号。
