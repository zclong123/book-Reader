import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'

export function useSpeech() {
  const supported =
    typeof window !== 'undefined' &&
    'speechSynthesis' in window &&
    typeof window.SpeechSynthesisUtterance !== 'undefined'

  const voices = ref([])
  const voiceURI = ref('')
  const rate = ref(1)
  // 朗读风格：主持（平稳、偏快） / 朗诵（放慢、有韵）
  const style = ref('anchor')
  const speaking = ref(false)
  const paused = ref(false)
  const activeIndex = ref(-1)
  const segments = ref([])
  const label = ref('')
  const error = ref('')

  let cancelled = true
  let timer = null

  const activeText = computed(() => segments.value[activeIndex.value]?.text ?? '')
  const selectedVoice = computed(() => voices.value.find((v) => v.voiceURI === voiceURI.value))

  function refreshVoices() {
    if (!supported) return
    const list = window.speechSynthesis.getVoices()
    if (!list.length) return
    voices.value = list
    if (!voiceURI.value) {
      const zh =
        list.find((v) => /^zh([-_]|$)/i.test(v.lang)) ??
        list.find((v) => /chinese|中文|普通话|国語|国语/i.test(v.name)) ??
        list[0]
      voiceURI.value = zh?.voiceURI ?? ''
    }
  }

  function finish() {
    speaking.value = false
    paused.value = false
    activeIndex.value = -1
    segments.value = []
    label.value = ''
  }

  function speakAt(index) {
    if (!supported || cancelled) return
    if (index >= segments.value.length) {
      finish()
      return
    }

    const text = (segments.value[index]?.text ?? '').trim()
    if (!text) {
      speakAt(index + 1)
      return
    }

    // 主持：标准普通话、语速平稳；朗诵：放慢一点、降调，句末补停顿
    const reciting = style.value === 'recite'
    const spoken = reciting && !/[。！？…”]$/.test(text) ? `${text}。` : text
    const utterance = new SpeechSynthesisUtterance(spoken)
    utterance.lang = 'zh-CN'
    utterance.rate = rate.value * (reciting ? 0.86 : 1)
    utterance.pitch = reciting ? 0.92 : 1.02
    if (selectedVoice.value) utterance.voice = selectedVoice.value

    utterance.onstart = () => {
      if (cancelled) return
      speaking.value = true
      paused.value = false
      activeIndex.value = index
    }

    utterance.onend = () => {
      if (cancelled) return
      speakAt(index + 1)
    }

    utterance.onerror = (event) => {
      if (cancelled || event.error === 'interrupted' || event.error === 'canceled') return
      error.value = `语音播放失败：${event.error || '未知错误'}`
      finish()
    }

    window.speechSynthesis.speak(utterance)
  }

  function stop() {
    if (!supported) return
    cancelled = true
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
    window.speechSynthesis.cancel()
    finish()
  }

  function play(nextSegments, options = {}) {
    if (!supported) {
      error.value = '当前浏览器不支持语音合成，建议使用最新版 Chrome、Edge 或 Safari。'
      return false
    }
    const list = (nextSegments ?? [])
      .map((item) => (typeof item === 'string' ? { text: item } : item))
      .filter((item) => (item?.text ?? '').trim())
    if (!list.length) {
      error.value = '暂无可朗读的内容。'
      return false
    }

    stop()
    cancelled = false
    error.value = ''
    segments.value = list
    label.value = options.label ?? ''
    activeIndex.value = options.start ?? 0
    speaking.value = true

    timer = setTimeout(() => {
      timer = null
      if (!cancelled) speakAt(options.start ?? 0)
    }, 60)
    return true
  }

  function toggle(nextSegments, options = {}) {
    if (speaking.value) {
      if (paused.value) {
        resume()
      } else {
        pause()
      }
      return true
    }
    return play(nextSegments, options)
  }

  function pause() {
    if (!supported || !speaking.value) return
    window.speechSynthesis.pause()
    paused.value = true
  }

  function resume() {
    if (!supported || !speaking.value) return
    window.speechSynthesis.resume()
    paused.value = false
  }

  function setRate(value) {
    rate.value = value
    if (speaking.value) {
      const resumeAt = activeIndex.value
      const current = [...segments.value]
      const currentLabel = label.value
      const wasPaused = paused.value
      stop()
      if (!wasPaused) {
        play(current, { label: currentLabel, start: Math.max(0, resumeAt) })
      }
    }
  }

  function setVoice(value) {
    voiceURI.value = value
    if (speaking.value) {
      const resumeAt = activeIndex.value
      const current = [...segments.value]
      const currentLabel = label.value
      const wasPaused = paused.value
      stop()
      if (!wasPaused) {
        play(current, { label: currentLabel, start: Math.max(0, resumeAt) })
      }
    }
  }

  onMounted(() => {
    refreshVoices()
    if (supported) {
      window.speechSynthesis.addEventListener?.('voiceschanged', refreshVoices)
    }
  })

  onBeforeUnmount(() => {
    stop()
    if (supported) {
      window.speechSynthesis.removeEventListener?.('voiceschanged', refreshVoices)
    }
  })

  return reactive({
    supported,
    voices,
    voiceURI,
    rate,
    style,
    speaking,
    paused,
    activeIndex,
    activeText,
    segments,
    label,
    error,
    play,
    toggle,
    pause,
    resume,
    stop,
    setRate,
    setVoice
  })
}