# 子页面动作源码检查

- Secondary.kt: 无旧按钮直接调用；MainButton=12, SubpageButton=5, SubpageTextButton=4
- ManagementPages.kt: 无旧按钮直接调用；MainButton=8, SubpageButton=0, SubpageTextButton=6
- UtilityPages.kt: 无旧按钮直接调用；MainButton=16, SubpageButton=3, SubpageTextButton=8
- SubscriptionFields.kt: 无旧按钮直接调用；MainButton=1, SubpageButton=0, SubpageTextButton=2
- FontScaleControl.kt: 无旧按钮直接调用；MainButton=0, SubpageButton=0, SubpageTextButton=1
- Feedback.kt: 无旧按钮直接调用；MainButton=0, SubpageButton=2, SubpageTextButton=0
- CodeEditor.kt: 无旧按钮直接调用；MainButton=0, SubpageButton=0, SubpageTextButton=0
- PlainSettingsFields.kt: 无旧按钮直接调用；MainButton=0, SubpageButton=0, SubpageTextButton=0

全部操作经 PlainActionSurface，素白下白底蓝字/禁用灰字/危险红字；原 Material 按钮仅存在共享组件的其他主题回退分支。
编辑器切换在素白下使用同一组件，FilterChip 仅保留于其他主题。顶部导航、开关和持久模式选择器保留相应控件类型。
