# 素白主题视觉验证

**Source visual truth**: docs/design/plain/reference.png（用户接受的紧凑版，1448×1086，三页同一方案）。
**Implementation**: docs/qa/plain-theme/{home-running,proxies,settings}.png；英文大字号截图位于同目录。
**Viewport**: Android 16，1170×2532px，480dpi，390×844dp；字体缩放 1.0，辅助验证 1.5。
**Normalization**: comparison.png 的源图等比缩至1170px宽，实际截图去除系统状态/导航区后按每页390px宽组合；comparison-settings-detail.png 按相同页面宽度对比设置分组。生成图未提供真实dp/字体元数据，实际应用按确定的14sp正文、12sp辅助文字、32dp图标块、54dp行高实现，不声称逐像素一致。
**State**: 同一素白配色、中文首页连接态/代理页/设置页。源图为虚构远程节点和流量；运行截图为本地验证配置 DIRECT/REJECT、空闲速率和实际选中节点。系统栏由Android绘制；这些内容差异不作为视觉缺陷。

## Findings and comparison history

- 初轮 [P2] 代理筛选操作纵向堆叠，节点列表被推低，偏离紧凑目标。修复：独立素白代理页，筛选/排序与编辑入口折叠，批量测速移动至节点列表下方。后续 comparison.png 中主列表与测速入口均完整可见。
- 字体与排版：采用原生 Android 中英文无衬线字体，20sp标题/14sp正文/12sp说明；较源生成图更克制，符合已确认的紧凑尺寸。1.5倍英文行会增高，不裁切文字。
- 间距与布局：16dp页边距、12dp组间距、20dp圆角、分组列表；下方高级项保留实际功能，滚动后可完整露出，底部胶囊有实际高度对应的滚动尾部留白。主区域比例符合紧凑方案。
- 颜色：#F4F4F4背景、白色面板、黑灰文本，蓝紫流量、彩色功能图标；无旧主题渐变和面板阴影。禁用连接/网络检查按真实运行条件显示灰色。
- 图像与图标：界面不含装饰位图；使用现有 Material 矢量图标库。DNS/内核/备份等图标与生成图的具体字形存在轻微差异，语义明确，列为P3，不阻塞使用。
- 文案与内容：保持真实运行状态、配置名和节点信息；补充连通性说明、总流量、策略组选择及高级设置以保留已有功能。新增中英文翻译已接入。

- 后续 [P2] 英文1.5倍字号时 Disconnected 被右侧按钮挤成单词内换行。修复：该字号下按钮移至单独一行；新增文字布局断言确保状态为一行。修复后截图 home-en-large.png 与 responsive-connection-detail.png 已查看，复验PlainThemeTest两项与lint全部通过。

## Evidence and interactions

- Full-view: docs/qa/plain-theme/comparison.png（源图和运行截图在同一画布中）。
- Focused: docs/qa/plain-theme/comparison-settings-detail.png（设置列表图标、分隔线、字重与留白）。
- Computer Use：本地屏幕镜像中实际点击外观主题入口、素白、返回、底部设置与代理、进入和退出网络设置；确认缺少配置时显示可返回的提示。
- Android 仪器测试：主题选择/重建/持久化/备份恢复、回切原主题；VPN启动/停止，DIRECT↔REJECT策略切换、搜索筛选，VPN/规则二级页；英文1.5倍字体下滚动到关于项。3项通过。
- 单元测试11项、lint、debug构建通过。Native crash buffer为空。

## Follow-up polish

- P3：部分Material图标与效果图的具体形状不同；未采用生成图中的装饰性高光。
- 真机与厂商系统适配未测试。

## Implementation checklist

- [x] 新增主题及恢复白名单，保留既有主题。
- [x] 首页、代理、设置和共享二级组件适配紧凑尺寸。
- [x] 检查主题切换、真实交互、可滚动布局和英文大字号。
- [x] 对比源图与实际截图并修正P2布局问题。

final result: passed

## 2026-10-24：圆角可折叠代理组与双列节点

**Source visual truth**: `docs/design/proxy-accordion/reference.png`（用户确认的独立圆角组头、组间留白与组内双列卡片）。
**Comparison**: `docs/qa/proxy-accordion/comparison.png` 与 `detail.png`，源图及真实运行截图在同一画布中，已打开核对；额外检查 `large-dark.png` 和 `live-two-expanded.png`。
**Viewport/state**: 390×844dp Android 16，中文浅色；两个组收起、一个组展开并选中一个成员。源图示例名与内核返回的组顺序、Direct测试协议及实际延迟不同，未伪造运行数据。附加426dp组件检查与英文1.5倍字号深色检查。

- 字体：沿用原生中英文无衬线与现有主题字号；组名、当前成员、协议和延迟层级清晰。长组名换行，大字体节点改为单列。
- 间距：独立12dp圆角组头、收起组间10dp、展开组间16dp、节点间8dp；双列等宽，节点10dp圆角。节点测速保留48dp触控区域，因此卡片略高于静态效果图。浮动底栏沿用当前应用，列表末尾预留底栏空间。
- 颜色：沿用现有主题色板，浅色背景较源图略灰；组头使用浅主题色，选中节点主题色描边、勾选及淡填充。深色与素白各用自身色板。
- 图像与图标：无装饰位图；原生Material图标。源图底栏图标及白底样式没有替换既有全局导航。
- 文案与状态：显示真实组类型及成员；数量紧随组名，手动/自动信息放在摘要中。测速提供进行中、失败和停止反馈；不自动切换选中节点。
- 交互：Computer Use验证独立收起、展开、成员选择、单节点测速不误切换以及多组同时展开。组件与真实内核设备测试补充过滤、批量去重、并发/取消与自动组只读。
- 无未解决P0/P1/P2问题。P3：与生成图不同的原生图标字形、组标题数量/类型文案排列；保留应用全局导航与主题风格。静态效果图的虚构节点及延迟未作为真实网络内容复刻。

验证：11项单元测试、7项设备测试、lint与debug构建通过。真机及真实订阅未复测。

final result: passed

## 2026-10-24：默认素白与代理页风格统一

用户在已确认的折叠双列结构上明确要求素白风格。风格来源：`docs/qa/plain-theme/proxies.png`；结构来源继续使用 `docs/design/proxy-accordion/reference.png`。最新运行截图 `docs/qa/plain-proxy-default/live.png`；`comparison.png` 在同一画布中对比上版与素白实际页面，已打开核对。390×844dp、同一组展开、同一选中节点；本地direct协议/延迟保持真实。

- 字体：素白既有14sp节点名、12sp辅助文字、14sp组名，较普通主题更紧凑；标题黑色、摘要灰色。成员数量移到中性小标签，长组名可换行。
- 布局：已确认的多组折叠、双列节点和间隙保留；组头16dp、节点12dp圆角与现有素白组件协调。无外层嵌套卡片，底部留白避让浮动导航。
- 配色：#F4F4F4背景、白色组头与节点，取消素白下浅蓝组头及普通节点描边；节点文字保持黑色，选中状态用蓝色细边框/勾选，延迟绿色、失败错误色。其他主题继续使用各自色板。
- 图像/图标：无装饰位图，沿用Material图标和素白导航样式；没有新增生成图资产。
- 文案/交互：现有搜索、更多菜单、组内和一键测速保持可操作；Computer Use核对展开收起、JP 01选择及HK 02单节点测速不切换当前节点；新安装打开外观页，素白已默认选中。
- 检查通过：13项单元测试、7项设备测试、lint及构建通过。默认偏好和旧备份回退统一，明确的system等用户选择保留。没有未解决P0/P1/P2问题。

final result: passed


## 2026-10-24：0.2.11 素白子页与外层统一

**Source visual truth**: docs/design/plain-subpages/reference-settings.png（724×1384）与 reference-home.png（726×1440）；用户指定迁移图1/2的分组样式，而不是复制相同页面内容。旧表单图 before-network.png、before-vpn.png 留作变更基线。
**Implementation**: docs/qa/0.2.11/network-live.png、vpn-live.png；Android 16 ARM64，1170×2532px，390×844dp，480dpi，中文100%，真实运行的16直连节点演示配置，VPN已连接。英文150%证据为 overview-en-large.png。
**Normalization / state**: style-comparison.png 将四张源/结果图按同一页面宽度360px等比组合，不拉伸；参考图裁掉了底部导航，系统栏裁切高度不同，不以这些差异判断布局缺陷。参考设置是导航列表，结果是参数列表，文案和行尾值按实际功能保留；首页连接态与VPN连接态比较同类状态卡。不是相同路由的逐像素复刻。
**Full-view evidence**: docs/qa/0.2.11/style-comparison.png，已打开检查。
**Focused evidence**: docs/qa/0.2.11/row-comparison.png，源图首个3行分组与新网络页前3行裁剪后按同一卡片宽度490px对比，已打开检查图标、字号、分隔线、留白、圆角。

### Findings and comparison history

- 初轮[P2] 子页保留OutlinedTextField，图标、连续列表与组内分隔线缺失；灰色说明大块、全宽浅蓝主按钮形成第二套视觉语言。修复：PlainSettingField/Toggle/Group使用现有PlainIcon、PlainGroup与54dp设置行；短值直接右侧编辑，长文本及大字号分行，说明改组外灰字，主操作使用右侧蓝色按钮。
- 初轮[P2] 英文150%时VPN状态 Disconnected 被操作按钮挤压至单词内换行；证据 iteration-1-overview-en-large.png。修复：大字号操作按钮移到下一行；PlainThemeTest增加VPN状态单行断言。修复后 overview-en-large.png 中状态完整。
- 初轮[P2] DNS和内核的后续入口缺少分隔线及语义图标。修复：改用同一PlainGroup/PlainDivider，DNS紫色图标、内核/诊断语义图标。复查 overview-zh.png 与 overview-en-large.png。
- 字体/排版：沿用素白Android系统字体、14sp设置名、12sp说明、20sp连接状态，与外层同源；英文150%时名称/值自适应分行。无新增位图文字或替代字体。
- 间距/布局：16dp页边距，20dp白色组卡，14dp组内水平留白，32dp彩色图标、12dp图文间隔、54dp短设置行；细分隔线与外层PlainGroup一致，无嵌套描边框。长文本输入和可滚动编辑器按实际用途保留。
- 配色：#F4F4F4底、白卡片、黑灰文字，蓝色参数图标与主操作，绿色安全/VPN、紫色DNS，开关关闭态去除粗描边。危险操作仍使用错误色。
- 图像/图标：无需要生成的装饰位图；使用既有Material图标库，与源图为同一体系。
- 文案/内容：使用用户真实配置名称、VPN状态与值；空值明确“未设置”，密码继续遮蔽，保留原校验/保存及中英文文案。未写入源截图的虚构数据。
- 交互：Computer Use检查启动VPN、网络设置行聚焦/光标、返回和VPN页；系统出现手写引导并已取消，不视为应用弹窗。EditorInteractionTest验证编辑/校验/保存与无效输入保护；32页×两种语言/字号遍历、PlainThemeTest通过。

### Implementation checklist

- [x] 共用边框less参数行和分组，沿用外层图标及分隔线。
- [x] 大字号适配及VPN状态断言。
- [x] 参数编辑、密码遮蔽、校验、保存与其他主题保留。
- [x] 修复P2后重新捕获中英文截图并与参考组合复查。
- [x] 模拟器保留运行中的演示VPN，停在新版VPN子页供用户操作。

**Follow-up polish**: 源图没有长文本/空值编辑态，采用组内多行输入作为必要的功能扩展；未在实体手机验收，不作真机显示保证。无待修复P0/P1/P2。

final result: passed


## 2026-10-24：0.2.12 子页操作按钮减重

Source visual truth：用户外层分组截图 docs/design/plain-subpages/reference-settings.png；修改前用户截图裁掉模拟器窗框后为 docs/qa/0.2.12/before-vpn.png（772×1674）。Implementation：docs/qa/0.2.12/vpn-live.png（1170×2532，390×844dp，480dpi，中文100%，演示VPN已连接）。

Full-view/focused evidence：button-comparison.png 将外层参考、修改前/后VPN页按同一350px页面宽度等比组合，已打开并比较；主按钮和顶部启停区在此比例下均清晰，无需额外放大裁剪。源外层参考为导航页，子页操作按其白色分组/蓝色文本语言迁移，不作相同路由像素复刻。

- [P2 resolved] 0.2.11实心蓝胶囊在白色分组页中抢眼，既有报告对这一视觉重量判断不足。0.2.12将保存/校验等主操作改为54dp白色操作行+蓝色文字，VPN启停改为至少48dp的文字按钮；导入保存也去掉实心蓝底。
- 字体：沿用素白14sp标题/操作文字，未改变参数、说明或系统字体。
- 间距：白色操作行20dp圆角，与既有分组同源，顶部不再增加独立色块；触摸面积保留。
- 色板：沿用#F4F4F4底、白卡、既有蓝色文本及灰色禁用状态；危险操作仍红色。
- 图像/图标：无新图像资产，既有彩色Material图标保留。
- 文案/交互：动作名称未变，EditorInteractionTest及PlainThemeTest共3项通过，包括禁用、保存、主题及大字号。Computer Use复查启动VPN、导航与最终VPN页面。

Checklist：白色动作行、文字启停、禁用状态、对比截图、模拟器预览已完成。无未解决P0/P1/P2；不声称实体手机验证。

final result: passed


## 2026-10-24：0.2.13 全部素白子页动作统一

Source visual truth：0.2.12已接受的白色分组/蓝色文字操作行，docs/qa/0.2.12/vpn-live.png；外层视觉仍以docs/design/plain-subpages/reference-settings.png为准。Implementation：docs/qa/0.2.13/screens/全部32子页首屏和末尾操作区，中文100%、英文150%，1170×2532px/390×844dp/480dpi；参考与实现同一原生Android字体及密度。所有页面按同宽280px缩略组合成pages-zh-1..8.png、pages-en-1..8.png，16张组合图均已打开逐组检查；non-vpn-preview.png补充实际运行的自动化页面。是跨子页的组件样式迁移，页面文案和状态各异，不作为像素一致的整页复制比较。

### Findings and comparison history

- [P2 resolved] 初轮遗漏原生OutlinedButton/TextButton及确认/取消、后台系统入口、导入校验、预览等入口。现有专属子页文件全部经MainButton/SubpageButton/SubpageTextButton路由至PlainActionSurface；source-audit.md无旧操作按钮直接调用。
- [P2 resolved] 全量截图又发现4个编辑页仍使用YAML描边FilterChip。素白下改为白色分组中的编辑器切换/恢复动作；其他主题保留旧选择器。4页8种语言/字号状态重新捕获并合并动作清单。
- [P2 resolved] 英文150%时编辑器切换和两行恢复文案的垂直中心不齐。改为居中对齐，editor-action-alignment.png包含同宽修改前后聚焦对比，已打开复查。
- 字体/排版：沿用素白14sp操作文字，同一ProvideTextStyle，中文和英文长标签可换行。导航/说明仍保留其原有层级。既有少量英文页面中文说明未在本按钮任务扩展翻译，列为范围外文案后续项。
- 间距/布局：全宽独立操作54dp，组内至少48dp；相同20dp圆角、白色分组。无新增实心/描边动作胶囊；末尾操作滚动后均可访问。顶部导航、开关和持久模式选择器保留对应控件。
- 配色：白底蓝字，禁用灰字，危险红字；确认区无红色实心按钮。按压危险动作的淡红ripple为短暂触摸反馈，不是静态容器颜色。
- 图像/图标：无新位图资产，Material图标与外层继续复用。
- 内容/行为：原动作名称与处理回调保留，YAML编辑器增加清晰的“可视化编辑”返回入口；导入、订阅诊断、备份/恢复、编辑/校验/保存、删除确认及主题重建的7项最终交互测试通过。

### Verification and checklist

- [x] 全部32子页、64语言/字号状态、157动作实例、16禁用实例；实际路由及至少48dp触摸高度断言通过，清单button-checklist.md、actions.tsv。
- [x] PageCoverageTest全量两项通过，包含空配置32页可返回；编辑页修复后8状态定向复验。
- [x] 9项不同界面测试最终通过（7交互+2全量）；13单元、构建与lint通过。最初目录检查受同名入口和转场影响，未计作通过；测试增加真实路由校验与确定性定位后重新全量完成。
- [x] 所有中文和英文组合图、危险确认delete-plain.png、编辑器聚焦对比均已查看。
- [x] Computer Use通过实际导入恢复演示配置、启动VPN、设置导航及自动化子页；预览留在非VPN页，VPN运行。

无待修复P0/P1/P2按钮样式问题。条件出现的远程/诊断等动作由同一组件、源码清单与相关状态交互测试覆盖，未连接真实远程控制器或实体手机。

final result: passed

## 2026-10-25 Dock reference material calibration

Reference: `/var/folders/sp/90pk0ss17bj66n2wcc3fgx1h0000gn/T/codex-clipboard-e626b1b2-0f2d-4d12-8d87-751ce8659b5f.png` (870×610). Native Compose fixture: 390dp × 285.59dp at density 3, captured 1170×857 and normalized to the 833px content width after removing the reference's 37px pillar. The two-tab fixture measures material only; the shipped app retains five tabs and its existing height/radii.

Implementation and comparison artifacts: `dist/qa-dock-reference-match/reference-final.png`, `comparison-final.png`, `comparison-before.png`, `phone-dock.png`, `phone-scroll.mp4`, `scroll-grid.png`. The shared native `dockGlassSurface` is rendered over a reconstructed photo row made from the reference's unobscured thumbnail. Covered source pixels cannot be recovered exactly; this is not a pixel-identical background or a screenshot painted over the app. No generated replacement artwork was used.

Observed and corrected:
- P1: previous 4dp blur and 58% shoulder coating created narrow dark arcs and horizontal lettering streaks. Broad 10dp sampling with 78–82% white coating now transmits low-frequency photo colour gently across the body and shoulder. The source remains live and separate from foreground controls.
- P2: a white-on-white perimeter had insufficient separation. A cached 8dp soft halo, 1.4dp white rim and faintly off-white body supply separation without adding a grey inset groove.
- P2: opaque selection hid all photo response. A 6.5% neutral shade over the same frosted body preserves selection contrast while retaining background colour. Dark-theme selection remains unchanged.
- Surrounding content keeps a lighter 2dp softening and gradual white fade; it is independent of the inner optical material. No changes to Dock height, corner radius, tab labels, production icons or horizontal stretch geometry.

Verification:
- Build, Android lint and `git diff --check` passed. All 12 native tests passed: reference fixture, eight material/foreground/source tests and three motion tests.
- Assertions were recalibrated for the reference's softer response: a 6dp green detail still changes the live optical edge by >0.025 RGB; blank-body luminance >0.93; edge stripes soften while retaining detail; refraction at the exact boundary remains continuous. Text suppression uses thin letter-sized strokes rather than a 26dp opaque image-sized slab, since the reference deliberately transmits broad image colour. Tests also retain fixed geometry, large-font label and selection-contrast checks.
- Wireless upgrade returned Success on the physical phone. UI-tree-driven proxy-list swipes were recorded, and the affected Dock frames were visually inspected. Modern gfxinfo reports 1 janky frame / 554 frames (0.18%); legacy count differs (252), so this brief run does not establish generally jank-free behavior.
- Computer Use inventory did not expose the emulator/phone as a native target; the Android QA skill's adb UI-tree, input and capture workflow supplied actual-device interaction evidence.

Remaining fidelity limitation (P2): identical hidden source imagery and Xiaomi's proprietary rendering parameters are unavailable. The static photo comparison is materially closer, but the exact video-time refraction and full surrounding fade are not established as identical. Reference fixture fonts/icons are native stand-ins; production app retains its own functional labels/icons. No claim of complete replication is made.

final result: blocked

## 2026-10-25 Follow-up: approaching text must not blur early

User reference: `/var/folders/sp/90pk0ss17bj66n2wcc3fgx1h0000gn/T/codex-clipboard-3f29a78e-664c-4f11-908f-b9aa7c153337.png`. The red box identifies the full-width strip above the capsule, where letters became defocused before entering the glass.

Removed the 24dp pre-entry blur region. The lower-page blur now starts at the capsule top and transitions over 12dp below it. The existing white fade remains independent, so approaching text can brighten without losing sharp edges. Inner sampling, perimeter, size, corner radii and animation were not changed by this follow-up.

Validation: build, lint and 11 material/motion instrumentation tests passed. The approaching-content test now checks retention of sharp high-frequency detail above the capsule; source fading and optical sampling remain separately tested. `dist/qa-dock-outer-clarity/ambient-fixture.png` shows sharp vertical boundaries above the top edge. Wireless installation returned Success; phone scroll captures are saved in the same directory. Overall exact Xiaomi material fidelity remains unverified as documented in the prior section.

final result: blocked

## 2026-10-25 Dark Dock: quiet perimeter with live background response

References: user screenshots `codex-clipboard-5969a9f2-d893-410f-9952-b62009dc4fe5.png` and `codex-clipboard-d1492d1e-2975-445e-87ee-9c9b0b0fc2fe.png` under the supplied temporary folder. The first shows a near-black body with a fine neutral rim; the second shows broad thumbnail brightness passing through the same material without a raised glossy edge.

Dark-only changes: neutral #171717 optical tint, equal body/shoulder coating, much lower directional reflection, no white surface glaze or catchlight, and a consistent faint grey hairline instead of the former bright-top/dark-bottom bevel. Light-mode constants, geometry, selection motion and approaching-content clarity remain unchanged. The API31 fallback uses the same dark tint/coating intent.

Build/lint/diff checks and 12 material/motion tests passed, including a new black/bright-backdrop regression: dark body remains near-black, inner shoulder does not create a bright ridge, selection remains distinct on black, and bright source pixels still affect the glass. An initial test sampling range overlapped the icon; it was corrected to sample the unobstructed upper shoulder, then the full suite reran successfully.

Wireless installation returned Success. Actual-phone proxy-list navigation and scrolls were driven from UI-tree bounds and recorded; `dist/qa-dock-dark/phone-dock.png`, `phone-scroll.mp4` and `scroll-grid.png` were inspected. Native test captures `dark-glass-black.png` and `dark-glass-bright.png` show the material under controlled black/white source changes. This verifies the dark bevel correction and live material response; it does not establish pixel-identical Xiaomi rendering.

final result: passed
