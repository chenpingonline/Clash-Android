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

// A settings row has one editable value, rather than a second card inside its group.
internal val LocalPlainSubpage = staticCompositionLocalOf { false }
internal val LocalPlainSettingGroup = staticCompositionLocalOf { false }

@Composable internal fun PlainSettingGroup(content: @Composable ColumnScope.() -> Unit) {
    CompositionLocalProvider(LocalPlainSettingGroup provides true) { PlainGroup(content) }
}

@Composable internal fun PlainSettingField(label: String, value: String, secret: Boolean = false,
                                         multiline: Boolean = false, onChange: (String) -> Unit) {
    val large = LocalDensity.current.fontScale > 1.2f
    val description = tr(label)
    val inline = !multiline && !large
    @Composable fun Content() {
        BasicTextField(value, onChange, Modifier.fillMaxWidth().testTag("setting-field:$label")
            .semantics { contentDescription = description }, singleLine = !multiline,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface, textAlign = if (inline) TextAlign.End else TextAlign.Start),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
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
                    Row(Modifier.heightIn(min = 54.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PlainIcon(settingIcon(label))
                        UiText(label, Modifier.weight(1.1f), style = MaterialTheme.typography.titleMedium)
                        Box(Modifier.weight(1f)) { Value() }
                    }
                } else {
                    Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PlainIcon(settingIcon(label))
                            UiText(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        }
                        Box(Modifier.fillMaxWidth().padding(start = 44.dp, bottom = 4.dp)) { Value() }
                    }
                }
            })
    }
    if (LocalPlainSettingGroup.current) Content() else PlainSettingGroup { Content() }
}

@Composable internal fun PlainSettingToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    @Composable fun Content() {
        Row(Modifier.fillMaxWidth().heightIn(min = 54.dp)
            .toggleable(checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PlainIcon(settingIcon(label))
            UiText(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Switch(checked, onCheckedChange = null, colors = SwitchDefaults.colors(
                uncheckedBorderColor = Color.Transparent, uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline))
        }
    }
    if (LocalPlainSettingGroup.current) Content() else PlainSettingGroup { Content() }
}
