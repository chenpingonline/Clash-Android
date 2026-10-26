package cn.nascp.clash.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// A settings row has one editable value, rather than a second card inside its group.
internal val LocalPlainSubpage = staticCompositionLocalOf { false }
internal val LocalPlainSettingGroup = staticCompositionLocalOf { false }

@Composable internal fun PlainSettingGroup(content: @Composable ColumnScope.() -> Unit) {
    CompositionLocalProvider(LocalPlainSettingGroup provides true) { PlainGroup(content) }
}

@Composable internal fun PlainSettingField(label: String, value: String, secret: Boolean = false,
                                         multiline: Boolean = false, onChange: (String) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    val numericError = numericFieldError(label,value)
    val large = LocalDensity.current.fontScale > 1.2f
    val description = tr(label)
    val inline = !multiline && !large
    @Composable fun Content() {
        BasicTextField(value, onChange, Modifier.fillMaxWidth().testTag("setting-field:$label")
            .semantics { contentDescription = description }, singleLine = !multiline,
            keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=if(isNumericField(label))androidx.compose.ui.text.input.KeyboardType.Number else androidx.compose.ui.text.input.KeyboardType.Text),
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp,
                color = MaterialTheme.colorScheme.onSurface, textAlign = if (inline) TextAlign.End else TextAlign.Start),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = if (secret && !visible) PasswordVisualTransformation() else VisualTransformation.None,
            decorationBox = { input ->
                @Composable fun Value() {
                    Box(Modifier.fillMaxWidth().heightIn(min = if (multiline) 48.dp else 24.dp),
                        contentAlignment = if (inline) Alignment.CenterEnd else Alignment.CenterStart) {
                        if (value.isEmpty()) UiText("未设置", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        input()
                    }
                }
                if (inline) {
                    Row(Modifier.heightIn(min = 56.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        PlainIcon(settingIcon(label))
                        UiText(label, Modifier.weight(1.1f), style = MaterialTheme.typography.titleMedium)
                        Box(Modifier.weight(1f)) { Value() }
                    }
                } else {
                    Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            PlainIcon(settingIcon(label))
                            UiText(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        }
                        Box(Modifier.fillMaxWidth().padding(start = 42.dp, bottom = 4.dp)) { Value() }
                    }
                }
            })
    }
    @Composable fun FieldContent() {
        Content()
        if(secret) TextButton(onClick={visible=!visible}) { UiText(if(visible)"隐藏内容" else "显示内容") }
        if(numericError!=null)UiText(numericError,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
    }
    if (LocalPlainSettingGroup.current) FieldContent() else PlainSettingGroup { FieldContent() }
}

@Composable internal fun PlainSettingToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    @Composable fun Content() {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .toggleable(checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PlainIcon(settingIcon(label))
            UiText(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Switch(checked, onCheckedChange = null, colors = SwitchDefaults.colors(
                uncheckedBorderColor = Color.Transparent, uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline))
        }
    }
    if (LocalPlainSettingGroup.current) Content() else PlainSettingGroup { Content() }
}

internal fun isNumericField(label:String)= label.contains("端口") || label.startsWith("MTU") || label.contains("分钟") || label.contains("超时")
internal fun numericFieldError(label:String,value:String):String? {
    if(!isNumericField(label))return null
    val range=when { label.startsWith("MTU")->1280L..9000L;label.contains("超时")->1000L..15000L;label.contains("分钟")->15L..Long.MAX_VALUE;label=="端口"->1L..65535L;else->0L..65535L }
    if(value.isBlank() && label.contains("端口"))return null
    val number=value.toLongOrNull()
    return if(number!=null && number in range)null else when {
        label.startsWith("MTU")->"请输入1280–9000之间的整数"
        label.contains("超时")->"测速超时需为 1000–15000 ms"
        label.contains("分钟")->"更新间隔至少为15分钟"
        label=="端口"->"端口需为1–65535之间的整数"
        else->"端口需为0–65535之间的整数（0为关闭）"
    }
}
