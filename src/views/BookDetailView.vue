<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import BookCover from '../components/BookCover.vue'
import Recorder from '../components/Recorder.vue'
import SpeechBar from '../components/SpeechBar.vue'
import { useSpeech } from '../composables/useSpeech.js'
import { catalog, getBook, getContent, getDossier, lectureBlocks, similarBooks, detailSource, saveDossier, removeDossier } from '../data/library.js'
import { generateDetailForBook, reviseDetailWithAI, loadAISettings } from '../services/ai.js'
import { normalizeCustomBook } from '../data/customBooks.js'

const props = defineProps({
  id: { type: String, required: true }
})

const route = useRoute()
const bookId = computed(() => props.id || String(route.params.id))
const book = computed(() => getBook(bookId.value))
const revision = ref(0)
const content = ref(null)
const contentError = ref('')

watch(
  [bookId, revision],
  ([id], _oldValue, onCleanup) => {
  let cancelled = false
  onCleanup(() => {
    cancelled = true
  })
  contentError.value = ''
  const local = getDossier(id)
  if (local) {
    content.value = JSON.parse(JSON.stringify(local))
    return
  }
  content.value = null
  getContent(id)
    .then((detail) => {
      if (!cancelled) content.value = detail
    })
    .catch((err) => {
      if (!cancelled) contentError.value = `精读详情加载失败：${err?.message || err}`
    })
  },
  { immediate: true }
)
const similar = computed(() => similarBooks(book.value, 4))
const authorWorks = computed(() => {
  const author = book.value?.author
  if (!author || ['佚名', '不详', '未知'].includes(author)) return []
  return catalog.filter((item) => item.author === author && item.id !== bookId.value)
})
const authorWorksPreview = computed(() => authorWorks.value.slice(0, 6))
const moreAuthorWorks = computed(() => authorWorks.value.slice(authorWorksPreview.value.length))
const authorIntro = computed(() => {
  const intro = content.value?.authorIntro?.trim()
  if (!intro || intro.includes('作家或编者') || intro.includes('其写作常从具体的人和具体的处境出发')) return ''
  return intro
})
const tab = ref('essence')
const speech = useSpeech()
const favorite = ref(isFavorite(bookId.value))

const tabs = computed(() => [
  { key: 'essence', label: '阅读精华', hint: `${storyBlocks.value.length + insightBlocks.value.length} 段` },
  { key: 'lecture', label: '每日演讲书稿', hint: content.value?.lecture?.durationMinutes ? `${content.value.lecture.durationMinutes} 分钟` : '讲稿' },
  { key: 'chapters', label: '章节导读', hint: `${content.value?.chapters?.length ?? 0} 章` }
])

const storyBlocks = computed(() =>
  (content.value?.essence?.story ?? []).map((text, index) => ({ text, index }))
)
const insightBlocks = computed(() => {
  const offset = storyBlocks.value.length
  return (content.value?.essence?.insights ?? []).map((text, index) => ({ text, index: offset + index }))
})
const quoteBlocks = computed(() => {
  const offset = storyBlocks.value.length + insightBlocks.value.length
  return (content.value?.essence?.quotes ?? []).map((quote, index) => ({ ...quote, index: offset + index }))
})

const essenceSegments = computed(() => [
  ...storyBlocks.value.map((b) => ({ text: b.text, kind: 'story' })),
  ...insightBlocks.value.map((b) => ({ text: b.text, kind: 'insight' })),
  ...quoteBlocks.value.map((b) => ({ text: b.text, kind: 'quote' }))
])

const lecture = computed(() => lectureBlocks(content.value))
const lectureBlocksList = computed(() => lecture.value.blocks)
const lectureSegments = computed(() => lecture.value.segments)

const chapterBlocks = computed(() => {
  let index = 0
  return (content.value?.chapters ?? []).map((chapter) => {
    const item = {
      ...chapter,
      guideIndex: index++,
      excerptIndex: chapter.excerpt ? index++ : -1
    }
    return item
  })
})
const chapterSegments = computed(() => {
  const segments = []
  for (const chapter of chapterBlocks.value) {
    if (chapter.guide) segments.push({ text: `${chapter.title}。${chapter.guide}` })
    if (chapter.excerpt) segments.push({ text: chapter.excerpt })
  }
  return segments
})

function isActive(index) {
  return speech.speaking && speech.activeIndex === index
}

function playEssence() {
  speech.play(essenceSegments.value, { label: '正在朗读 · 阅读精华' })
}

function playLecture() {
  speech.play(lectureSegments.value, { label: '正在朗读 · 每日演讲书稿' })
}

function playChapter() {
  speech.play(chapterSegments.value, { label: '正在朗读 · 章节导读' })
}

function switchTab(key) {
  if (tab.value === key) return
  speech.stop()
  tab.value = key
}

function isFavorite(id) {
  try {
    return JSON.parse(localStorage.getItem('classics:favorites') ?? '[]').includes(id)
  } catch {
    return false
  }
}

function toggleFavorite() {
  if (!book.value) return
  let ids = []
  try {
    ids = JSON.parse(localStorage.getItem('classics:favorites') ?? '[]')
  } catch {
    ids = []
  }
  const index = ids.indexOf(book.value.id)
  if (index >= 0) ids.splice(index, 1)
  else ids.push(book.value.id)
  localStorage.setItem('classics:favorites', JSON.stringify(ids))
  favorite.value = ids.includes(book.value.id)
}

/* ---------------- 按需生成 / 编辑 / AI 修改 ---------------- */
const source = computed(() => {
  revision.value
  return detailSource(bookId.value)
})
const aiBusy = ref('')
const aiError = ref('')
const aiNotice = ref('')
const editMode = ref(false)
const showRevise = ref(false)
const reviseInstruction = ref('')
const reviseScope = ref('all')
const draft = ref(null)
const settings = ref(loadAISettings())

function toText(list) {
  return (list ?? []).join('\n')
}

function toList(text) {
  return String(text ?? '')
    .split(/\n+/)
    .map((line) => line.trim())
    .filter(Boolean)
}

function prepareDraft(detail) {
  const copy = JSON.parse(JSON.stringify(detail ?? {}))
  copy.essence = copy.essence ?? {}
  copy.essence.storyText = toText(copy.essence.story)
  copy.essence.insightsText = toText(copy.essence.insights)
  copy.essence.quotes = copy.essence.quotes ?? []
  copy.lecture = copy.lecture ?? { title: '', opening: '', sections: [], closing: '' }
  copy.lecture.openingText = toText([copy.lecture.opening].filter(Boolean))
  copy.lecture.closingText = toText([copy.lecture.closing].filter(Boolean))
  copy.lecture.sections = (copy.lecture.sections ?? []).map((section) => ({
    ...section,
    paragraphsText: toText(section.paragraphs)
  }))
  copy.chapters = copy.chapters ?? []
  return copy
}

function persist(detail) {
  const clean = normalizeCustomBook(detail, {
    title: book.value?.title || '',
    author: book.value?.author || '',
    category: book.value?.category || ''
  })
  clean.id = bookId.value
  clean.title = book.value?.title || clean.title
  clean.custom = false
  saveDossier(bookId.value, clean)
  revision.value += 1
  return clean
}

async function generateDetail() {
  if (!book.value) return
  aiError.value = ''
  aiNotice.value = ''
  aiBusy.value = 'generate'
  try {
    const raw = await generateDetailForBook({ book: book.value, settings: settings.value })
    persist(raw)
    aiNotice.value = '精读详情已生成并保存在本机。'
  } catch (err) {
    aiError.value = err?.message || '生成失败，请稍后重试'
  } finally {
    aiBusy.value = ''
  }
}

function startEdit() {
  draft.value = prepareDraft(content.value)
  editMode.value = true
  aiNotice.value = ''
}

function saveEdit() {
  if (!book.value || !draft.value) return
  const d = draft.value
  d.essence.story = toList(d.essence.storyText)
  d.essence.insights = toList(d.essence.insightsText)
  d.lecture.opening = toList(d.lecture.openingText).join('\n')
  d.lecture.closing = toList(d.lecture.closingText).join('\n')
  d.lecture.sections = d.lecture.sections.map((section) => ({
    title: section.title,
    paragraphs: toList(section.paragraphsText)
  }))
  persist(d)
  editMode.value = false
  draft.value = null
  aiNotice.value = '修改已保存到本机。'
}

function cancelEdit() {
  editMode.value = false
  draft.value = null
}

function addQuoteRow() {
  if (draft.value?.essence) {
    draft.value.essence.quotes = draft.value.essence.quotes ?? []
    draft.value.essence.quotes.push({ text: '', source: '', note: '' })
  }
}

function addSectionRow() {
  if (draft.value?.lecture) {
    draft.value.lecture.sections = draft.value.lecture.sections ?? []
    draft.value.lecture.sections.push({ title: '新段落', paragraphs: [], paragraphsText: '' })
  }
}

function resetLocal() {
  if (!window.confirm('确定要清除本机的精读详情修改，恢复为馆内默认内容吗？')) return
  removeDossier(bookId.value)
  revision.value += 1
  aiNotice.value = '已恢复默认内容。'
}

async function reviseWithAI() {
  if (!book.value || !content.value) return
  aiError.value = ''
  aiBusy.value = 'revise'
  try {
    const raw = await reviseDetailWithAI({
      book: book.value,
      detail: content.value,
      instruction: reviseInstruction.value,
      scope: reviseScope.value,
      settings: settings.value
    })
    persist({ ...content.value, ...raw })
    aiNotice.value = 'AI 已按你的要求完成修改。'
    showRevise.value = false
    reviseInstruction.value = ''
  } catch (err) {
    aiError.value = err?.message || '修改失败，请稍后重试'
  } finally {
    aiBusy.value = ''
  }
}

watch(bookId, () => {
  speech.stop()
  tab.value = 'essence'
  favorite.value = isFavorite(bookId.value)
  editMode.value = false
  showRevise.value = false
  aiError.value = ''
  aiNotice.value = ''
})

watch(tab, () => {
  if (speech.speaking) speech.stop()
})
</script>

<template>
  <div v-if="!book" class="empty-state page-block">
    <strong>没有找到这部作品</strong>
    <p>它可能还未收录，返回总目录看看其它名著。</p>
    <RouterLink class="primary-btn" to="/">返回总目录</RouterLink>
  </div>

  <div v-else-if="contentError" class="empty-state page-block">
    <strong>精读详情加载失败</strong>
    <p>{{ contentError }}</p>
    <RouterLink class="primary-btn" to="/">返回总目录</RouterLink>
  </div>

  <div v-else-if="!content" class="empty-state page-block">
    <strong>正在加载精读详情…</strong>
  </div>

  <div v-else class="detail-view">
    <nav class="breadcrumb">
      <RouterLink to="/">总目录</RouterLink>
      <span>/</span>
      <span>{{ book.category }}</span>
      <span>/</span>
      <strong>{{ book.title }}</strong>
    </nav>

    <section class="book-hero">
      <BookCover :book="book" size="lg" />

      <div class="book-hero__content">
        <span class="eyebrow">{{ book.country }} · {{ book.era }} · {{ book.category }}</span>
        <h1>{{ book.title }}</h1>
        <p class="book-subtitle">{{ content.subtitle || book.summary }}</p>
        <p class="book-author">{{ book.author }}</p>

        <div class="meta-pills">
          <span>{{ book.category }}</span>
          <span>{{ book.level }}</span>
          <span>约 {{ book.readingMinutes }} 分钟读完</span>
          <span v-if="content.lecture?.durationMinutes">讲稿 {{ content.lecture.durationMinutes }} 分钟</span>
        </div>

        <div class="book-tags large">
          <span v-for="tag in book.tags" :key="tag">{{ tag }}</span>
        </div>

        <div class="hero-actions">
          <button class="primary-btn" type="button" @click="playEssence">
            {{ speech.speaking ? '重新朗读精华' : '朗读阅读精华' }}
          </button>
          <RouterLink class="ghost-btn" :to="`/book/${book.id}/read`">阅读原书 / 导入本地书</RouterLink>
          <button class="ghost-btn" type="button" @click="toggleFavorite">
            {{ favorite ? '★ 已收藏' : '☆ 收藏' }}
          </button>
        </div>

        <div class="dossier-actions">
          <span class="dossier-source">
            {{ source === 'local' ? '本机版本（已生成/编辑）' : source === 'builtin' ? '馆内精读档案' : '尚无精读详情' }}
          </span>
          <button
            v-if="source === 'none'"
            class="primary-btn small"
            type="button"
            :disabled="Boolean(aiBusy)"
            @click="generateDetail"
          >
            {{ aiBusy === 'generate' ? '正在生成…' : '⚡ AI 生成精读详情' }}
          </button>
          <button v-else class="ghost-btn small" type="button" @click="startEdit">✎ 编辑精读详情</button>
          <button v-if="source !== 'none'" class="ghost-btn small" type="button" @click="showRevise = !showRevise">🤖 AI 修改</button>
          <button v-if="source === 'local'" class="ghost-btn small" type="button" @click="resetLocal">恢复默认</button>
        </div>

        <p v-if="aiBusy === 'generate'" class="inline-note">正在生成精读内容，请保持页面打开。</p>
        <p v-if="aiBusy === 'revise'" class="inline-note">正在按你的要求修改精读内容…</p>
        <p v-if="aiError" class="inline-note error">{{ aiError }}</p>
        <p v-if="aiNotice" class="inline-note">{{ aiNotice }}</p>
      </div>
    </section>

    <section v-if="showRevise && source !== 'none'" class="panel revise-panel">
      <h3>修改这份精读</h3>
      <div class="field-row">
        <label class="field">
          <span>修改范围</span>
          <select v-model="reviseScope">
            <option value="all">精读详情全部</option>
            <option value="essence">仅阅读精华</option>
            <option value="lecture">仅演讲书稿</option>
          </select>
        </label>
        <label class="field grow">
          <span>修改要求</span>
          <input
            v-model="reviseInstruction"
            type="text"
            placeholder="例如：把演讲书稿改得更口语化；或：补充关于女性角色的分析"
            @keyup.enter="reviseWithAI"
          />
        </label>
      </div>
      <div class="custom-actions">
        <button class="primary-btn small" type="button" :disabled="Boolean(aiBusy)" @click="reviseWithAI">
          {{ aiBusy === 'revise' ? '正在修改…' : '🤖 提交修改' }}
        </button>
        <button class="ghost-btn small" type="button" @click="showRevise = false">收起</button>
      </div>
    </section>

    <section v-if="editMode && draft" class="panel edit-panel">
      <h3>编辑精读详情（保存在本机）</h3>
      <label class="field"><span>副标题</span><input v-model="draft.subtitle" type="text" /></label>
      <label class="field"><span>作者小传</span><input v-model="draft.authorIntro" type="text" /></label>
      <label class="field"><span>一句话简介</span><input v-model="draft.summary" type="text" /></label>

      <h4>故事情节（每行一段）</h4>
      <textarea v-model="draft.essence.storyText" rows="5" class="edit-area"></textarea>

      <h4>阅读所获（每行一条）</h4>
      <textarea v-model="draft.essence.insightsText" rows="5" class="edit-area"></textarea>

      <h4>必背章句</h4>
      <div v-for="(quote, qi) in draft.essence.quotes" :key="`q-${qi}`" class="edit-quote-row">
        <input v-model="quote.text" type="text" placeholder="金句原文" />
        <input v-model="quote.source" type="text" placeholder="出处" />
        <input v-model="quote.note" type="text" placeholder="短评（可选）" />
        <button class="text-btn" type="button" @click="draft.essence.quotes.splice(qi, 1)">删除</button>
      </div>
      <button class="ghost-btn small" type="button" @click="addQuoteRow">＋ 添加章句</button>

      <h4>演讲书稿</h4>
      <label class="field"><span>讲稿标题</span><input v-model="draft.lecture.title" type="text" /></label>
      <label class="field"><span>开场白（每行一段）</span><textarea v-model="draft.lecture.openingText" rows="3" class="edit-area"></textarea></label>
      <h4>讲稿段落</h4>
      <div v-for="(sec, si) in draft.lecture.sections" :key="`s-${si}`" class="edit-section">
        <input v-model="sec.title" type="text" placeholder="小节标题" />
        <textarea v-model="sec.paragraphsText" rows="3" class="edit-area" placeholder="每行一段"></textarea>
        <button class="text-btn" type="button" @click="draft.lecture.sections.splice(si, 1)">删除本节</button>
      </div>
      <button class="ghost-btn small" type="button" @click="addSectionRow">＋ 添加段落</button>
      <label class="field"><span>收束（每行一段）</span><textarea v-model="draft.lecture.closingText" rows="3" class="edit-area"></textarea></label>

      <div class="custom-actions">
        <button class="primary-btn small" type="button" @click="saveEdit">保存修改</button>
        <button class="ghost-btn small" type="button" @click="cancelEdit">取消</button>
      </div>
    </section>

    <SpeechBar :speech="speech" :label="speech.label || '正在朗读'" />

    <p v-if="content.aiGenerated" class="pending-note">
      本书由 AI 根据书名生成，内容请自行校对；如需修改，可在“我的书架”中删除后重新生成。
    </p>

    <div class="detail-layout">
      <div class="detail-main">
        <div class="tab-bar" role="tablist">
          <button
            v-for="item in tabs"
            :key="item.key"
            class="tab-btn"
            :class="{ active: tab === item.key }"
            type="button"
            role="tab"
            @click="switchTab(item.key)"
          >
            <span>{{ item.label }}</span>
            <small>{{ item.hint }}</small>
          </button>
          <button
            v-if="tab === 'essence'"
            class="tab-play"
            type="button"
            :disabled="!essenceSegments.length"
            @click="playEssence"
          >
            ▶ 朗读本页
          </button>
          <button
            v-else-if="tab === 'lecture'"
            class="tab-play"
            type="button"
            :disabled="!lectureSegments.length"
            @click="playLecture"
          >
            ▶ 朗读讲稿
          </button>
          <button
            v-else
            class="tab-play"
            type="button"
            :disabled="!chapterSegments.length"
            @click="playChapter"
          >
            ▶ 朗读导读
          </button>
        </div>

        <section v-if="tab === 'essence'" class="panel">
          <div v-if="content.isFallback" class="pending-note">
            这本作品的精读档案正在整理中。以下为目录速览，你仍然可以导入本地原书按章节阅读。
          </div>

          <article class="essence-section">
            <header>
              <span class="section-index">壹</span>
              <h2>故事情节</h2>
              <small>先把故事读明白</small>
            </header>
            <p
              v-for="block in storyBlocks"
              :key="`story-${block.index}`"
              class="reading-text"
              :class="{ 'is-speaking': isActive(block.index) }"
              @click="speech.play(essenceSegments, { label: '正在朗读 · 阅读精华', start: block.index })"
            >
              {{ block.text }}
            </p>
            <p v-if="!storyBlocks.length" class="muted-text">精读情节整理中，可先阅读总目录简介。</p>
          </article>

          <article class="essence-section">
            <header>
              <span class="section-index">贰</span>
              <h2>阅读所获</h2>
              <small>读完能带走什么</small>
            </header>
            <div class="insight-list">
              <div
                v-for="block in insightBlocks"
                :key="`insight-${block.index}`"
                class="insight-item"
                :class="{ 'is-speaking': isActive(block.index) }"
                @click="speech.play(essenceSegments, { label: '正在朗读 · 阅读精华', start: block.index })"
              >
                <span>{{ String(block.index - storyBlocks.length + 1).padStart(2, '0') }}</span>
                <p>{{ block.text }}</p>
              </div>
            </div>
            <p v-if="!insightBlocks.length" class="muted-text">阅读所获整理中。</p>
          </article>

          <article class="essence-section">
            <header>
              <span class="section-index">叁</span>
              <h2>必背精华章句</h2>
              <small>值得抄写、背诵的句子</small>
            </header>
            <div class="quote-list">
              <blockquote
                v-for="quote in quoteBlocks"
                :key="`quote-${quote.index}`"
                class="quote-card"
                :class="{ 'is-speaking': isActive(quote.index) }"
                @click="speech.play(essenceSegments, { label: '正在朗读 · 阅读精华', start: quote.index })"
              >
                <p>“{{ quote.text }}”</p>
                <footer>
                  <span>{{ quote.source }}</span>
                  <small v-if="quote.note">{{ quote.note }}</small>
                </footer>
              </blockquote>
            </div>
            <p v-if="!quoteBlocks.length" class="muted-text">本作品的金句仍在整理中。</p>
          </article>
        </section>

        <section v-else-if="tab === 'lecture'" class="panel">
          <div class="lecture-head">
            <div>
              <span class="eyebrow">每日精读 · 演讲书稿</span>
              <h2>{{ content.lecture?.title || `每天精读一本书：${book.title}` }}</h2>
              <p>
                <span v-if="content.lecture?.tone">语气：{{ content.lecture.tone }}</span>
                <span v-if="content.lecture?.durationMinutes"> · 预计 {{ content.lecture.durationMinutes }} 分钟</span>
                <span v-if="!content.lecture?.durationMinutes"> · 讲稿整理中，以下为导读提纲</span>
              </p>
            </div>
            <button class="primary-btn small" type="button" :disabled="!lectureSegments.length" @click="playLecture">
              ▶ 从头播放
            </button>
          </div>

          <div class="lecture-body">
            <template v-for="block in lectureBlocksList" :key="`lecture-${block.index}`">
              <p
                v-if="block.type === 'opening' || block.type === 'paragraph' || block.type === 'closing'"
                class="lecture-paragraph"
                :class="{ 'is-speaking': isActive(block.index), opening: block.type === 'opening', closing: block.type === 'closing' }"
                @click="speech.play(lectureSegments, { label: '正在朗读 · 每日演讲书稿', start: block.index })"
              >
                {{ block.text }}
              </p>
              <h3 v-else class="lecture-heading" :class="{ 'is-speaking': isActive(block.index) }">
                {{ block.text }}
              </h3>
            </template>
          </div>

          <div v-if="!lectureSegments.length" class="pending-note">
            这本作品的演讲书稿正在整理中。你可以在总目录中查看已上线书稿，或先阅读原书。
          </div>
        </section>

        <section v-else class="panel">
          <div class="lecture-head">
            <div>
              <span class="eyebrow">章节导读 · 原书入口</span>
              <h2>按章节进入原书</h2>
              <p>这里提供关键章节的导读与节选；全文请导入你电脑中的电子书。</p>
            </div>
            <RouterLink class="primary-btn small" :to="`/book/${book.id}/read`">打开阅读器</RouterLink>
          </div>

          <div v-if="chapterBlocks.length" class="chapter-list">
            <article
              v-for="chapter in chapterBlocks"
              :key="chapter.title"
              class="chapter-card"
            >
              <header>
                <span class="chapter-number">{{ String(chapter.guideIndex + 1).padStart(2, '0') }}</span>
                <h3>{{ chapter.title }}</h3>
                <button
                  class="icon-btn"
                  type="button"
                  title="朗读本章导读"
                  @click="speech.play([{ text: `${chapter.title}。${chapter.guide || ''}${chapter.excerpt || ''}` }], { label: '正在朗读 · 章节导读' })"
                >
                  ▶
                </button>
              </header>
              <p :class="{ 'is-speaking': isActive(chapter.guideIndex) }">{{ chapter.guide }}</p>
              <blockquote v-if="chapter.excerpt" :class="{ 'is-speaking': isActive(chapter.excerptIndex) }">
                {{ chapter.excerpt }}
              </blockquote>
            </article>
          </div>
          <div v-else class="pending-note">
            章节导读整理中。你可以直接在阅读器中导入本地原书，系统会自动按“第X章 / 第X回”拆分章节。
          </div>
        </section>

        <Recorder />
      </div>

      <aside class="detail-side">
        <section class="side-card author-card">
          <h2>作者介绍</h2>
          <p class="side-text">{{ authorIntro || '作者简介正在核对，暂不展示未核实的内容。' }}</p>
          <div v-if="authorWorksPreview.length" class="author-works">
            <h3>馆藏作品集</h3>
            <RouterLink
              v-for="item in authorWorksPreview"
              :key="item.id"
              :to="`/book/${item.id}`"
            >
              {{ item.title }}
            </RouterLink>
            <details v-if="moreAuthorWorks.length" class="author-works__more">
              <summary>查看其余 {{ moreAuthorWorks.length }} 部作品</summary>
              <RouterLink v-for="item in moreAuthorWorks" :key="item.id" :to="`/book/${item.id}`">
                {{ item.title }}
              </RouterLink>
            </details>
          </div>
          <p v-else class="side-text">暂无其他同作者馆藏作品。</p>
        </section>

        <section class="side-card">
          <h2>作品档案</h2>
          <dl class="fact-list">
            <div><dt>作者</dt><dd>{{ book.author }}</dd></div>
            <div><dt>年代</dt><dd>{{ book.era }}</dd></div>
            <div><dt>国别</dt><dd>{{ book.country }}</dd></div>
            <div><dt>类别</dt><dd>{{ book.category }}</dd></div>

            <div v-if="book.custom"><dt>来源</dt><dd>我的书架 · AI 自定义</dd></div>
            <div v-else><dt>推荐等级</dt><dd>{{ book.level }}</dd></div>
          </dl>
        </section>

        <section class="side-card">
          <h2>朗读提示</h2>
          <p class="side-text">
            语音由浏览器内置的语音合成引擎生成，不消耗流量。若没有中文音色，可在系统的“语音与语言”设置中安装中文语音包。
          </p>
        </section>

        <section class="side-card">
          <h2>相似的书</h2>
          <div class="similar-list">
            <RouterLink v-for="item in similar" :key="item.id" class="similar-item" :to="`/book/${item.id}`">
              <BookCover :book="item" size="xs" />
              <div>
                <strong>{{ item.title }}</strong>
                <span>{{ item.author }} · {{ item.category }}</span>
              </div>
            </RouterLink>
            <p v-if="!similar.length" class="side-text">暂未找到相似的书籍。</p>
          </div>
        </section>
      </aside>
    </div>
  </div>
</template>