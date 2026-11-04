<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { AI_PRESETS, DEFAULT_AI_SETTINGS, applyPreset, generateBookWithAI, loadAISettings, saveAISettings } from '../services/ai.js'
import { addCustomBook, createCustomId, createLocalBook, loadCustomBooks, normalizeCustomBook, removeCustomBook } from '../data/customBooks.js'

const router = useRouter()
const title = ref('')
const author = ref('')
const category = ref('文史哲')
const categories = ['古典小说', '文史哲', '诗词曲赋', '近现代文学', '外国文学', '科普新知']
const settings = ref({ ...DEFAULT_AI_SETTINGS })
const settingsSaved = ref(false)
const busy = ref('')
const error = ref('')
const result = ref(null)
const books = ref([])

const preset = computed(() => AI_PRESETS.find((item) => item.key === settings.value.preset) ?? AI_PRESETS[0])
const lectureStats = computed(() => {
  const lecture = result.value?.lecture
  if (!lecture) return null
  const chars =
    (lecture.opening || '').length +
    (lecture.sections || []).flatMap((section) => section.paragraphs || []).join('').length +
    (lecture.closing || '').length
  return { sections: lecture.sections?.length ?? 0, chars }
})

onMounted(() => {
  settings.value = loadAISettings()
  books.value = loadCustomBooks()
})

function onPreset() {
  settings.value = applyPreset(settings.value, settings.value.preset)
  settingsSaved.value = false
}

function persistSettings() {
  saveAISettings(settings.value)
  settingsSaved.value = true
}

function refreshBooks() {
  books.value = loadCustomBooks()
}

function saveGenerated(book) {
  addCustomBook(book)
  refreshBooks()
  result.value = book
}

async function generate() {
  const name = title.value.trim()
  if (!name) {
    error.value = '请先输入书名'
    return
  }
  if (!settings.value.baseURL.trim() || !settings.value.model.trim()) {
    error.value = '请先在“AI 服务设置”中填写服务地址与模型名称'
    return
  }
  error.value = ''
  result.value = null
  busy.value = 'ai'
  try {
    const raw = await generateBookWithAI({
      title: name,
      author: author.value.trim(),
      category: category.value,
      settings: settings.value
    })
    const book = {
      ...normalizeCustomBook(raw, { title: name, author: author.value.trim(), category: category.value }),
      id: createCustomId(),
      title: name,
      aiGenerated: true,
      model: settings.value.model
    }
    saveGenerated(book)
  } catch (err) {
    error.value = err?.message || '生成失败，请稍后重试'
  } finally {
    busy.value = ''
  }
}

function generateLocal() {
  const name = title.value.trim()
  if (!name) {
    error.value = '请先输入书名'
    return
  }
  error.value = ''
  saveGenerated(createLocalBook({ title: name, author: author.value.trim(), category: category.value }))
}

function openBook(id) {
  window.location.hash = `#/book/${id}`
  window.location.reload()
}

function remove(id) {
  removeCustomBook(id)
  refreshBooks()
  if (result.value?.id === id) result.value = null
  window.location.hash = '#/custom'
  window.location.reload()
}

function downloadFile(filename, content, type) {
  const url = URL.createObjectURL(new Blob([content], { type }))
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

function downloadProjectJson() {
  const book = result.value
  if (!book) return

  const detail = {
    id: book.id,
    title: book.title,
    subtitle: book.subtitle,
    author: book.author,
    authorIntro: book.authorIntro,
    country: book.country,
    era: book.era,
    category: book.category,
    tags: book.tags,
    readingMinutes: book.readingMinutes,
    summary: book.summary,
    importance: 1,
    externalText: null,
    essence: book.essence,
    lecture: book.lecture,
    chapters: book.chapters,
    similar: []
  }
  const filename = `${book.id.replace(/[<>:"/\\|?*\u0000-\u001f]/g, '-')}.json`
  downloadFile(filename, `${JSON.stringify(detail, null, 2)}\n`, 'application/json;charset=utf-8')
}

function downloadCatalogCsv() {
  const book = result.value
  if (!book) return

  const header = ['id', 'title', 'author', 'country', 'era', 'category', 'level', 'batch', 'reading_minutes', 'tags', 'summary', 'cat1', 'cat2', 'cat3']
  const row = [
    book.id,
    book.title,
    book.author,
    book.country,
    book.era,
    book.category,
    '重要',
    0,
    book.readingMinutes,
    book.tags.join('|'),
    book.summary,
    '',
    '',
    ''
  ]
  const csvCell = (value) => {
    const text = String(value ?? '')
    return /[",\r\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text
  }
  const csv = `\uFEFF${header.join(',')}\r\n${row.map(csvCell).join(',')}\r\n`
  const filename = `${book.id.replace(/[<>:"/\\|?*\u0000-\u001f]/g, '-')}-catalog.csv`
  downloadFile(filename, csv, 'text/csv;charset=utf-8')
}
</script>

<template>
  <div class="custom-view page-block">
    <nav class="breadcrumb">
      <RouterLink to="/">总目录</RouterLink>
      <span>/</span>
      <strong>添加自定义书</strong>
    </nav>

    <section class="custom-hero">
      <span class="eyebrow">自定义书架 · 免费大模型</span>
      <h1>输入书名，生成一份精读档案</h1>
      <p>
        调用兼容 OpenAI 接口的免费模型，生成故事情节、阅读所获、必背章句、演讲书稿与章节导读。
        生成结果只保存在本机浏览器，并自动加入总目录的“我的书架”。
      </p>
    </section>

    <section class="panel custom-form-panel">
      <div class="field-row">
        <label class="field">
          <span>书名</span>
          <input v-model="title" type="text" placeholder="例如：百年孤独" @keyup.enter="generate" />
        </label>
        <label class="field">
          <span>作者（可选）</span>
          <input v-model="author" type="text" placeholder="例如：马尔克斯" />
        </label>
        <label class="field">
          <span>类别</span>
          <select v-model="category">
            <option v-for="item in categories" :key="item" :value="item">{{ item }}</option>
          </select>
        </label>
      </div>

      <details class="ai-settings">
        <summary>AI 服务设置 · {{ preset.label }}</summary>
        <div class="field-row">
          <label class="field">
            <span>预设</span>
            <select v-model="settings.preset" @change="onPreset">
              <option v-for="item in AI_PRESETS" :key="item.key" :value="item.key">{{ item.label }}</option>
            </select>
          </label>
          <label class="field">
            <span>服务地址</span>
            <input v-model="settings.baseURL" type="text" placeholder="http://localhost:11434/v1" />
          </label>
          <label class="field">
            <span>模型名称</span>
            <input v-model="settings.model" type="text" placeholder="qwen2.5:7b" />
          </label>
          <label class="field">
            <span>API Key（本地 Ollama 可留空）</span>
            <input v-model="settings.apiKey" type="password" autocomplete="off" placeholder="sk-..." />
          </label>
        </div>
        <p class="custom-note">{{ preset.hint }}</p>
        <div class="custom-actions">
          <button class="ghost-btn small" type="button" @click="persistSettings">保存设置</button>
          <span v-if="settingsSaved" class="inline-note">设置已保存在本机</span>
        </div>
      </details>

      <div class="custom-actions">
        <button class="primary-btn" type="button" :disabled="busy === 'ai'" @click="generate">
          {{ busy === 'ai' ? '正在生成…' : 'AI 生成精读档案' }}
        </button>
        <button class="ghost-btn" type="button" :disabled="Boolean(busy)" @click="generateLocal">
          仅加入书架（本地速览）
        </button>
      </div>

      <p v-if="busy === 'ai'" class="inline-note">正在调用大模型，通常需要 1—3 分钟，请保持页面打开。</p>
      <p v-if="error" class="inline-note error">{{ error }}</p>
      <p class="custom-note">API Key 只保存在本机浏览器；生成内容由模型产出，请自行校对后再使用。</p>
    </section>

    <section v-if="result" class="panel custom-result">
      <span class="eyebrow">已加入本机书架</span>
      <h2>{{ result.title }}</h2>
      <p class="book-meta">{{ result.author }} · {{ result.category }}</p>
      <p>{{ result.summary }}</p>
      <div class="meta-pills">
        <span v-for="tag in result.tags" :key="tag">{{ tag }}</span>
        <span v-if="lectureStats">{{ lectureStats.sections }} 节讲稿 · 约 {{ lectureStats.chars }} 字</span>
        <span v-if="result.aiGenerated">AI 生成 · {{ result.model }}</span>
        <span v-else>本地速览</span>
      </div>
      <div class="custom-actions">
        <button class="primary-btn" type="button" @click="openBook(result.id)">查看详情</button>
        <template v-if="result.aiGenerated">
          <button class="ghost-btn" type="button" @click="downloadProjectJson">下载项目档案 JSON</button>
          <button class="ghost-btn" type="button" @click="downloadCatalogCsv">下载目录 CSV</button>
        </template>
        <button class="ghost-btn" type="button" @click="result = null">继续添加</button>
      </div>
      <p v-if="result.aiGenerated" class="custom-note">
        JSON 可放入 <code>data/books/{{ result.category }}/</code>；目录 CSV 含表头和书目行，将第二行追加到 <code>data/catalog.csv</code>，再运行 <code>npm run build</code>。CSV 中批次为 0（未分批）。
      </p>
    </section>

    <section class="panel">
      <div class="custom-list-head">
        <h2>我的书架</h2>
        <span>{{ books.length }} 部自定义书</span>
      </div>
      <div v-if="books.length" class="custom-list">
        <article v-for="book in books" :key="book.id" class="custom-item">
          <div>
            <strong>{{ book.title }}</strong>
            <span>{{ book.author }} · {{ book.category }}<template v-if="book.aiGenerated"> · AI 生成</template></span>
          </div>
          <div class="custom-item__actions">
            <button class="text-btn" type="button" @click="openBook(book.id)">查看</button>
            <button class="text-btn" type="button" @click="remove(book.id)">删除</button>
          </div>
        </article>
      </div>
      <p v-else class="muted-text">还没有自定义书。输入书名，生成第一本。</p>
    </section>
  </div>
</template>