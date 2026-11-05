package com.classics.readingroom.speech

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/** 麦克风跟读：录下自己的朗读，保存在应用私有目录，可回放 */
class RecorderController(private val context: Context) {

    var recording by mutableStateOf(false)
        private set
    var seconds by mutableStateOf(0)
        private set
    var amplitude by mutableStateOf(0f)
        private set
    var file by mutableStateOf<File?>(null)
        private set
    var error by mutableStateOf("")
        private set

    private var recorder: MediaRecorder? = null
    private var thread: Thread? = null

    fun toggle() {
        if (recording) stop() else start()
    }

    fun start() {
        if (recording) return
        error = ""
        val target = File(context.filesDir, "recording-${System.currentTimeMillis()}.m4a")
        try {
            val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context)
            else @Suppress("DEPRECATION") MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(96_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(target.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            file = target
            recording = true
            seconds = 0
            amplitude = 0f
            thread = Thread {
                while (recording) {
                    runCatching {
                        val max = r.maxAmplitude
                        amplitude = (max / 32767f).coerceIn(0f, 1f)
                    }
                    Thread.sleep(100)
                    if (recording) seconds += 1
                }
            }.also { it.isDaemon = true; it.start() }
        } catch (e: Exception) {
            error = "无法开始录音：${e.message ?: "麦克风不可用"}"
            release()
        }
    }

    fun stop() {
        if (!recording) return
        recording = false
        amplitude = 0f
        runCatching { recorder?.stop() }
        release()
    }

    fun clear() {
        stop()
        file?.delete()
        file = null
        seconds = 0
        error = ""
    }

    private fun release() {
        runCatching { recorder?.release() }
        recorder = null
    }

    fun releaseAll() {
        if (recording) stop()
        release()
        thread = null
    }
}
