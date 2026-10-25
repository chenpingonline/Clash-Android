# Image Gen 提示集

生成方式：内置 image_gen；参考图：`../direction-2.png`。用户已选第2组，这些是功能页延展，不是新的备选风格。

## 修订记录

部分首版画板采用过宽画布导致下排裁切，因此02/03/04/06/07/08重绘：追加“portrait 2048×2400、4列2排、8个完整390:844页面、底部至少40px留白、禁止裁切”的约束，参考相应首版及01完整画板。04同步修正规则策略与代理链一致、YAML错误示例及脚本作用范围；03修正预览步骤和覆写示例；07修正主题标签与应用图标。最终以索引中保存的画板为准。

## 共用提示

Use case ui-mockup. Create high-fidelity Android Clash UI design handoff board extending attached APPROVED reference direction-2, not new style options. Exactly EIGHT complete screen frames, FOUR columns and TWO rows. Each frame logical 390x844, board about 1720x1850, render high resolution. Top row four LIGHT pages, bottom row identical four pages in DARK theme in exactly same order with SAME layout/content/state. Tiny outside frame captions include specified IDs and title. All Chinese UI text sharp and readable, body14-16sp, touch48dp, horizontal16dp padding. Faithful reference: electric blue primary, subtle blue pills, restrained grouped white surfaces, thin separators, compact rounded fields, no excessive shadows. Light bg #F5F7FB surface #FFFFFF text #172033 secondary #596579 primary #165DFF. Dark bg #111827 surface #1B2434 field #222E42 divider #344258 text #EDF2FA secondary #B0BED2 primary #85ACFF; primary button dark blue fill #245BDB white text. Dark must retain EXACT geometry. This is app UI only, NO phones, OS status bars, clocks, home indicators, browser chrome. Secondary pages have BACK arrow and title, no bottom main-navigation tabs. Fixed bottom form actions where appropriate. Show useful realistic fields, not generic placeholders. Date anchor 2026-10-23. Credentials masked, use example.org and example.net. Do not label VPN as 系统代理. No invented premium account/cloud services. Every specified page must appear once in each theme. Overflow content may scroll, show natural scroll cue. Use clear production typography, no decorative illustrations. Board topic and exact page contents follow:


## 01-proxies · 开始使用与代理编辑

01 首次使用: step 导入配置→检查配置→启动VPN, 配置已就绪 green check, explanation Android系统将请求VPN授权, main 启动VPN, secondary 稍后设置. 04 代理组详情: 自动选择/url-test, currently 香港01 42ms, last test time, test button, members 香港01 日本02 新加坡01 latencies, edit group top action. 05 节点编辑: name 香港01, protocol Shadowsocks dropdown, server hk.example.org, port443, cipher selector, masked password, UDP toggle, 传输设置 collapsed, buttons 保存草稿 and 保存并应用. 06 代理组编辑: name 自动选择, type url-test, selected members checkboxes, reorder handles, healthcheck URL example.org/check, interval300s, tolerance50ms, bottom 保存并应用.

## 02-profiles · 订阅配置与规则入口

08 添加配置: 链接/文件/粘贴 tabs selected 链接, name 日常订阅, masked subscription address https://example.org/••••, 更新间隔12小时, button 识别并预览, validation green 已识别Mihomo配置, bottom 保存 / 保存并应用. 09 订阅详情: 日常订阅 当前使用, quota12.4GB/200GB, update timestamp2026-10-23 14:20, 更新 action, rows 订阅信息 规则增强 节点与代理组 扩展覆写 扩展脚本 YAML配置, footer 最近应用成功. 10 订阅信息: name/address masked fields, auto-update toggle, interval12小时, 当前配置更新后自动应用 toggle, last actual update2026-10-23 14:20, small 后台执行时间可能延后, bottom 保存修改. 11 路由规则: 当前配置 日常订阅, 规则/规则集 tabs, search, 类型/策略/来源 filters, compact rows DOMAIN-SUFFIX example.org DIRECT toggle; DOMAIN api.example.net 自动选择 toggle; RULE-SET geosite-cn DIRECT toggle; MATCH 自动选择 toggle, bottom 添加规则.

## 03-rules · 规则管理与覆写

12 编辑规则: scope 日常订阅, 未保存修改 badge, 类型 DOMAIN-SUFFIX dropdown, 内容 example.org, 策略 DIRECT, 位置 前置/后置 pills 前置 selected, 原始规则 排除/恢复 row and 自定义规则 list, bottom 保存草稿 保存并应用. 13 导入规则集: progress 来源→预览→保存, preview 已识别 classical 128条, name 自定义规则, target 自动选择, merge 前置/后置/替换, warning 2条不支持已列出, expandable unsupported list, bottom 保存到订阅 保存并应用. 14 规则集详情: 国内域名, type domain, count2048, source https://example.org/rules.yaml, interval24h and last update2026-10-23 14:20, 更新 button, searchable entries example.org example.net, update-success state. 15 扩展覆写: scope 全局/当前订阅 tabs 当前订阅 selected, enabled switch, code editor line numbers with valid YAML mixed-port:7890 and ipv6:true, 查看合并结果 entry, 校验通过 status, bottom 校验 保存并应用.

## 04-editors · 编辑器与连接详情

16 扩展脚本: scope 当前订阅·日常订阅, enabled toggle, line-number JavaScript editor function main(config) { config.ipv6 = true; return config; }, execution result success 耗时12ms, error output area collapsed, bottom 校验 保存并应用. 17 YAML配置: 原始配置 / 生效配置 tabs 原始 selected, search, line-number editor port:7890 mode:rule proxies:[] rules: followed by malformed sample at line8, explicit red 第8行：缺少冒号 and highlight, bottom 校验 and disabled 保存并应用, draft-preserved notice. 18 配置历史: date grouped backups 2026-10-23 14:20 规则更新 / 10:05 订阅更新, selected backup diff preview + DOMAIN-SUFFIX,example.org,DIRECT in green and removed line red with +/- symbols, bottom 导出 恢复预览. 20 连接详情: example.org:443, app Chrome, protocolTCP, IP203.0.113.10, upload128KB download2.4MB time03:21, 命中规则 DOMAIN-SUFFIX,example.org, 代理链 自动选择→香港01→目标, actions 添加规则 and red 关闭连接.

## 05-vpn-network · VPN与网络

23 VPN设置: 已授权 VPN已启动 separate status, IPv4 and IPv6 toggles, MTU1500, 绕过局域网 enabled, 排除网段 list192.168.0.0/16, 系统始终开启VPN row with external-link icon not fake toggle, bottom 保存并重连 with note 更改路由需要重连. 24 分应用代理: 全部/仅选中/排除选中 segmented, 仅选中 selected, search, 显示系统应用 toggle, app rows Chrome Telegram Firefox 微信 with package names and checkboxes, 已选3个应用, bottom 保存并重连. 25 网络与端口: Mixed enabled7890, HTTP disabled7891, SOCKS disabled7892, 允许局域网连接 toggle off, 监听地址127.0.0.1, 代理认证 entry, IPv6 toggle, note 端口服务不等同于热点透明代理, bottom 保存. 26 DNS基础: DNS enabled, source 当前订阅+全局设置, mode Fake IP / Redir Host, listen127.0.0.1:1053, fakeIP198.18.0.1/16, IPv6解析 toggle, entries 解析服务器 Fake IP过滤 回退过滤 Hosts映射, bottom 保存并应用.

## 06-dns · DNS详细设置

27 DNS服务器: five distinct grouped editable lists 默认域名服务器1.1.1.1; 主解析https://dns.example.org/dns-query; 回退tls://dns.example.net; 代理节点DNS1.0.0.1; 直连DNS223.5.5.5; add plus each group, bottom 保存并应用. 28 Fake IP与域名策略: 过滤/域名策略 tabs 过滤 selected, 黑名单/白名单/规则模式 selection, search, rows *.lan localhost *.local with edit icons, 批量粘贴 plus 添加域名, note 匹配域名使用真实IP, bottom 保存并应用. 29A DNS回退过滤: GeoIP enabled, 国家代码CN dropdown, IP段 list240.0.0.0/4, 域名列表 +.example.org, 新增条件, explanatory short note 回退过滤决定何时使用回退解析, bottom 保存并应用. 29B Hosts映射: search, rows router.lan→192.168.1.1 nas.lan→192.168.1.10, edit/add form 域名 dev.lan IP地址192.168.1.20, add mapping action, validation success, bottom 保存并应用.

## 07-runtime · 内核、自动化与外观

30 内核管理: Mihomo Android 运行中, 架构arm64-v8a, 内核版本 内置版本, 重启/重载 controls, log levelinfo, test URLhttps://example.org/check, timeout5000ms, 更新随应用版本发布 info row, 查看启动日志. 31 GEO数据: GeoIP GeoSite MMDB ASN rows each last update2026-10-23 filesize and update icon, 自动更新 switch, 周期24小时, source management entry, bottom 全部更新, one file 更新失败 重试 state. 32 自动化与后台运行: 订阅自动更新12小时, GEO24小时, 最近执行 今天14:20成功, 下次计划明天02:20, 系统始终开启VPN entry, 电池后台限制 当前受限 row 去设置, note 计划可能因系统调度延后; no guaranteed keepalive. 33 外观与语言: theme 跟随系统/浅色/深色 selection, LIGHT top screen 浅色 selected DARK bottom 深色 selected (only permitted state difference); mini theme preview, language 简体中文/English/跟随系统, 字体大小 跟随系统, 减少动画 toggle, 应用图标 small choice row, immediate preview note.

## 08-maintenance · 备份、远程与诊断

34 备份与恢复: export checklist 配置 订阅增强 应用偏好 all checked, 含订阅凭据 请妥善保存 notice, 导出备份 primary, 从文件恢复 entry, restore preview backup2026-10-23 3配置 12规则, 恢复前自动备份 toggle, bottom 验证并恢复. 35 关于与更新: Clash Android icon small, 应用版本 开发预览, 内核版本 内置版本, 检查更新 button, 更新记录 开源许可 问题反馈 诊断导出 rows, no fabricated release version. 36 远程控制器: 本机/远程 segmented remote selected, note 远程管理不会接管手机流量, instance 家中NAS, address https://nas.example.org, masked token field, connection test button, 已连接 管理模式 status, capability rows 连接 日志 规则, 内核更新 对端不支持 disabled. 37 诊断与导出: include toggles 应用日志 内核日志 配置摘要, 脱敏预览 editor subscription URL replaced [已隐藏] password [已隐藏], time range最近15分钟, export formatZIP, note 不包含原始凭据, bottom 导出诊断包.
