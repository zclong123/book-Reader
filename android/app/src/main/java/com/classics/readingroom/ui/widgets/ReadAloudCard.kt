package com.classics.readingroom.ui.widgets

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.classics.readingroom.speech.Recorder

private fun formatTime(seconds: Int): String =
    "%02d:%02d".format(seconds / 60, seconds % 60)

private fun hasAudioPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

private fun shareRecording(context: Context, recorder: Recorder) {
    val file = recorder.outputFile() ?: return
    val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "audio/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "保存或分享录音"))
}

/** 跟读练习：录音、停止、回放自己的声音、保存 */
@Composable
fun ReadAloudCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val recorder = remember { Recorder(context) }
    var permissionDenied by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { recorder.release() }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            recorder.start()
        } else {
            permissionDenied = true
        }
    }

    fun startOrCheck() {
        if (hasAudioPermission(context)) {
            recorder.start()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val colors = MaterialTheme.colorScheme

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("跟读练习", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "戴上耳机，用自己的声音读一段；录音只保存在本机。",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (recorder.recording) colors.primaryContainer else colors.surfaceVariant)
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(
                        when {
                            recorder.recording -> "录音中"
                            recorder.lastFile != null -> "已录制"
                            else -> "待录音"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (recorder.recording) colors.onPrimaryContainer else colors.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    onClick = {
                        when {
                            recorder.recording -> recorder.stop()
                            recorder.lastFile != null -> {
                                recorder.clear()
                                startOrCheck()
                            }
                            else -> startOrCheck()
                        }
                    },
                    shape = CircleShape,
                    color = if (recorder.recording) colors.primary else colors.primaryContainer,
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (recorder.recording) {
                            Box(Modifier.size(18.dp).clip(RoundedCornerShape(3.dp)).background(colors.onPrimary))
                        } else {
                            Box(Modifier.size(22.dp).clip(CircleShape).background(colors.primary))
                        }
                    }
                }

                Column(Modifier.weight(1f)) {
                    Text(
                        when {
                            recorder.recording -> "停止录音 · ${formatTime(recorder.seconds)}"
                            recorder.lastFile != null -> "重新录制"
                            else -> "开始录音"
                        },
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                        repeat(18) { index ->
                            val h = if (recorder.recording) (6 + recorder.level * 30 * ((index % 5) + 1) / 2).dp else 4.dp
                            Box(
                                Modifier
                                    .width(4.dp)
                                    .height(h.coerceIn(4.dp, 30.dp))
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (recorder.recording) colors.primary else colors.outlineVariant)
                            )
                        }
                    }
                }
            }

            if (recorder.lastFile != null) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { recorder.playOrPause() }) {
                        Text(if (recorder.playing) "■ 停止回放" else "▶ 回放我的朗读")
                    }
                    TextButton(onClick = { shareRecording(context, recorder) }) { Text("保存录音") }
                    TextButton(onClick = { recorder.clear() }) { Text("清除") }
                }
            }

            val message = recorder.error.ifBlank { if (permissionDenied) "麦克风权限被拒绝，请在系统设置中允许录音。" else "" }
            if (message.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(message, style = MaterialTheme.typography.bodySmall, color = colors.error)
            }
        }
    }
}
