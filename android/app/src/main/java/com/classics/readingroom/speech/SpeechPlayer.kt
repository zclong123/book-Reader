package com.classics.readingroom.speech

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * 系统语音合成朗读：主持人（平稳）与朗诵（舒缓）两种语气。
 * 长文本按标点切成片段，片段与界面上的段落一一对应，便于高亮当前段落。
 */
class SpeechPlayer(private val context: Context) {

    var supported by mutableStateOf(false)
        private set
    var ready by mutableStateOf(false)
        private set
    var speaking by mutableStateOf(false)
        private set
    var paused by mutableStateOf(false)
        private set
    var activeIndex by mutableStateOf(-1)
        private set
    var activeText by mutableStateOf("")
        private set
    var label by mutableStateOf("")
        private set
    var error by mutableStateOf("")
        private set
    var rate by mutableStateOf(1f)
        private set
    var style by mutableStateOf(STYLE_ANCHOR)
        private set
    var voices by mutableStateOf<List<Pair<String, String>>>(emptyList())
        private set
    var voiceName by mutableStateOf("")
        private set

    private var tts: TextToSpeech? = null
    private var segments: List<String> = emptyList()
    private var indexToSegment = IntArray(0)
    private var queue = ArrayList<Pair<Int, String>>()
    private var position = 0
    private var generation = 0
    private var pendingStart: (() -> Unit)? = null

    init {
        supported = runCatching {
            tts = TextToSpeech(context.applicationContext) { status ->
                ready = status == TextToSpeech.SUCCESS
                if (!ready) error = "当前设备没有可用的语音合成引擎"
                tts?.language = Locale.SIMPLIFIED_CHINESE
                refreshVoices()
                pendingStart?.invoke()
                pendingStart = null
            }
            true
        }.getOrDefault(false)
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                val gen = utteranceId?.substringBefore('-')?.toIntOrNull() ?: return
                if (gen != generation) return
                val pos = utteranceId?.substringAfter('-')?.toIntOrNull() ?: return
                val segment = queue.getOrNull(pos) ?: return
                activeIndex = segment.first
                activeText = segment.second
                speaking = true
                paused = false
            }

            override fun onDone(utteranceId: String?) {
                val gen = utteranceId?.substringBefore('-')?.toIntOrNull() ?: return
                if (gen != generation) return
                val pos = utteranceId?.substringAfter('-')?.toIntOrNull() ?: return
                if (pos >= queue.lastIndex) finish()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                val gen = utteranceId?.substringBefore('-')?.toIntOrNull()
                if (gen != null && gen != generation) return
                error = "语音播放失败，请检查系统的语音输出设置"
                finish()
            }

            @Deprecated("Deprecated in Java")
            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                // 手动停止或切换时不需要额外处理
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                onError(utteranceId)
            }
        })
    }

    private fun refreshVoices() {
        val engine = tts ?: return
        val list = runCatching {
            engine.voices
                ?.filter { it.locale.language == "zh" }
                ?.map { it.name to shortName(it.name, it.locale.country) }
                ?.sortedBy { it.first }
        }.getOrNull().orEmpty()
        voices = list
        if (voiceName.isBlank() && list.isNotEmpty()) voiceName = list.first().first
    }

    private fun shortName(name: String, country: String): String {
        val label = when (country) {
            "CN" -> "普通话"
            "TW" -> "台湾"
            "HK" -> "粤语"
            else -> country
        }
        return "$label · ${name.substringAfterLast('#').take(18)}"
    }

    private fun applyVoice(engine: TextToSpeech) {
        if (voiceName.isBlank()) return
        runCatching {
            engine.voices?.firstOrNull { it.name == voiceName }?.let { engine.voice = it }
        }
    }

    fun play(texts: List<String>, indices: IntArray, newLabel: String, start: Int = 0) {
        if (!supported) {
            error = "当前设备不支持语音朗读"
            return
        }
        if (!ready) {
            pendingStart = { play(texts, indices, newLabel, start) }
            return
        }
        stopInternal()
        if (texts.isEmpty()) {
            error = "暂无可朗读的内容"
            return
        }
        generation++
        queue.clear()
        texts.forEachIndexed { segmentPos, text ->
            val segmentIndex = indices.getOrElse(segmentPos) { segmentPos }
            val chunks = splitForSpeech(text)
            chunks.forEach { chunk -> queue.add(segmentIndex to chunk) }
        }
        if (queue.isEmpty()) return
        label = newLabel
        error = ""
        position = start.coerceIn(0, queue.lastIndex)
        startQueue()
    }

    private fun startQueue() {
        val engine = tts ?: return
        engine.stop()
        applyVoice(engine)
        val reciting = style == STYLE_RECITE
        engine.setSpeechRate(rate * if (reciting) 0.86f else 1f)
        engine.setPitch(if (reciting) 0.92f else 1.02f)
        val from = position
        val gen = generation
        queue.subList(from, queue.size).forEachIndexed { i, item ->
            val spoken = if (reciting && !item.second.takeLast(1).matches(Regex("[。！？…”’]"))) "${item.second}。" else item.second
            val params = Bundle()
            val id = "$gen-${from + i}"
            engine.speak(spoken, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, params, id)
        }
        speaking = true
        paused = false
        activeIndex = queue[from].first
        activeText = queue[from].second
    }

    private fun splitForSpeech(text: String): List<String> {
        val normalized = text.replace(Regex("\\s+"), " ").trim()
        if (normalized.isEmpty()) return emptyList()
        val limit = 150
        if (normalized.length <= limit) return listOf(normalized)
        val result = ArrayList<String>()
        val current = StringBuilder()
        for (ch in normalized) {
            current.append(ch)
            val cut = (current.length >= limit && ch in "。！？；…”’!?") || current.length >= limit * 2
            if (cut) {
                result.add(current.toString().trim())
                current.setLength(0)
            }
        }
        if (current.isNotBlank()) result.add(current.toString().trim())
        return result
    }

    /** 暂停：停在当前片段，继续时从该片段重新读起 */
    fun pause() {
        if (!speaking || paused) return
        val currentIndex = queue.indexOfFirst { it.first == activeIndex }
        if (currentIndex >= 0) position = currentIndex
        generation++
        tts?.stop()
        speaking = false
        paused = true
    }

    fun resume() {
        if (!paused) return
        paused = false
        startQueue()
    }

    fun stop() {
        stopInternal()
        finish()
    }

    private fun stopInternal() {
        generation++
        tts?.stop()
        queue.clear()
    }

    private fun finish() {
        generation++
        speaking = false
        paused = false
        activeIndex = -1
        activeText = ""
        position = 0
        queue.clear()
    }

    /** 调整语速：播放中则从当前片段重新开读 */
    fun changeRate(value: Float) {
        rate = value
        restartIfPlaying()
    }

    /** 切换语气：主持人（平稳）或朗诵（舒缓） */
    fun changeStyle(value: String) {
        style = value
        restartIfPlaying()
    }

    fun changeVoice(name: String) {
        voiceName = name
        restartIfPlaying()
    }

    private fun restartIfPlaying() {
        if (!speaking || paused) return
        val current = queue.indexOfFirst { it.first == activeIndex }.coerceAtLeast(0)
        generation++
        tts?.stop()
        position = current
        startQueue()
    }

    val progress: Float
        get() {
            if (queue.isEmpty() || activeIndex < 0) return 0f
            val pos = queue.indexOfFirst { it.first == activeIndex }
            if (pos < 0) return 0f
            return (pos + 1f) / queue.size
        }

    val totalSegments: Int get() = queue.map { it.first }.distinct().size

    fun release() {
        stop()
        tts?.shutdown()
        tts = null
    }

    companion object {
        const val STYLE_ANCHOR = "anchor"
        const val STYLE_RECITE = "recite"
    }
}
