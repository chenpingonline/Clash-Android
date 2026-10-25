# 0.2.11-dev 素白子页统一

按用户指定的外层首页/设置截图迁移分组结构：彩色图标、连续白色设置行、细分隔线、右侧编辑值；长文本保留多行编辑，说明为组外灰字，主按钮右侧紧凑蓝色。大字号时参数名/数值及VPN状态/按钮按空间分行。

- testDebugUnitTest 13项通过；assembleDebug、assembleDebugAndroidTest、lintDebug通过。
- EditorInteractionTest、PageCoverageTest#secondaryPagesRenderInBothThemes(plainOnly=true)、PlainThemeTest共4项通过。32子页×中文100%/英文150%=64个渲染状态，且验证真实编辑、无效输入保护、保存、主题/状态适配。原始输出 instrumentation.txt。
- 新增VPN大字号“Disconnected”不在单词内换行的断言，修复前后截图保留。
- Computer Use检查真实运行演示VPN下的新网络/VPN子页、编辑值聚焦和返回。测试配置保留，VPN恢复运行。
- 视觉报告见仓库根 design-qa.md 的0.2.11节；来源与新截图组合 style-comparison.png、row-comparison.png，中文/英文概览 overview-zh.png、overview-en-large.png。
- 未连接实体手机。

安装包：dist/Clash-Android-0.2.11-dev-arm64.apk，versionCode13，cn.nascp.clash.dev，debug签名。
SHA-256：82aa43aa087b12ae943f1608e0e54677217b7db2fbf4447589955cd821d0d3c7
