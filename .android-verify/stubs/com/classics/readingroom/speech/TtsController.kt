package com.classics.readingroom.speech

import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale
import java.util.UUID

/**
 * 系统中文语音朗读。两档语气：
 *  - 主持人（anchor）：平稳、清楚，适合讲稿
 *  - 朗诵（recite）：速度略慢、音调略低，适合精华章句与原书
 */
class TtsController(context: Context) : TextToSpeech.OnInitListener {

    private val tts = TextToSpeech(context.applicationContext, this)

    var ready by mutableStateOf(false)
        private set
    var speaking by mutableStateOf(false)
        private set
    var activeIndex by mutableStateOf(-1)
        private set
    var label by mutableStateOf("")
        private set
    var error by mutableStateOf("")
        private set

    var voices: List<TextToSpeech.EngineInfo> = emptyList()
        private set
    var engineLabel by mutableStateOf("系统语音")
        private set

    private var segments: List<String> = emptyList()
    private var cursor = 0
    private var rate = 1f
    private var style = "anchor"
    private var currentUtterance = ""

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            error = "语音引擎初始化失败，请在系统设置中安装语音服务"
            ready = false
            return
        }
        voiceSetup()
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (utteranceId != currentUtterance) return
                speaking = true
                activeIndex = cursor
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId != currentUtterance) return
                cursor += 1
                if (cursor >= segments.size) {
                    stopInternal()
                } else {
                    speakCurrent()
                }
            }

            override fun onError(utteranceId: String?) {
                if (utteranceId != currentUtterance) return
                error = "语音播放失败，可尝试在系统设置中更换语音"
                stopInternal()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                if (utteranceId != currentUtterance) return
                error = "语音播放失败（${describeError(errorCode)}）"
                stopInternal()
            }
        })
        ready = true
    }

    private fun voiceSetup() {
        val result = tts.setLanguage(Locale.SIMPLIFIED_CHINESE)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            error = "系统缺少中文语音，请在“设置 → 语言与输入 → 文字转语音”中安装中文语音包"
        }
        runCatching {
            voices = tts.engines
            engineLabel = voices.firstOrNull { it.name == tts.defaultEngine }?.label ?: "系统语音"
        }
    }

    private fun describeError(code: Int): String = when (code) {
        TextToSpeech.ERROR_NETWORK -> "需要联网下载语音"
        TextToSpeech.ERROR_NETWORK_TIMEOUT -> "语音下载超时"
        TextToSpeech.ERROR_NOT_INSTALLED_YET -> "语音尚未安装"
        TextToSpeech.ERROR_OUTPUT -> "音频输出异常"
        else -> "错误码 $code"
    }

    fun setRate(value: Float) {
        rate = value.coerceIn(0.5f, 2f)
        if (speaking) restartFrom(activeIndex.coerceAtLeast(0))
    }

    fun setStyle(value: String) {
        style = value
        if (speaking) restartFrom(activeIndex.coerceAtLeast(0))
    }

    fun switchEngine(engineName: String) {
        stop()
        runCatching {
            tts.engine = engineName
            engineLabel = voices.firstOrNull { it.name == engineName }?.label ?: engineName
        }
    }

    fun play(texts: List<String>, startAt: Int = 0, labelText: String = "正在朗读") {
        if (!ready) {
            error = "语音引擎正在准备，请稍后再试"
            return
        }
        val clean = texts.map { it.trim() }.filter { it.isNotEmpty() }
        if (clean.isEmpty()) {
            error = "暂无可朗读的内容"
            return
        }
        segments = clean
        cursor = startAt.coerceIn(0, clean.lastIndex)
        label = labelText
        error = ""
        speakCurrent()
    }

    fun togglePause() {
        if (!speaking) return
        if (activeIndex >= 0) restartFrom(activeIndex)
    }

    private fun restartFrom(index: Int) {
        val texts = segments
        val currentLabel = label
        stop()
        play(texts, index, currentLabel)
    }

    private fun speakCurrent() {
        val text = segments.getOrNull(cursor) ?: return run { stopInternal() }
        val reciting = style == "recite"
        val spoken = if (reciting && text.trimEnd().lastOrNull()?.toString()?.matches(Regex("[。！？…]")) == false) "$text。" else text
        val speed = rate * (if (reciting) 0.86f else 1f)
        val pitch = if (reciting) 0.92f else 1.02f
        currentUtterance = UUID.randomUUID().toString()
        tts.setSpeechRate(speed)
        tts.setPitch(pitch)
        speaking = true
        activeIndex = cursor
        val queueMode = TextToSpeech.QUEUE_FLUSH
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(spoken, queueMode, null, currentUtterance)
        } else {
            @Suppress("DEPRECATION")
            val params = HashMap<String, String>().apply { put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, currentUtterance) }
            @Suppress("DEPRECATION")
            tts.speak(spoken, queueMode, params)
        }
    }

    fun stop() {
        currentUtterance = ""
        runCatching { tts.stop() }
        stopInternal()
    }

    private fun stopInternal() {
        speaking = false
        activeIndex = -1
        segments = emptyList()
        label = ""
    }

    fun release() {
        currentUtterance = ""
        runCatching { tts.stop() }
        runCatching { tts.shutdown() }
    }
}
