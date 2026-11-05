package com.classics.readingroom.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.classics.readingroom.speech.SpeechPlayer

/** 朗读控制条：播放/暂停、语速、语气、音色 */
@Composable
fun SpeechPanel(speech: SpeechPlayer, modifier: Modifier = Modifier) {
    var voiceMenu by remember { mutableStateOf(false) }
    var styleMenu by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = colors.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (speech.speaking && !speech.paused) colors.primary else colors.outline)
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (speech.speaking) (speech.label.ifBlank { "正在朗读" }) else "语音朗读",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        when {
                            speech.speaking && speech.paused -> "已暂停，点播放继续"
                            speech.speaking -> speech.activeText
                            else -> "系统合成语音，可调语速与语气"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                if (speech.speaking) {
                    TextButton(onClick = { if (speech.paused) speech.resume() else speech.pause() }) {
                        Text(if (speech.paused) "继续" else "暂停")
                    }
                    TextButton(onClick = { speech.stop() }) { Text("停止") }
                }
            }

            if (speech.speaking) {
                Spacer(Modifier.height(6.dp))
                androidx.compose.material3.LinearProgressIndicator(
                    progress = speech.progress,
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = colors.primary,
                    trackColor = colors.surfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("语速 ${"%.1f".format(speech.rate)}×", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = speech.rate,
                        onValueChange = { value -> speech.changeRate(kotlin.math.round(value * 10f) / 10f) },
                        valueRange = 0.6f..1.8f,
                        steps = 11,
                        modifier = Modifier.height(28.dp)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                StylePicker(
                    label = if (speech.style == SpeechPlayer.STYLE_RECITE) "朗诵（舒缓）" else "主持人（平稳）",
                    expanded = styleMenu,
                    onExpandedChange = { styleMenu = it },
                    options = listOf(
                        SpeechPlayer.STYLE_ANCHOR to "主持人（平稳）",
                        SpeechPlayer.STYLE_RECITE to "朗诵（舒缓）"
                    ),
                    selected = speech.style,
                    onSelected = { speech.changeStyle(it) }
                )
                if (speech.voices.isNotEmpty()) {
                    StylePicker(
                        label = speech.voices.firstOrNull { it.first == speech.voiceName }?.second ?: "默认音色",
                        expanded = voiceMenu,
                        onExpandedChange = { voiceMenu = it },
                        options = speech.voices.take(8),
                        selected = speech.voiceName,
                    onSelected = { speech.changeVoice(it) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (speech.error.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(speech.error, style = MaterialTheme.typography.bodySmall, color = colors.error)
            }
        }
    }
}

@Composable
private fun StylePicker(
    label: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    options: List<Pair<String, String>>,
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.layout.Box(modifier) {
        OutlinedButton(
            onClick = { onExpandedChange(true) },
            shape = RoundedCornerShape(999.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 3.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text,
                            style = if (value == selected) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium
                        )
                    },
                    onClick = {
                        onSelected(value)
                        onExpandedChange(false)
                    }
                )
            }
        }
    }
}
