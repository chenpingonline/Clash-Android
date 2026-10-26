package cn.nascp.clash.ui

import androidx.compose.runtime.*
import cn.nascp.clash.data.ClashRepository
import kotlinx.coroutines.delay

/** Persist the latest edit even if Back cancels the debounce immediately. */
@Composable internal fun EditorDraft(repo:ClashRepository,key:String,value:String,dirty:Boolean) {
    val epoch=remember(key){repo.state.value.editorEpoch}
    val latestValue by rememberUpdatedState(value)
    val latestDirty by rememberUpdatedState(dirty)
    LaunchedEffect(key,value,dirty) {
        if(dirty){delay(500);if(repo.state.value.editorEpoch==epoch)repo.saveEditorDraft(key,value)}
    }
    DisposableEffect(key) {
        onDispose { if(latestDirty && repo.state.value.editorEpoch==epoch)repo.saveEditorDraft(key,latestValue) }
    }
    if(dirty)Info("未保存修改，返回时保留草稿。")
}
