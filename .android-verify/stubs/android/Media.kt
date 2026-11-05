@file:Suppress("unused", "UNUSED_PARAMETER")

package android.media

class MediaRecorder(val context: android.content.Context) {
    fun setAudioSource(source: Int) = Unit
    fun setOutputFormat(format: Int) = Unit
    fun setAudioEncoder(encoder: Int) = Unit
    fun setAudioEncodingBitRate(bits: Int) = Unit
    fun setAudioSamplingRate(rate: Int) = Unit
    fun setOutputFile(path: String) = Unit
    fun prepare() = Unit
    fun start() = Unit
    fun stop() = Unit
    fun reset() = Unit
    fun release() = Unit
    val maxAmplitude: Int get() = 0
    companion object {
        const val AudioSource_MIC = 1
        const val OutputFormat_MPEG_4 = 2
        const val AudioEncoder_AAC = 3
    }
}

class MediaPlayer {
    fun setDataSource(path: String) = Unit
    fun prepare() = Unit
    fun start() = Unit
    fun stop() = Unit
    fun release() = Unit
    fun setOnCompletionListener(listener: () -> Unit) = Unit
}
