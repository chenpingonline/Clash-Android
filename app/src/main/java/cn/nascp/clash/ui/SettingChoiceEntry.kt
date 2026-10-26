package cn.nascp.clash.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A compact settings row with a bottom-aligned, single-choice dialog. */
@Composable internal fun SettingChoiceEntry(
    title:String, options:List<Pair<String,String>>, selected:String, onSelect:(String)->Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Entry(title,options.firstOrNull{it.first==selected}?.second.orEmpty()){open=true}
    if(open) AppBottomSheet(title,onDismiss={open=false}) {
        Column(Modifier.selectableGroup()) {
            options.forEach { (value,label) ->
                val checked=value==selected
                Row(Modifier.fillMaxWidth().selectable(selected=checked,role=Role.RadioButton,
                    onClick={onSelect(value);open=false}).heightIn(min=60.dp).padding(vertical=12.dp),
                    verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    UiText(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge.copy(fontSize=18.sp),fontWeight=FontWeight.Medium,
                        color=if(checked)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    if(checked)Icon(Icons.Outlined.Check,contentDescription=null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(24.dp))
                }
            }
        }
    }
}
