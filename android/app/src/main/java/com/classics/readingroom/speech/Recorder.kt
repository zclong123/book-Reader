package com.classics.readingroom.speech

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/** 麦克风跟读：录制本机音频，录制后可回放或保存 */
class Recorder(private val context: Context) {

    var recording by mutableStateOf(false)
        private set
    var seconds by mutableStateOf(0)
        private set
    var level by mutableStateOf(0f)
        private set
    var playing by mutableStateOf(false)
        private set
    var lastFile by mutableStateOf<File?>(null)
        private set
    var error by mutableStateOf("")
        private set

    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var timer: android.os.Handler? = null
    private var currentFile: File? = null

    private var levelThread: Thread? = null
    private var levelStop = false

    fun start() {
        error = ""
        stopPlayback()
        if (Build.VERSION.SDK_INT >= 31) {
            tryStart { MediaRecorder(context) }
        } else {
            tryStart { @Suppress("DEPRECATION") MediaRecorder() }
        }
    }

    private fun tryStart(factory: () -> MediaRecorder) {
        try {
            val file = File(context.cacheDir, "read-aloud-${System.currentTimeMillis()}.m4a")
            val r = factory()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(44_100)
            r.setAudioEncodingBitRate(96_000)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            currentFile = file
            recording = true
            seconds = 0
            startTimer()
            startLevelMeter(r)
        } catch (e: Exception) {
            cleanup()
            error = "无法开始录音：${e.message ?: "请检查麦克风权限"}"
            recording = false
        }
    }

    private fun startTimer() {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        timer = handler
        handler.post(object : Runnable {
            override fun run() {
                if (!recording) return
                seconds += 1
                handler.postDelayed(this, 1000)
            }
        })
    }

    private fun startLevelMeter(r: MediaRecorder) {
        levelStop = false
        levelThread = Thread {
            while (!levelStop) {
                val amplitude = runCatching { amplitudeOf(r) }.getOrDefault(0)
                level = (amplitude / 12_000f).coerceIn(0f, 1f)
                try {
                    Thread.sleep(80)
                } catch (_: InterruptedException) {
                    break
                }
            }
            level = 0f
        }.also { it.isDaemon = true; it.start() }
    }

    @Suppress("DEPRECATION")
    private fun amplitudeOf(r: MediaRecorder): Int = r.maxAmplitude

    fun stop() {
        if (!recording) return
        recording = false
        levelStop = true
        timer?.removeCallbacksAndMessages(null)
        timer = null
        runCatching { recorder?.stop() }
            .onFailure {
                error = "录音时间太短，没有保存"
                currentFile?.delete()
                currentFile = null
            }
        runCatching { recorder?.release() }
        recorder = null
        if (currentFile != null) lastFile = currentFile
    }

    fun playOrPause() {
        if (playing) {
            stopPlayback()
            return
        }
        val file = lastFile ?: return
        error = ""
        runCatching {
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    playing = false
                    stopPlayback()
                }
                prepare()
                start()
            }
            playing = true
        }.onFailure {
            playing = false
            error = "无法播放录音：${it.message ?: "文件损坏"}"
        }
    }

    fun stopPlayback() {
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
        playing = false
    }

    fun clear() {
        stopPlayback()
        lastFile?.delete()
        lastFile = null
        seconds = 0
        error = ""
    }

    private fun cleanup() {
        levelStop = true
        runCatching { recorder?.release() }
        recorder = null
        recording = false
        timer?.removeCallbacksAndMessages(null)
        timer = null
    }

    fun release() {
        if (recording) stop()
        stopPlayback()
    }

    fun outputFile(): File? = lastFile
}
