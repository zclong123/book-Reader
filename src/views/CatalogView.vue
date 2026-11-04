<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import BookCover from '../components/BookCover.vue'
import { batches, catalog, categories, hasDetail, levels, stats, todayBook } from '../data/library.js'
import catalogCsv from '../../data/catalog.csv?raw'

const router = useRouter()
const query = ref('')
const activeCategory = ref('全部')
const activeBatch = ref('全部')
const activeLevel = ref('全部')
const sortBy = ref('batch')
const favoriteIds = ref(loadFavorites())

const summary = stats()
const today = todayBook()

function loadFavorites() {
  try {
    return JSON.parse(localStorage.getItem('classics:favorites') ?? '[]')
  } catch {
    return []
  }
}

function toggleFavorite(book) {
  const index = favoriteIds.value.indexOf(book.id)
  if (index >= 0) favoriteIds.value.splice(index, 1)
  else favoriteIds.value.push(book.id)
  localStorage.setItem('classics:favorites', JSON.stringify(favoriteIds.value))
}

function isFavorite(id) {
  return favoriteIds.value.includes(id)
}

function openBook(id) {
  router.push(`/book/${id}`)
}

function downloadCatalog() {
  const blob = new Blob([catalogCsv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = '古今名著总目录.csv'
  a.click()
  URL.revokeObjectURL(url)
}

const categoryOptions = computed(() => {
  const counts = new Map()
  for (const book of catalog) counts.set(book.category, (counts.get(book.category) ?? 0) + 1)
  return ['全部', ...categories].map((name) => ({
    name,
    count: name === '全部' ? catalog.length : counts.get(name) ?? 0
  }))
})

const filtered = computed(() => {
  const keyword = query.value.trim().toLowerCase()
  const list = catalog.filter((book) => {
    if (activeCategory.value !== '全部' && book.category !== activeCategory.value) return false
    if (activeBatch.value !== '全部' && book.batch !== Number(activeBatch.value)) return false
    if (activeLevel.value !== '全部' && book.level !== activeLevel.value) return false
    if (!keyword) return true
    const haystack = [book.title, book.author, book.era, book.country, book.category, book.summary, ...book.tags]
      .join(' ')
      .toLowerCase()
    return haystack.includes(keyword)
  })

  const sorted = [...list]
  if (sortBy.value === 'title') sorted.sort((a, b) => a.title.localeCompare(b.title, 'zh-CN'))
  else if (sortBy.value === 'time') sorted.sort((a, b) => b.readingMinutes - a.readingMinutes)
  else if (sortBy.value === 'level') {
    const rank = { 殿堂级: 0, 经典: 1, 重要: 2 }
    sorted.sort((a, b) => (rank[a.level] ?? 9) - (rank[b.level] ?? 9))
  } else {
    sorted.sort((a, b) => a.batch - b.batch)
  }
  return sorted
})

const grouped = computed(() => {
  if (query.value.trim() || sortBy.value !== 'batch') return null
  const map = new Map()
  for (const book of filtered.value) {
    if (!map.has(book.batch)) map.set(book.batch, [])
    map.get(book.batch).push(book)
  }
  return [...map.entries()].sort((a, b) => a[0] - b[0])
})

function resetFilters() {
  query.value = ''
  activeCategory.value = '全部'
  activeBatch.value = '全部'
  activeLevel.value = '全部'
  sortBy.value = 'batch'
}
</script>

<template>
  <div class="catalog-view">
    <section class="catalog-hero">
      <div class="catalog-hero__copy">
        <span class="eyebrow">四百部经典 · 四十批推进</span>
        <h1>把一部名著，<br />读成自己的东西。</h1>
        <p>
          这里有阅读精华、十五到三十分钟的每日讲稿、可跟读的合成语音，
          也可以导入你电脑里的原书，按章节慢慢读。
        </p>
        <div class="hero-actions">
          <button class="primary-btn" type="button" @click="router.push(`/book/${today.id}`)">
            今日精读：{{ today.title }}
          </button>
          <button class="ghost-btn" type="button" @click="downloadCatalog">下载总目录 CSV</button>
        </div>
      </div>

      <div class="hero-stats">
        <article>
          <strong>{{ summary.total }}</strong>
          <span>部作品</span>
        </article>
        <article>
          <strong>{{ summary.batches }}</strong>
          <span>个批次</span>
        </article>
        <article>
          <strong>{{ summary.categories }}</strong>
          <span>个类别</span>
        </article>
        <article>
          <strong>{{ summary.china }}</strong>
          <span>部中国作品</span>
        </article>
      </div>
    </section>

    <section class="catalog-tools">
      <div class="search-row">
        <label class="search-box">
          <span class="search-icon">⌕</span>
          <input v-model="query" type="search" placeholder="搜索书名、作者、朝代、主题，例如：苏轼、志怪、家族" />
          <button v-if="query" type="button" class="search-clear" @click="query = ''">清除</button>
        </label>
        <select v-model="activeBatch" class="select-control" aria-label="按批次筛选">
          <option value="全部">全部批次</option>
          <option v-for="batch in batches" :key="batch" :value="String(batch)">第 {{ batch }} 批</option>
        </select>
        <select v-model="activeLevel" class="select-control" aria-label="按推荐等级筛选">
          <option value="全部">全部等级</option>
          <option v-for="level in levels" :key="level" :value="level">{{ level }}</option>
        </select>
        <select v-model="sortBy" class="select-control" aria-label="排序方式">
          <option value="batch">按批次</option>
          <option value="level">按推荐等级</option>
          <option value="time">按阅读时长</option>
          <option value="title">按书名</option>
        </select>
      </div>

      <div class="chip-row">
        <button
          v-for="item in categoryOptions"
          :key="item.name"
          class="filter-chip"
          :class="{ active: activeCategory === item.name }"
          type="button"
          @click="activeCategory = item.name"
        >
          {{ item.name }} <span>{{ item.count }}</span>
        </button>
        <button v-if="query || activeBatch !== '全部' || activeLevel !== '全部' || activeCategory !== '全部'" class="filter-chip ghost" type="button" @click="resetFilters">
          重置筛选
        </button>
      </div>

      <p class="result-line">
        共 <strong>{{ filtered.length }}</strong> 部作品
        <span v-if="activeCategory !== '全部'"> · {{ activeCategory }}</span>
        <span v-if="activeBatch !== '全部'"> · 第 {{ activeBatch }} 批</span>
        <span v-if="query"> · 关键词“{{ query }}”</span>
      </p>
    </section>

    <template v-if="grouped">
      <section v-for="[batch, books] in grouped" :key="batch" class="batch-section">
        <header class="batch-head">
          <h2>第 {{ batch }} 批</h2>
          <span>{{ books.length }} 部</span>
          <i></i>
        </header>
        <div class="book-grid">
          <article v-for="book in books" :key="book.id" class="book-card" @click="openBook(book.id)">
            <BookCover :book="book" size="sm" />
            <div class="book-card__body">
              <div class="book-card__top">
                <span class="book-category">{{ book.category }}</span>
                <span v-if="hasDetail(book.id)" class="ready-badge">精读已上线</span>
                <span v-else class="pending-badge">速读档案</span>
              </div>
              <h3>{{ book.title }}</h3>
              <p class="book-meta">{{ book.author }} · {{ book.era }} · {{ book.country }}</p>
              <p class="book-summary">{{ book.summary }}</p>
              <div class="book-tags">
                <span v-for="tag in book.tags.slice(0, 3)" :key="tag">{{ tag }}</span>
              </div>
              <div class="book-card__foot">
                <span>约 {{ book.readingMinutes }} 分钟读完</span>
                <button class="star-btn" type="button" :class="{ on: isFavorite(book.id) }" @click.stop="toggleFavorite(book)">
                  {{ isFavorite(book.id) ? '★ 已收藏' : '☆ 收藏' }}
                </button>
              </div>
            </div>
          </article>
        </div>
      </section>
    </template>

    <section v-else class="book-grid">
      <article v-for="book in filtered" :key="book.id" class="book-card" @click="openBook(book.id)">
        <BookCover :book="book" size="sm" />
        <div class="book-card__body">
          <div class="book-card__top">
            <span class="book-category">{{ book.category }}</span>
            <span v-if="hasDetail(book.id)" class="ready-badge">精读已上线</span>
            <span v-else class="pending-badge">速读档案</span>
          </div>
          <h3>{{ book.title }}</h3>
          <p class="book-meta">{{ book.author }} · {{ book.era }} · 第 {{ book.batch }} 批</p>
          <p class="book-summary">{{ book.summary }}</p>
          <div class="book-tags">
            <span v-for="tag in book.tags.slice(0, 3)" :key="tag">{{ tag }}</span>
          </div>
          <div class="book-card__foot">
            <span>约 {{ book.readingMinutes }} 分钟读完</span>
            <button class="star-btn" type="button" :class="{ on: isFavorite(book.id) }" @click.stop="toggleFavorite(book)">
              {{ isFavorite(book.id) ? '★ 已收藏' : '☆ 收藏' }}
            </button>
          </div>
        </div>
      </article>
    </section>

    <section v-if="!filtered.length" class="empty-state">
      <strong>没有找到匹配的作品</strong>
      <p>换一个关键词，或点击“重置筛选”看看全部目录。</p>
      <button class="ghost-btn" type="button" @click="resetFilters">重置筛选</button>
    </section>

    <section class="catalog-note">
      <h2>关于分批扩充</h2>
      <p>
        总目录按每批 10 部逐步扩充，目标收录 1000 部古今名著，并以中国作品为主。
        当前目录已录入 {{ summary.total }} 部，其中 {{ summary.covered }} 部已配好完整精读档案。
        新增档案只需在 <code>data/books/</code> 下补充同名 JSON，重新构建即可。
      </p>
    </section>
  </div>
</template>