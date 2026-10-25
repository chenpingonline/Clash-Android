package cn.nascp.clash.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private class CodeColors(private val key:Color,private val comment:Color):VisualTransformation {
 override fun filter(text:AnnotatedString):TransformedText {
  val styled=AnnotatedString.Builder(text.text)
  Regex("(?m)^\\s*[A-Za-z0-9_-]+(?=:) |\\b(function|return|const|let|var|if|else|true|false|null)\\b",RegexOption.COMMENTS).findAll(text.text).forEach{styled.addStyle(SpanStyle(color=key),it.range.first,it.range.last+1)}
  Regex("(?m)^\\s*(#|//).*$").findAll(text.text).forEach{styled.addStyle(SpanStyle(color=comment),it.range.first,it.range.last+1)}
  return TransformedText(styled.toAnnotatedString(),OffsetMapping.Identity)
 }
}
@Composable fun CodeEditor(value:String,onChange:(String)->Unit,readOnly:Boolean=false,label:String="YAML"){
 val typography=MaterialTheme.typography.bodyMedium.copy(fontFamily=FontFamily.Monospace,fontSize=14.sp,lineHeight=21.sp)
 UiText(label,style=MaterialTheme.typography.labelLarge)
 BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min=220.dp,max=480.dp).border(1.dp,MaterialTheme.colorScheme.outlineVariant,RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface,RoundedCornerShape(18.dp))){
  val width=maxWidth
  Row(Modifier.verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(12.dp)){
   UiText((1..value.lines().size).joinToString("\n"),Modifier.width(42.dp),color=MaterialTheme.colorScheme.onSurfaceVariant,style=typography)
   BasicTextField(value,onChange,Modifier.widthIn(min=width-74.dp),readOnly=readOnly,textStyle=typography.copy(color=MaterialTheme.colorScheme.onSurface),visualTransformation=CodeColors(MaterialTheme.colorScheme.primary,MaterialTheme.colorScheme.onSurfaceVariant),cursorBrush=androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary))
  }
 }
}
