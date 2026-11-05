package com.classics.readingroom.ui.components

import android.media.MediaPlayer
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.classics.readingroom.MainViewModel
import com.classics.readingroom.speech.RecorderController
import com.classics.readingroom.ui.Pill
import kotlin.math.roundToInt

@Composable
fun AppTopBar(title: String, subtitle: String? = null, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    CenterAlignedTopAppBar(
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.94f)
        )
    )
}

/** 朗读控制台：显示当前读到的段落、进度、语速与语气 */
@Composable
fun SpeechBar(vm: MainViewModel, modifier: Modifier = Modifier) {
    val tts = vm.tts
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(if (tts.speaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                )
                Column(Modifier.padding(start = 8.dp).weight(1f)) {
                    Text(
                        if (tts.speaking) tts.label.ifBlank { "正在朗读" } else "语音朗读",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        if (tts.speaking && tts.activeIndex >= 0) "第 ${tts.activeIndex + 1} 段" else "系统中文语音 · 可调语速与语气",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (tts.speaking) {
                    IconButton(onClick = { tts.togglePause() }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "重新播放当前段")
                    }
                    IconButton(onClick = { tts.stop() }) {
                        Icon(Icons.Filled.Stop, contentDescription = "停止")
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("语气", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                listOf("anchor" to "主持人", "recite" to "朗诵").forEach { (key, label) ->
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (vm.ttsStyleSetting() == key) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (vm.ttsStyleSetting() == key) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 6.dp).clickable { vm.setTtsStyle(key) }
                    ) {
                        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "${String.format("%.1f", vm.ttsRateSetting())}×",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Slider(
                value = vm.ttsRateSetting(),
                onValueChange = { vm.setTtsRate(it) },
                valueRange = 0.6f..1.8f,
                steps = 11
            )
            val message = tts.error.ifBlank { if (!tts.ready) "正在准备语音引擎…" else "" }
            if (message.isNotBlank()) {
                Text(message, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** 跟读录音卡片：录音 / 停止 / 回放 / 清除 */
@Composable
fun RecorderCard(recorder: RecorderController, onToggleRecord: () -> Unit) {
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            player?.release()
            player = null
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("跟读练习", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "戴上耳机，用自己的声音读一段；录音只保存在本机，不会上传。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Pill(if (recorder.recording) "录音中" else if (recorder.file != null) "已录制" else "待录音")
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (recorder.recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.clickable { onToggleRecord() }
                ) {
                    Text(
                        if (recorder.recording) "■ 停止录音 · ${formatTime(recorder.seconds)}" else "● 开始录音",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
                Row(
                    Modifier.weight(1f).padding(start = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(16) { index ->
                        Box(
                            Modifier
                                .width(3.dp)
                                .height(
                                    if (recorder.recording) (8 + recorder.amplitude * 30 * ((index % 5) + 1) / 2).dp
                                    else 6.dp
                                )
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = if (recorder.recording) 0.8f else 0.25f))
                        )
                    }
                }
            }

            val file = recorder.file
            if (file != null && !recorder.recording) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "已录制 ${formatTime(recorder.seconds)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (playing) "停止回放" else "▶ 回放我的朗读",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable {
                                if (playing) {
                                    player?.stop()
                                    player?.release()
                                    player = null
                                    playing = false
                                } else {
                                    player?.release()
                                    player = MediaPlayer().apply {
                                        setDataSource(file.absolutePath)
                                        prepare()
                                        setOnCompletionListener {
                                            playing = false
                                        }
                                        start()
                                    }
                                    playing = true
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    Text(
                        "清除",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable {
                                player?.release()
                                player = null
                                playing = false
                                recorder.clear()
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            if (recorder.error.isNotBlank()) {
                Text(recorder.error, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun formatTime(value: Int): String {
    val m = (value / 60).toString().padStart(2, '0')
    val s = (value % 60).toString().padStart(2, '0')
    return "$m:$s"
}

@Composable
fun SpeakingParagraph(text: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else androidx.compose.ui.graphics.Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    )
}

@Composable
fun LectureHead(title: String, tone: String, minutes: Int, onPlay: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("每日精读 · 演讲书稿", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.headlineSmall, fontSize = 19.sp)
            Text(
                buildString {
                    if (tone.isNotBlank()) append("语气：").append(tone)
                    if (minutes > 0) append(" · 预计 ").append(minutes).append(" 分钟")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (onPlay != null) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.clickable(onClick = onPlay)
            ) {
                Text("▶ 从头播放", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }
    }
}

@Composable
fun ProgressLine(value: Float) {
    LinearProgressIndicator(
        progress = { value },
        modifier = Modifier.fillMaxWidth().height(3.dp)
    )
}

fun minutesText(minutes: Int) = if (minutes > 0) "$minutes 分钟" else "—"

fun percentText(part: Int, total: Int): String =
    if (total <= 0) "0%" else "${(part * 100f / total).roundToInt()}%"

@Composable
fun ReadingText(text: String, fontSize: Int, serif: Boolean = true) {
    Text(
        text,
        fontFamily = if (serif) FontFamily.Serif else FontFamily.Default,
        fontSize = fontSize.sp,
        lineHeight = (fontSize * 1.75f).sp,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    )
}
