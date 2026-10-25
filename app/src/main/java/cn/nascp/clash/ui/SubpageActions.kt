package cn.nascp.clash.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One presentation for every plain subpage action, including disabled and destructive states. */
@Composable internal fun PlainActionSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
    minHeight: Dp = 48.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val color = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface, contentColor = color,
        modifier = modifier.testTag("plain-subpage-action")) {
        ProvideTextStyle(MaterialTheme.typography.titleMedium) {
            Row(Modifier.heightIn(min = minHeight).widthIn(min = 48.dp).padding(contentPadding),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, content = content)
        }
    }
}

@Composable internal fun SubpageButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    outlined: Boolean = false,
    destructive: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    if (LocalPlainSubpage.current) {
        PlainActionSurface(onClick, modifier, enabled, destructive, content = content)
    } else if (outlined) {
        OutlinedButton(onClick, modifier, enabled, content = content)
    } else {
        Button(onClick, modifier, enabled,
            colors = if (destructive) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError) else ButtonDefaults.buttonColors(), content = content)
    }
}

@Composable internal fun SubpageTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    if (LocalPlainSubpage.current) {
        PlainActionSurface(onClick, modifier, enabled, contentPadding = contentPadding, content = content)
    } else {
        TextButton(onClick, modifier, enabled, contentPadding = contentPadding, content = content)
    }
}
