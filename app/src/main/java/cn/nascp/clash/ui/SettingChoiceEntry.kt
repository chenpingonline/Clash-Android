package cn.nascp.clash.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

/** A compact settings row with a bottom-aligned, single-choice dialog. */
@Composable internal fun SettingChoiceEntry(
    title:String, options:List<Pair<String,String>>, selected:String, onSelect:(String)->Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Entry(title,options.firstOrNull{it.first==selected}?.second.orEmpty()){open=true}
    if(open) {
        Dialog(onDismissRequest={open=false},properties=DialogProperties(usePlatformDefaultWidth=false)) {
            val view=LocalView.current
            SideEffect { (view.parent as? DialogWindowProvider)?.window?.setDimAmount(.24f) }
            BoxWithConstraints(Modifier.fillMaxSize(),contentAlignment=Alignment.BottomCenter) {
                Box(Modifier.matchParentSize().clickable(
                    interactionSource=remember{MutableInteractionSource()},indication=null,
                    onClick={open=false}))
                Surface(Modifier.padding(horizontal=16.dp,vertical=12.dp).widthIn(max=560.dp)
                    .fillMaxWidth().heightIn(max=maxHeight*.85f),
                    shape=RoundedCornerShape(32.dp),color=MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(horizontal=24.dp,vertical=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        UiText(title,Modifier.fillMaxWidth().padding(vertical=4.dp),
                            style=MaterialTheme.typography.titleLarge,textAlign=TextAlign.Center)
                        Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()).selectableGroup()) {
                            options.forEach { (value,label) ->
                                val checked=value==selected
                                Row(Modifier.fillMaxWidth().selectable(selected=checked,role=Role.RadioButton,
                                    onClick={onSelect(value);open=false}).heightIn(min=60.dp).padding(vertical=12.dp),
                                    verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                                    UiText(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge,
                                        color=if(checked)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                    if(checked)Icon(Icons.Outlined.Check,contentDescription=null,
                                        tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(24.dp))
                                }
                            }
                        }
                        Surface(onClick={open=false},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),
                            color=MaterialTheme.colorScheme.surfaceVariant,contentColor=MaterialTheme.colorScheme.onSurface) {
                            Box(Modifier.heightIn(min=52.dp).padding(12.dp),contentAlignment=Alignment.Center) {
                                UiText("取消",style=MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
