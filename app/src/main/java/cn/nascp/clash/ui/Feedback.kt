package cn.nascp.clash.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cn.nascp.clash.data.MessageKind

val LocalOperationBusy = staticCompositionLocalOf { false }

@Composable fun OperationNotice(text: String, kind: MessageKind, onDismiss: () -> Unit) {
    val isError=kind==MessageKind.ERROR
    Surface(
        modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=6.dp).semantics{liveRegion=LiveRegionMode.Polite},
        shape=RoundedCornerShape(18.dp),
        color=if(isError)MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
        contentColor=if(isError)MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(Modifier.padding(start=14.dp,top=4.dp,bottom=4.dp,end=2.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Icon(if(isError)Icons.Outlined.ErrorOutline else Icons.Outlined.Info,null,Modifier.size(20.dp))
            Box(Modifier.weight(1f).heightIn(max=160.dp).verticalScroll(rememberScrollState()).padding(vertical=10.dp)) {
                UiText(text,style=MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick=onDismiss){Icon(Icons.Outlined.Close,tr("关闭提示"))}
        }
    }
}

@Composable fun InlineConfirmation(
    title: String,
    text: String,
    confirmLabel: String,
    destructive: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val requester=remember{BringIntoViewRequester()}
    LaunchedEffect(Unit){withFrameNanos{};requester.bringIntoView()}
    Box(Modifier.fillMaxWidth().bringIntoViewRequester(requester)) {
        Panel {
            UiText(title,Modifier.semantics{heading()},style=MaterialTheme.typography.titleMedium,
                color=if(destructive)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            UiText(text,style=MaterialTheme.typography.bodyMedium)
            FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                SubpageButton(onClick=onDismiss,enabled=!LocalOperationBusy.current,outlined=true){UiText("取消")}
                SubpageButton(onClick=onConfirm,enabled=!LocalOperationBusy.current,destructive=destructive) {UiText(confirmLabel)}
            }
        }
    }
}
