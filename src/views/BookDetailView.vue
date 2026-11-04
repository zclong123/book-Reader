<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import BookCover from '../components/BookCover.vue'
import Recorder from '../components/Recorder.vue'
import SpeechBar from '../components/SpeechBar.vue'
import { useSpeech } from '../composables/useSpeech.js'
import { getBook, getContent, lectureBlocks, similarBooks } from '../data/library.js'

const props = defineProps({
  id: { type: String, required: true }
})

const route = useRoute()
const bookId = computed(() => props.id || String(route.params.id))
const book = computed(() => getBook(bookId.value))
const content = computed(() => getContent(bookId.value))
const similar = computed(() => similarBooks(book.value, 4))
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

watch(bookId, () => {
  speech.stop()
  tab.value = 'essence'
  favorite.value = isFavorite(bookId.value)
})

watch(tab, () => {
  if (speech.speaking) speech.stop()
})
</script>

<template>
  <div v-if="!book || !content" class="empty-state page-block">
    <strong>没有找到这部作品</strong>
    <p>它可能还未收录，返回总目录看看其它名著。</p>
    <RouterLink class="primary-btn" to="/">返回总目录</RouterLink>
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
        <span class="eyebrow">{{ book.country }} · {{ book.era }} · 第 {{ book.batch }} 批</span>
        <h1>{{ book.title }}</h1>
        <p class="book-subtitle">{{ content.subtitle || book.summary }}</p>
        <p class="book-author">
          {{ book.author }}
          <span v-if="content.authorIntro"> · {{ content.authorIntro }}</span>
        </p>

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
      </div>
    </section>

    <SpeechBar :speech="speech" :label="speech.label || '正在朗读'" />

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
            这本作品的完整精读档案正在按批次整理中。以下为目录速览，你仍然可以导入本地原书按章节阅读。
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
        <section class="side-card">
          <h2>作品档案</h2>
          <dl class="fact-list">
            <div><dt>作者</dt><dd>{{ book.author }}</dd></div>
            <div><dt>年代</dt><dd>{{ book.era }}</dd></div>
            <div><dt>国别</dt><dd>{{ book.country }}</dd></div>
            <div><dt>类别</dt><dd>{{ book.category }}</dd></div>
            <div><dt>推荐</dt><dd>{{ book.level }}</dd></div>
            <div><dt>批次</dt><dd>第 {{ book.batch }} 批</dd></div>
            <div><dt>阅读量</dt><dd>约 {{ book.readingMinutes }} 分钟</dd></div>
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