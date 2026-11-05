package com.classics.readingroom.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.classics.readingroom.data.Book
import com.classics.readingroom.data.CategoryNode
import com.classics.readingroom.data.Dossier

/* ---------------- 书封 ---------------- */

private val coverPalettes = mapOf(
    "古典小说" to (Color(0xFFB4472F) to Color(0xFFF3D9C4)),
    "诗词曲赋" to (Color(0xFF2F6B5C) to Color(0xFFD6E8DF)),
    "文史哲" to (Color(0xFF3F5A7A) to Color(0xFFD9E3EF)),
    "近现代文学" to (Color(0xFF9A6A2F) to Color(0xFFF2E2C6)),
    "外国文学" to (Color(0xFF6A5A8C) to Color(0xFFE4DEF0)),
    "科普新知" to (Color(0xFF2E6E7A) to Color(0xFFD5E9EC))
)

@Composable
fun BookCover(book: Book, size: String = "md", modifier: Modifier = Modifier) {
    val (dark, light) = coverPalettes[book.category] ?: (Color(0xFF54483C) to Color(0xFFC3A67B))
    val width = when (size) {
        "xs" -> 40.dp
        "sm" -> 64.dp
        "lg" -> 108.dp
        else -> 80.dp
    }
    val height = width * 1.45f
    val titleSize = when (size) {
        "xs" -> 9.sp
        "sm" -> 11.sp
        "lg" -> 17.sp
        else -> 13.sp
    }
    Box(
        modifier = modifier
            .size(width, height)
            .clip(RoundedCornerShape(4.dp))
            .background(Brush.linearGradient(listOf(dark, dark.copy(alpha = 0.9f), dark)))
            .border(0.5.dp, light.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
    ) {
        Box(
            Modifier
                .padding(start = width * 0.12f, top = 6.dp, end = 6.dp, bottom = 6.dp)
                .width(1.dp)
                .height(height * 0.82f)
                .background(light.copy(alpha = 0.6f))
        )
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = width * 0.2f, top = 8.dp, end = 6.dp, bottom = 6.dp)
        ) {
            if (size != "xs") {
                Text(
                    book.level,
                    color = light.copy(alpha = 0.95f),
                    fontSize = if (size == "lg") 10.sp else 8.sp,
                    maxLines = 1
                )
                Spacer(Modifier.height(if (size == "lg") 8.dp else 4.dp))
            }
            Text(
                book.title,
                color = Color(0xFFFFFDF8),
                fontSize = titleSize,
                lineHeight = titleSize * 1.25f,
                fontWeight = FontWeight.Bold,
                maxLines = if (size == "lg") 4 else 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.weight(1f))
            if (size != "xs") {
                Text(
                    book.author,
                    color = light.copy(alpha = 0.9f),
                    fontSize = if (size == "lg") 11.sp else 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (size != "xs") {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(5.dp)
                    .size(if (size == "lg") 20.dp else 14.dp)
                    .border(0.8.dp, light.copy(alpha = 0.8f), RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("典", color = light.copy(alpha = 0.9f), fontSize = if (size == "lg") 11.sp else 8.sp)
            }
        }
    }
}

/* ---------------- 标签与提示 ---------------- */

@Composable
fun Tag(text: String, modifier: Modifier = Modifier, highlighted: Boolean = false) {
    Box(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    // 卡片/列表内使用的标题行，modifier 保持可选
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(3.dp)
                .height(18.dp)
                .background(Cinnabar, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (subtitle != null) {
            Spacer(Modifier.width(8.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun CardBox(
    onClick: (() -> Unit)? = null,
    padding: Dp = 14.dp,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val base = modifier
        .fillMaxWidth()
        .shadow(1.dp, RoundedCornerShape(CardRadius), clip = false)
        .clip(RoundedCornerShape(CardRadius))
        .background(MaterialTheme.colorScheme.surface)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(CardRadius))
    val clickable = if (onClick != null) base.clickable { onClick() } else base
    Column(clickable.padding(padding), content = content)
}

@Composable
fun EmptyState(title: String, hint: String? = null, action: (@Composable () -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (hint != null) {
            Spacer(Modifier.height(8.dp))
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(64.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = if (primary) colors.primary else colors.surfaceVariant,
        contentColor = if (primary) colors.onPrimary else colors.onSurface,
        border = if (primary) null else BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
            maxLines = 1
        )
    }
}

/** 阅读中需要在朗读时高亮的段落 */
@Composable
fun HighlightText(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    align: TextAlign = TextAlign.Start
) {
    val background = if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else Color.Transparent
    Text(
        text,
        style = style,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 4.dp),
        textAlign = align
    )
}

fun categoryLabel(node: CategoryNode) = "${node.name}（${node.count}）"

fun lectureMinutesLabel(dossier: Dossier): String {
    val minutes = if (dossier.lecture.durationMinutes > 0) dossier.lecture.durationMinutes else 0
    return if (minutes > 0) "讲稿 $minutes 分钟" else "讲稿"
}

@Composable
fun HorizontalDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
fun QuickRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}
