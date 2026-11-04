<script setup>
import { computed } from 'vue'

const props = defineProps({
  speech: { type: Object, required: true },
  label: { type: String, default: '正在朗读' }
})

const rateValue = computed({
  get: () => props.speech.rate,
  set: (value) => props.speech.setRate(Number(value))
})

const voiceValue = computed({
  get: () => props.speech.voiceURI,
  set: (value) => props.speech.setVoice(value)
})

const progress = computed(() => {
  const total = props.speech.segments.length
  if (!total || props.speech.activeIndex < 0) return 0
  return ((props.speech.activeIndex + 1) / total) * 100
})
</script>

<template>
  <section class="speech-bar" aria-label="语音朗读控制台">
    <div class="speech-bar__main">
      <div class="speech-bar__meta">
        <span class="speech-dot" :class="{ live: speech.speaking, paused: speech.paused }"></span>
        <div>
          <p class="speech-label">{{ speech.speaking ? label : '语音朗读' }}</p>
          <p class="speech-text">
            {{ speech.speaking ? speech.activeText : '使用浏览器内置语音合成，可调节语速与音色' }}
          </p>
        </div>
      </div>

      <div class="speech-bar__controls">
        <button
          v-if="speech.speaking"
          class="icon-btn"
          type="button"
          :title="speech.paused ? '继续播放' : '暂停'"
          @click="speech.paused ? speech.resume() : speech.pause()"
        >
          {{ speech.paused ? '▶' : '❚❚' }}
        </button>
        <button v-if="speech.speaking" class="icon-btn stop" type="button" title="停止" @click="speech.stop()">
          ■
        </button>
        <span v-if="speech.speaking" class="speech-progress-text">
          {{ speech.activeIndex + 1 }} / {{ speech.segments.length }}
        </span>

        <label class="speech-field">
          <span>音色</span>
          <select v-model="voiceValue" :disabled="!speech.voices.length">
            <option v-if="!speech.voices.length" value="">默认</option>
            <option v-for="voice in speech.voices" :key="voice.voiceURI" :value="voice.voiceURI">
              {{ voice.name }} · {{ voice.lang }}
            </option>
          </select>
        </label>

        <label class="speech-field rate-field">
          <span>语速 {{ Number(rateValue).toFixed(1) }}×</span>
          <input v-model="rateValue" type="range" min="0.6" max="1.8" step="0.1" />
        </label>
      </div>
    </div>

    <div v-if="speech.speaking" class="speech-bar__progress" :style="{ width: `${progress}%` }"></div>
    <p v-if="speech.error" class="speech-error">{{ speech.error }}</p>
    <p v-if="!speech.supported" class="speech-error">
      当前浏览器不支持语音合成，请使用最新版 Chrome / Edge / Safari。
    </p>
  </section>
</template>