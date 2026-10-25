# 0.2.13-dev 全部素白子页按钮统一

## 范围与实现

全部32个子页的操作统一经过 PlainActionSurface：白色分组中的蓝色文字，禁用灰字，危险红字。MainButton、原Button/OutlinedButton/TextButton、确认/取消、诊断/编辑/远程动作及编辑器切换均接入共享组件。独立操作为整行，组内操作按所在行布局，触摸高度至少48dp。其他主题保留原Material按钮。

源码检查 source-audit.md 确认子页文件中无旧操作按钮直接调用；完整32页清单 button-checklist.md，逐页动作标签/禁用数 actions.tsv。64种语言/字号状态共157个动作实例，其中16个禁用实例。条件显示的动作另外由共享组件和交互测试覆盖。

## 验证

Android 16 ARM64模拟器，1170×2532px，390×844dp，默认字号与英文150%，未连接实体手机。

- 全量 PageCoverageTest 两项通过：32子页中英文/字号64种状态的真实路由、渲染、触摸高度与动作记录；空配置32子页可返回。首屏及最后操作区截图见 screens/，已查看 pages-zh-1..8.png 和 pages-en-1..8.png。输出 instrumentation-pages.txt。
- 发现4个编辑页仍有YAML描边切换，改为白色操作分组；这4页8种状态重新验证，见 instrumentation-editors.txt、instrumentation-editor-layout.txt；合并最新记录到 actions.tsv。英文长操作文案左右中心对齐，见 editor-action-alignment.png。
- 最终交互回归7项通过：EditorInteractionTest、PlainThemeTest两项、SubscriptionUiTest两项、DocumentFlowTest、FeedbackUiTest删除确认。覆盖编辑/校验/保存与无效输入、编辑模式切换、主题重建、大字号、订阅导入/身份诊断、系统文件选择器导出/恢复与删除确认。删除确认额外在素白/浅色/深色核对。输出 instrumentation-final-interactions.txt。
- 共9项不同界面测试最终通过。13项单元测试、assembleDebug、assembleDebugAndroidTest、lintDebug通过。初次目录遍历被同名入口/转场影响，未算作通过；已修正定位并增加实际路由校验，保留初次输出供追溯。

安装包：Clash-Android-0.2.13-dev-arm64.apk，versionCode15，debug签名，88845932字节。
SHA-256：aff2dfa098295225a74c6cb5e8864c4f7f9706f1ac4b77603c39d9bae4a4f69f

## 实际预览

Computer Use通过系统文件选择器导入保留的Clash-UI-Demo.yaml并保存，恢复16个直连测试节点/3个策略组的演示VPN；验证设置导航并打开“自动化与后台运行”，截图automation-live.png。该页保存计划、立即更新、系统电池、系统始终开启VPN和通知入口均为白色文字操作行；没有触发立即更新或修改Android系统设置。实时预览保留在此页。非VPN示例汇总见non-vpn-preview.png。
