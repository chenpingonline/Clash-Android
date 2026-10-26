package cn.nascp.clash.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

// Scoped to compact plain surfaces; other themes retain their established spacing.
internal val LocalPlainCompact = staticCompositionLocalOf { false }

@Composable fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Surface(contentColor = MaterialTheme.colorScheme.onSurface, shape = RoundedCornerShape(24.dp), color = if (LocalSageGlass.current || LocalPearlGlass.current) MaterialTheme.colorScheme.surface.copy(alpha = 0.76f) else MaterialTheme.colorScheme.surface, border = if (LocalPlain.current) null else BorderStroke(if (LocalSageGlass.current || LocalPearlGlass.current) 1.dp else 0.5.dp, if (LocalSageGlass.current || LocalPearlGlass.current) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
        androidx.compose.runtime.CompositionLocalProvider(LocalPlainSettingGroup provides LocalPlainSubpage.current) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(if (LocalPlainCompact.current) 6.dp else if (LocalPlain.current) 8.dp else 12.dp), content = content)
        }
    }
}
@Composable fun Info(text: String) {
    if (LocalPlainSubpage.current) {
        UiText(text, Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val compact = LocalPlainCompact.current
    val foreground = if (compact) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer
    Surface(shape = RoundedCornerShape(if (compact) 12.dp else 18.dp),
        color = if (compact) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(if (compact) 10.dp else 14.dp), horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)) {
            Icon(Icons.Outlined.Info, null, Modifier.size(18.dp), tint = foreground)
            UiText(text, Modifier.weight(1f), style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium, color = foreground)
        }
    }
}
@Composable fun Entry(title: String, subtitle: String = "", icon: ImageVector = settingIcon(title), onClick: () -> Unit) {
    if (LocalPlain.current) { PlainEntry(title, subtitle, icon, onClick = onClick); return }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
        Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            UiText(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle.isNotBlank()) UiText(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}
@Composable fun Choices(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    @Composable fun Item(value:String,label:String,modifier:Modifier) {
        val background by animateColorAsState(if(value==selected && (LocalPearlGlass.current || LocalPlain.current))MaterialTheme.colorScheme.surface else if(value==selected)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, tween(180), label="choiceBackground")
        val foreground by animateColorAsState(if(value==selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, tween(180), label="choiceForeground")
        Surface(selected=value==selected,onClick={onSelect(value)},shape=RoundedCornerShape(16.dp),color=background,modifier=modifier) {
            Box(Modifier.heightIn(min=if (LocalPlain.current) 40.dp else 48.dp).padding(8.dp),contentAlignment=Alignment.Center){
                UiText(label,color=foreground,style=MaterialTheme.typography.labelLarge,textAlign=TextAlign.Center)
            }
        }
    }
    val background=Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant,RoundedCornerShape(24.dp)).padding(4.dp)
    if(LocalDensity.current.fontScale>1.2f || options.size>4) {
        FlowRow(background,horizontalArrangement=Arrangement.spacedBy(4.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            options.forEach{(value,label)->Item(value,label,Modifier.widthIn(min=72.dp))}
        }
    }else Row(background,horizontalArrangement=Arrangement.spacedBy(4.dp)){
        options.forEach{(value,label)->Item(value,label,Modifier.weight(1f))}
    }
}
@Composable fun MainButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    if (LocalPlainSubpage.current) {
        PlainActionSurface(onClick, Modifier.fillMaxWidth(), enabled, minHeight = 56.dp,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) { UiText(label) }
        return
    }
    val modifier = Modifier.fillMaxWidth().heightIn(min = if (LocalPlainCompact.current) 48.dp else 50.dp)
    val shape = RoundedCornerShape(if (LocalPlainCompact.current) 12.dp else 16.dp)
    if (LocalPlain.current) {
        FilledTonalButton(onClick = onClick, enabled = enabled, shape = shape, modifier = modifier,
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) { UiText(label) }
    } else {
        Button(onClick = onClick, enabled = enabled, shape = shape, modifier = modifier) { UiText(label) }
    }
}
fun bytes(value: Long): String = when {
    value >= 1024 * 1024 * 1024 -> "%.1f GB".format(value / 1073741824.0)
    value >= 1024 * 1024 -> "%.1f MB".format(value / 1048576.0)
    value >= 1024 -> "%.1f KB".format(value / 1024.0)
    else -> "$value B"
}

@Composable fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    if (LocalPlainSubpage.current) { PlainSettingToggle(label, checked, onChange); return }
    val compact = LocalPlainCompact.current
    Surface(shape = RoundedCornerShape(if (compact) 12.dp else 18.dp), color = MaterialTheme.colorScheme.surface,
        border = if (compact) null else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .heightIn(min = 48.dp).padding(horizontal = if (compact) 4.dp else 16.dp, vertical = if (compact) 2.dp else 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            UiText(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

@Composable fun AppTextField(
    value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null, placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null, singleLine: Boolean = false,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    if (LocalPlainSubpage.current && label != null && singleLine) {
        // Import-name/address fields use the same borderless row vocabulary.
        Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            androidx.compose.foundation.text.BasicTextField(value, onValueChange, Modifier.fillMaxWidth().padding(14.dp),
                textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface), singleLine = true,
                visualTransformation = visualTransformation,
                decorationBox = { input -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    label()
                    Row(verticalAlignment=Alignment.CenterVertically) { Box(Modifier.weight(1f)){input()}; trailingIcon?.invoke() }
                } })
        }
        return
    }
    OutlinedTextField(value, onValueChange, modifier, label = label, placeholder = placeholder,
        leadingIcon = leadingIcon, trailingIcon = trailingIcon, singleLine = singleLine, visualTransformation = visualTransformation,
        textStyle = textStyle, shape = RoundedCornerShape(if (LocalPlainCompact.current) 12.dp else 16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant))
}

@Composable fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange)
        .padding(vertical = if (LocalPlainCompact.current) 2.dp else 8.dp, horizontal = 4.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Checkbox(checked = checked, onCheckedChange = null)
        UiText(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable internal fun GlobalExtensions(go: (String) -> Unit) {
    Panel {
        Entry("全局覆写", "为所有配置统一修改指定设置", Icons.Outlined.Tune) { go("global-override") }
        Entry("全局扩展脚本", "用 JavaScript 处理所有配置", Icons.Outlined.Code) { go("global-script") }
    }
}
