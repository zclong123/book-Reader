<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import Recorder from '../components/Recorder.vue'
import SpeechBar from '../components/SpeechBar.vue'
import { useSpeech } from '../composables/useSpeech.js'
import { getBook, getContent } from '../data/library.js'

const props = defineProps({
  id: { type: String, required: true }
})

const route = useRoute()
const bookId = computed(() => props.id || String(route.params.id))
const book = computed(() => getBook(bookId.value))
const content = computed(() => getContent(bookId.value))

const speech = useSpeech()
const imported = ref(null)
const currentIndex = ref(0)
const fontSize = ref(18)
const theme = ref('paper')
const importError = ref('')
const importInfo = ref('')
const fileInput = ref(null)

const builtInChapters = computed(() =>
  (content.value?.chapters ?? []).map((chapter) => ({
    title: chapter.title,
    text: [chapter.guide, chapter.excerpt].filter(Boolean).join('\n\n'),
    guide: chapter.guide,
    excerpt: chapter.excerpt
  }))
)

const chapters = computed(() => {
  if (imported.value?.chapters?.length) return imported.value.chapters
  return builtInChapters.value
})

const usingImported = computed(() => Boolean(imported.value?.chapters?.length))

const currentChapter = computed(() => chapters.value[currentIndex.value] ?? null)

const chapterParagraphs = computed(() => {
  const text = currentChapter.value?.text ?? ''
  return text
    .split(/\n+/)
    .map((p) => p.trim())
    .filter(Boolean)
})

const chapterSegments = computed(() =>
  chapterParagraphs.value.map((text) => ({ text }))
)

const progressKey = computed(() => `classics:read:${bookId.value}`)
const originalKey = computed(() => `classics:original:${bookId.value}`)

function loadSaved() {
  try {
    const saved = JSON.parse(localStorage.getItem(originalKey.value) ?? 'null')
    if (saved?.text) {
      imported.value = { name: saved.name, chapters: splitChapters(saved.text) }
      importInfo.value = `已恢复上次导入的《${saved.name}》`
    }
  } catch {
    imported.value = null
  }
  try {
    const progress = JSON.parse(localStorage.getItem(progressKey.value) ?? 'null')
    if (progress && Number.isFinite(progress.chapter)) {
      currentIndex.value = Math.min(progress.chapter, Math.max(0, chapters.value.length - 1))
    }
  } catch {
    currentIndex.value = 0
  }
  const savedFont = Number(localStorage.getItem('classics:fontSize'))
  if (savedFont >= 14 && savedFont <= 30) fontSize.value = savedFont
  const savedTheme = localStorage.getItem('classics:theme')
  if (savedTheme === 'night' || savedTheme === 'paper') theme.value = savedTheme
}

function saveProgress() {
  try {
    localStorage.setItem(progressKey.value, JSON.stringify({ chapter: currentIndex.value }))
  } catch {
    // 忽略隐私模式下的存储失败
  }
}

function splitChapters(rawText, fallbackTitle = '全文') {
  const text = String(rawText ?? '').replace(/\r\n?/g, '\n').trim()
  if (!text) return []

  const headingRegex =
    /^[ \t]*(第[0-9零〇一二三四五六七八九十百千万两]+[章回节卷篇部集][^\n]{0,60}|序章|序言|前言|楔子|引子|尾声|后记|附录[^\n]{0,30}|#{1,3}[ \t]+[^\n]{1,60})[ \t]*$/gm
  const matches = [...text.matchAll(headingRegex)]

  if (matches.length >= 2) {
    const result = []
    const preface = text.slice(0, matches[0].index).trim()
    if (preface.length > 60) {
      result.push({ title: '序 / 前言', text: preface })
    }
    matches.forEach((match, i) => {
      const start = match.index + match[0].length
      const end = i + 1 < matches.length ? matches[i + 1].index : text.length
      const title = match[1].replace(/^#{1,3}[ \t]+/, '').trim()
      const body = text.slice(start, end).trim()
      if (body) result.push({ title, text: body })
    })
    if (result.length >= 2) return result
  }

  const paragraphs = text.split(/\n\s*\n/).map((p) => p.trim()).filter(Boolean)
  const chunks = []
  let buffer = ''
  for (const paragraph of paragraphs) {
    if ((buffer + paragraph).length > 3200) {
      if (buffer) chunks.push(buffer)
      buffer = paragraph
    } else {
      buffer += `${buffer ? '\n\n' : ''}${paragraph}`
    }
  }
  if (buffer) chunks.push(buffer)

  if (chunks.length <= 1) return [{ title: fallbackTitle, text }]
  return chunks.map((chunk, i) => ({ title: `第 ${i + 1} 部分`, text: chunk }))
}

function pickFile() {
  fileInput.value?.click()
}

async function onFile(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  importError.value = ''
  importInfo.value = ''
  if (file.size > 12 * 1024 * 1024) {
    importError.value = '文件超过 12MB，建议先在本地压缩或拆分成几卷后再导入。'
    return
  }
  try {
    const text = await file.text()
    const parsed = splitChapters(text, file.name.replace(/\.[^.]+$/, ''))
    if (!parsed.length) {
      importError.value = '没有从文件中读到正文，请确认是纯文本或 Markdown 文件。'
      return
    }
    imported.value = { name: file.name, chapters: parsed }
    currentIndex.value = 0
    importInfo.value = `已导入《${file.name}》，自动拆分 ${parsed.length} 章`
    speech.stop()
    try {
      localStorage.setItem(originalKey.value, JSON.stringify({ name: file.name, text }))
    } catch {
      importInfo.value += '（文件较大，本次仅在内存中阅读）'
    }
    saveProgress()
  } catch (err) {
    importError.value = `读取失败：${err?.message || err}`
  }
}

function resetImport() {
  imported.value = null
  currentIndex.value = 0
  importInfo.value = '已切换回内置章节导读'
  importError.value = ''
  speech.stop()
  try {
    localStorage.removeItem(originalKey.value)
    localStorage.removeItem(progressKey.value)
  } catch {
    // ignore
  }
}

function goChapter(index) {
  if (index < 0 || index >= chapters.value.length) return
  speech.stop()
  currentIndex.value = index
  saveProgress()
}

function adjustFont(delta) {
  fontSize.value = Math.min(30, Math.max(14, fontSize.value + delta))
  localStorage.setItem('classics:fontSize', String(fontSize.value))
}

function toggleTheme() {
  theme.value = theme.value === 'paper' ? 'night' : 'paper'
  localStorage.setItem('classics:theme', theme.value)
}

function playChapter() {
  speech.play(chapterSegments.value, { label: `正在朗读 · ${currentChapter.value?.title ?? book.value?.title ?? ''}` })
}

watch(bookId, () => {
  speech.stop()
  imported.value = null
  currentIndex.value = 0
  importInfo.value = ''
  importError.value = ''
  loadSaved()
})

watch(currentIndex, () => {
  saveProgress()
  if (speech.speaking) speech.stop()
})

onMounted(loadSaved)
</script>

<template>
  <div v-if="!book" class="empty-state page-block">
    <strong>没有找到这部作品</strong>
    <RouterLink class="primary-btn" to="/">返回总目录</RouterLink>
  </div>

  <div v-else class="read-view" :class="`theme-${theme}`">
    <nav class="breadcrumb">
      <RouterLink to="/">总目录</RouterLink>
      <span>/</span>
      <RouterLink :to="`/book/${book.id}`">{{ book.title }}</RouterLink>
      <span>/</span>
      <strong>阅读原书</strong>
    </nav>

    <section class="reader-head">
      <div>
        <span class="eyebrow">原书阅读器 · 本地文件</span>
        <h1>{{ book.title }}</h1>
        <p>{{ book.author }} · {{ book.era }} · {{ usingImported ? '本地原书' : '内置章节导读' }}</p>
      </div>
      <div class="reader-head__actions">
        <button class="primary-btn" type="button" @click="pickFile">导入本地原书 (.txt)</button>
        <button v-if="usingImported" class="ghost-btn" type="button" @click="resetImport">恢复内置导读</button>
        <input ref="fileInput" class="visually-hidden" type="file" accept=".txt,.md,.markdown,text/plain,text/markdown" @change="onFile" />
      </div>
    </section>

    <p v-if="importInfo" class="inline-note">{{ importInfo }}</p>
    <p v-if="importError" class="inline-note error">{{ importError }}</p>

    <div class="reader-toolbar">
      <div class="reader-tools">
        <span class="tool-label">字号</span>
        <button class="tool-btn" type="button" @click="adjustFont(-1)">A−</button>
        <span class="font-value">{{ fontSize }}px</span>
        <button class="tool-btn" type="button" @click="adjustFont(1)">A+</button>
        <button class="tool-btn" type="button" @click="toggleTheme">
          {{ theme === 'paper' ? '夜间模式' : '纸质模式' }}
        </button>
      </div>
      <div class="reader-tools">
        <span class="tool-label">章节</span>
        <strong>{{ chapters.length ? currentIndex + 1 : 0 }} / {{ chapters.length }}</strong>
      </div>
    </div>

    <SpeechBar :speech="speech" :label="speech.label || '正在朗读本章'" />

    <div v-if="!chapters.length" class="empty-state page-block">
      <strong>还没有可阅读的章节</strong>
      <p>导入一本你电脑里的 .txt 或 .md 电子书，系统会自动按“第X章 / 第X回”拆分。</p>
      <button class="primary-btn" type="button" @click="pickFile">选择本地文件</button>
    </div>

    <div v-else class="reader-layout">
      <aside class="chapter-nav">
        <header>
          <h2>目录</h2>
          <small>{{ chapters.length }} 章</small>
        </header>
        <button
          v-for="(chapter, index) in chapters"
          :key="`${chapter.title}-${index}`"
          class="chapter-nav-item"
          :class="{ active: index === currentIndex }"
          type="button"
          @click="goChapter(index)"
        >
          <span>{{ String(index + 1).padStart(2, '0') }}</span>
          <strong>{{ chapter.title }}</strong>
        </button>
      </aside>

      <article class="reader-body" :style="{ fontSize: `${fontSize}px` }">
        <header class="reader-body__head">
          <div>
            <span class="eyebrow">{{ usingImported ? imported.name : '章节导读与节选' }}</span>
            <h2>{{ currentChapter.title }}</h2>
          </div>
          <button class="primary-btn small" type="button" :disabled="!chapterSegments.length" @click="playChapter">
            ▶ 朗读本章
          </button>
        </header>

        <p v-if="!usingImported" class="reader-hint">
          这里展示的是章节导读与短节选。要读全文，请点击右上角“导入本地原书”。
        </p>

        <div class="reader-text">
          <p
            v-for="(paragraph, index) in chapterParagraphs"
            :key="index"
            :class="{ 'is-speaking': speech.speaking && speech.activeIndex === index }"
            @click="speech.play(chapterSegments, { label: `正在朗读 · ${currentChapter.title}`, start: index })"
          >
            {{ paragraph }}
          </p>
        </div>

        <footer class="reader-pager">
          <button class="ghost-btn" type="button" :disabled="currentIndex === 0" @click="goChapter(currentIndex - 1)">
            ← 上一章
          </button>
          <button
            class="ghost-btn"
            type="button"
            :disabled="currentIndex >= chapters.length - 1"
            @click="goChapter(currentIndex + 1)"
          >
            下一章 →
          </button>
        </footer>
      </article>
    </div>

    <Recorder />
  </div>
</template>