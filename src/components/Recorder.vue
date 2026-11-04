<script setup>
import { onBeforeUnmount, ref } from 'vue'

const supported =
  typeof navigator !== 'undefined' &&
  Boolean(navigator.mediaDevices?.getUserMedia) &&
  typeof window !== 'undefined' &&
  'MediaRecorder' in window

const recording = ref(false)
const seconds = ref(0)
const audioUrl = ref('')
const error = ref('')
const level = ref(0)

let recorder = null
let stream = null
let chunks = []
let timer = null
let audioContext = null
let analyser = null
let raf = null

function formatTime(value) {
  const m = String(Math.floor(value / 60)).padStart(2, '0')
  const s = String(value % 60).padStart(2, '0')
  return `${m}:${s}`
}

function cleanupStream() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
  if (raf) {
    cancelAnimationFrame(raf)
    raf = null
  }
  if (audioContext) {
    audioContext.close().catch(() => {})
    audioContext = null
  }
  analyser = null
  if (stream) {
    stream.getTracks().forEach((track) => track.stop())
    stream = null
  }
}

function monitorLevel(source) {
  try {
    audioContext = new (window.AudioContext || window.webkitAudioContext)()
    analyser = audioContext.createAnalyser()
    analyser.fftSize = 256
    const node = audioContext.createMediaStreamSource(source)
    node.connect(analyser)
    const data = new Uint8Array(analyser.frequencyBinCount)
    const tick = () => {
      analyser.getByteFrequencyData(data)
      const avg = data.reduce((sum, n) => sum + n, 0) / data.length
      level.value = Math.min(1, avg / 120)
      raf = requestAnimationFrame(tick)
    }
    tick()
  } catch {
    // 音频可视化失败不影响录音
  }
}

async function start() {
  error.value = ''
  if (!supported) {
    error.value = '当前浏览器不支持麦克风录音，请使用 Chrome / Edge 并通过 http://localhost 访问。'
    return
  }
  try {
    stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    chunks = []
    const preferred = ['audio/webm;codecs=opus', 'audio/webm', 'audio/mp4', 'audio/ogg']
    const mimeType = preferred.find((type) => MediaRecorder.isTypeSupported(type)) || ''
    recorder = new MediaRecorder(stream, mimeType ? { mimeType } : undefined)
    recorder.addEventListener('dataavailable', (event) => {
      if (event.data.size > 0) chunks.push(event.data)
    })
    recorder.addEventListener('stop', () => {
      const blob = new Blob(chunks, { type: recorder?.mimeType || 'audio/webm' })
      if (audioUrl.value) URL.revokeObjectURL(audioUrl.value)
      audioUrl.value = URL.createObjectURL(blob)
      recording.value = false
      cleanupStream()
      level.value = 0
    })
    recorder.start()
    recording.value = true
    seconds.value = 0
    timer = setInterval(() => {
      seconds.value += 1
    }, 1000)
    monitorLevel(stream)
  } catch (err) {
    error.value =
      err?.name === 'NotAllowedError'
        ? '麦克风权限被拒绝，请在浏览器地址栏中允许麦克风访问。'
        : `无法启动录音：${err?.message || err}`
    cleanupStream()
    recording.value = false
  }
}

function stop() {
  if (recorder && recording.value) {
    recorder.stop()
  }
}

function reset() {
  if (audioUrl.value) URL.revokeObjectURL(audioUrl.value)
  audioUrl.value = ''
  seconds.value = 0
  error.value = ''
}

onBeforeUnmount(() => {
  if (recorder && recording.value) {
    recorder.stop()
  }
  cleanupStream()
  if (audioUrl.value) URL.revokeObjectURL(audioUrl.value)
})
</script>

<template>
  <section class="recorder-card">
    <header class="recorder-card__head">
      <div>
        <h3>跟读练习</h3>
        <p>戴上耳机，用自己的声音读一段；录音只保存在本机，不会上传。</p>
      </div>
      <span class="recorder-status" :class="{ live: recording }">
        {{ recording ? '录音中' : audioUrl ? '已录制' : '待录音' }}
      </span>
    </header>

    <div class="recorder-body">
      <button v-if="!recording" class="record-btn" type="button" @click="audioUrl ? (reset(), start()) : start()">
        <span class="record-dot"></span>
        {{ audioUrl ? '重新录制' : '开始录音' }}
      </button>
      <button v-else class="record-btn recording" type="button" @click="stop">
        <span class="record-square"></span>
        停止录音 · {{ formatTime(seconds) }}
      </button>

      <div class="recorder-wave" :class="{ live: recording }" aria-hidden="true">
        <span v-for="n in 18" :key="n" :style="{ height: recording ? `${12 + level * 34 * ((n % 5) + 1) / 2}px` : '8px' }"></span>
      </div>
    </div>

    <audio v-if="audioUrl" class="recorder-audio" :src="audioUrl" controls preload="metadata"></audio>
    <div v-if="audioUrl" class="recorder-actions">
      <a class="text-btn" :href="audioUrl" :download="`跟读录音-${Date.now()}.webm`">下载录音</a>
      <button class="text-btn" type="button" @click="reset">清除录音</button>
    </div>

    <p v-if="error" class="recorder-error">{{ error }}</p>
    <p v-if="!supported" class="recorder-error">
      当前环境不支持录音：需要 Chrome / Edge，并通过 http://localhost 或 HTTPS 访问。
    </p>
  </section>
</template>