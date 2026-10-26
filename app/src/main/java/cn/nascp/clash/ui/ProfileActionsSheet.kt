package cn.nascp.clash.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

@Composable internal fun AppBottomSheet(title:String,onDismiss:()->Unit,content:@Composable ColumnScope.()->Unit) {
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        val view=LocalView.current
        SideEffect { (view.parent as? DialogWindowProvider)?.window?.setDimAmount(.24f) }
        BoxWithConstraints(Modifier.fillMaxSize(),contentAlignment=Alignment.BottomCenter) {
            Box(Modifier.matchParentSize().clickable(interactionSource=remember{MutableInteractionSource()},indication=null,onClick=onDismiss))
            Surface(Modifier.padding(horizontal=16.dp,vertical=12.dp).widthIn(max=560.dp).fillMaxWidth().heightIn(max=maxHeight*.85f),
                shape=RoundedCornerShape(32.dp),color=MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(horizontal=24.dp,vertical=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    UiText(title,Modifier.fillMaxWidth().padding(top=4.dp,bottom=12.dp),style=MaterialTheme.typography.titleLarge.copy(fontSize=20.sp),fontWeight=FontWeight.SemiBold,textAlign=TextAlign.Center)
                    Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()),content=content)
                    Surface(onClick=onDismiss,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surfaceVariant) {
                        Box(Modifier.heightIn(min=56.dp).padding(12.dp),contentAlignment=Alignment.Center) { UiText("取消",style=MaterialTheme.typography.titleMedium.copy(fontSize=18.sp),fontWeight=FontWeight.Medium) }
                    }
                }
            }
        }
    }
}

@Composable internal fun ProfileSheetAction(label:String,tag:String,enabled:Boolean,onClick:()->Unit) {
    Box(Modifier.fillMaxWidth().testTag(tag).clickable(enabled=enabled,role=Role.Button,onClick=onClick).heightIn(min=60.dp).padding(vertical=12.dp),contentAlignment=Alignment.CenterStart) {
        UiText(label,style=MaterialTheme.typography.bodyLarge.copy(fontSize=18.sp),fontWeight=FontWeight.Medium,
            color=if(enabled)MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.5f))
    }
}
