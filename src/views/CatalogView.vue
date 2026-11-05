<script setup>
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import BookCover from '../components/BookCover.vue'
import { buildCategoryTree, catalog, hasDetail, levels, stats, todayBook } from '../data/library.js'
import { filterByTaxonomy } from '../data/catalogFilters.js'
import catalogCsv from '../../data/catalog.csv?raw'

const router = useRouter()
const query = ref('')
const activeCat1 = ref('全部')
const activeCat2 = ref('全部')
const activeCat3 = ref('全部')
const activeBatch = ref('全部')
const activeLevel = ref('全部')
const sortBy = ref('category')
const page = ref(1)
const PAGE_SIZE = 20
const favoriteIds = ref(loadFavorites())
const catTree = buildCategoryTree()

const summary = stats()
const today = todayBook()

// 选中一级分类后可用的二级/三级选项
const cat2Options = computed(() => {
  if (activeCat1.value === '全部') return []
  const node = catTree.find((item) => item.name === activeCat1.value)
  return node?.children ?? []
})
const cat3Options = computed(() => {
  if (activeCat2.value === '全部') return []
  return cat2Options.value.find((item) => item.name === activeCat2.value)?.children ?? []
})

watch([query, activeCat1, activeCat2, activeCat3, activeBatch, activeLevel, sortBy], () => {
  page.value = 1
})

function pickCat1(name) {
  activeCat1.value = name
  activeCat2.value = '全部'
  activeCat3.value = '全部'
}
function pickCat2(name) {
  activeCat2.value = name
  activeCat3.value = '全部'
}

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

const eraOptions = computed(() => {
  const counts = new Map()
  for (const book of catalog) {
    const key = eraBucket(book.era)
    counts.set(key, (counts.get(key) ?? 0) + 1)
  }
  const order = ['先秦', '秦汉', '魏晋南北朝', '隋唐五代', '宋元', '明代', '清代', '近现代', '现当代']
  return [...counts.keys()].sort((a, b) => order.indexOf(a) - order.indexOf(b))
})

function eraBucket(era) {
  const text = String(era ?? '')
  const year = Number((text.match(/\d{3,4}/) ?? [])[0])
  if (Number.isFinite(year)) {
    if (year < 1911) return year < 1840 ? '清代及以前' : '晚清民初'
    if (year < 1949) return '民国时期'
    if (year < 1978) return '新中国初期'
    return '当代'
  }
  if (text.includes('先秦')) return '先秦'
  if (text.includes('秦') || text.includes('汉')) return '秦汉'
  if (text.includes('三国') || text.includes('晋') || text.includes('南') || text.includes('北')) return '魏晋南北朝'
  if (text.includes('隋') || text.includes('唐') || text.includes('五代')) return '隋唐五代'
  if (text.includes('宋') || text.includes('元')) return '宋元'
  if (text.includes('明')) return '明代'
  if (text.includes('清')) return '清代及以前'
  if (text.includes('民国')) return '民国时期'
  if (/[0-9]0年代/.test(text)) return '当代'
  return text || '其他'
}

const filtered = computed(() => {
  const keyword = query.value.trim().toLowerCase()
  const list = filterByTaxonomy(catalog, {
    cat1: activeCat1.value,
    cat2: activeCat2.value,
    cat3: activeCat3.value
  }).filter((book) => {
    if (activeBatch.value !== '全部' && eraBucket(book.era) !== activeBatch.value) return false
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
    const rank = { 殿堂级: 0, 经典: 1, 重要: 2, 拓展: 3 }
    sorted.sort((a, b) => (rank[a.level] ?? 9) - (rank[b.level] ?? 9))
  } else if (sortBy.value === 'category') {
    sorted.sort((a, b) => String(a.cat1 ?? '').localeCompare(String(b.cat1 ?? ''), 'zh-CN') || String(a.cat2 ?? '').localeCompare(String(b.cat2 ?? ''), 'zh-CN') || a.title.localeCompare(b.title, 'zh-CN'))
  } else {
    sorted.sort((a, b) => b.readingMinutes - a.readingMinutes)
  }
  return sorted
})

const paged = computed(() => {
  const start = (page.value - 1) * PAGE_SIZE
  return filtered.value.slice(start, start + PAGE_SIZE)
})

const totalPages = computed(() => Math.max(1, Math.ceil(filtered.value.length / PAGE_SIZE)))

function gotoPage(n) {
  if (n < 1 || n > totalPages.value) return
  page.value = n
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

const groups = computed(() => {
  const list = paged.value
  if (query.value.trim() || sortBy.value !== 'category') return null
  const map = new Map()
  for (const book of list) {
    const key = book.custom ? '我的书架' : book.cat3 || book.category
    if (!map.has(key)) map.set(key, [])
    map.get(key).push(book)
  }
  return [...map.entries()].sort((a, b) => b[1].length - a[1].length)
})

function resetFilters() {
  query.value = ''
  activeCat1.value = '全部'
  activeCat2.value = '全部'
  activeCat3.value = '全部'
  activeBatch.value = '全部'
  activeLevel.value = '全部'
  sortBy.value = 'category'
}
</script>

<template>
  <div class="catalog-view">
    <section class="catalog-hero">
      <div class="catalog-hero__copy">
        <span class="eyebrow">{{ summary.total }} 部名著 · {{ summary.categories }} 个类别</span>
        <h1>把一部名著，<br />读成自己的东西。</h1>
        <p>
          这里有阅读精华、十五到三十分钟的每日讲稿、可跟读的合成语音，
          也可以导入你电脑里的原书，按章节慢慢读。
        </p>
        <div class="hero-actions">
          <button class="primary-btn" type="button" @click="router.push(`/book/${today.id}`)">
            今日精读：{{ today.title }}
          </button>
          <RouterLink class="ghost-btn" to="/custom">＋ 添加自定义书</RouterLink>
          <button class="ghost-btn" type="button" @click="downloadCatalog">下载总目录 CSV</button>
        </div>
      </div>

      <div class="hero-stats">
        <article>
          <strong>{{ summary.total }}</strong>
          <span>部作品</span>
        </article>
        <article>
          <strong>{{ summary.categories }}</strong>
          <span>个细分类别</span>
        </article>
        <article>
          <strong>{{ summary.china }}</strong>
          <span>部中国作品</span>
        </article>
        <article>
          <strong>{{ summary.covered }}</strong>
          <span>部精读档案</span>
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
        <select v-model="activeBatch" class="select-control" aria-label="按历史年代筛选">
          <option value="全部">全部年代</option>
          <option v-for="era in eraOptions" :key="era" :value="era">{{ era }}</option>
        </select>
        <select v-model="activeLevel" class="select-control" aria-label="按推荐等级筛选">
          <option value="全部">全部等级</option>
          <option v-for="level in levels" :key="level" :value="level">{{ level }}</option>
        </select>
        <select v-model="sortBy" class="select-control" aria-label="排序方式">
          <option value="category">按类别</option>
          <option value="level">按推荐等级</option>
          <option value="time">按阅读时长</option>
          <option value="title">按书名</option>
        </select>
      </div>

      <div class="chip-row cat-tree">
        <button
          v-for="node in catTree"
          :key="node.name"
          class="filter-chip cat1"
          :class="{ active: activeCat1 === node.name }"
          type="button"
          @click="pickCat1(node.name)"
        >
          {{ node.name }} <span>{{ node.count }}</span>
        </button>
      </div>

      <div v-if="activeCat1 !== '全部'" class="chip-row cat-sub">
        <button class="filter-chip ghost" :class="{ active: activeCat2 === '全部' }" type="button" @click="pickCat2('全部')">全部二级</button>
        <button
          v-for="node2 in cat2Options"
          :key="node2.name"
          class="filter-chip cat2"
          :class="{ active: activeCat2 === node2.name }"
          type="button"
          @click="pickCat2(node2.name)"
        >
          {{ node2.name }} <span>{{ node2.count }}</span>
        </button>
      </div>

      <div v-if="activeCat2 !== '全部'" class="chip-row cat-sub">
        <button class="filter-chip ghost" :class="{ active: activeCat3 === '全部' }" type="button" @click="activeCat3 = '全部'">全部三级</button>
        <button
          v-for="node3 in cat3Options"
          :key="node3.name"
          class="filter-chip cat3"
          :class="{ active: activeCat3 === node3.name }"
          type="button"
          @click="activeCat3 = node3.name"
        >
          {{ node3.name }} <span>{{ node3.count }}</span>
        </button>
      </div>

      <div class="chip-row">
        <button
          class="filter-chip"
          :class="{ active: activeCat1 === '全部' }"
          type="button"
          @click="resetFilters"
        >
          全部分类 <span>{{ catalog.length }}</span>
        </button>
        <button v-if="query || activeBatch !== '全部' || activeLevel !== '全部' || activeCat1 !== '全部'" class="filter-chip ghost" type="button" @click="resetFilters">
          重置筛选
        </button>
      </div>

      <p class="result-line">
        共 <strong>{{ filtered.length }}</strong> 部作品
        <span v-if="activeCat1 !== '全部'"> · {{ activeCat1 }}</span>
        <span v-if="activeCat2 !== '全部'"> · {{ activeCat2 }}</span>
        <span v-if="activeCat3 !== '全部'"> · {{ activeCat3 }}</span>
        <span v-if="activeBatch !== '全部'"> · {{ activeBatch }}</span>
        <span v-if="query"> · 关键词“{{ query }}”</span>
      </p>
    </section>

    <template v-if="groups">
      <section v-for="[name, books] in groups" :key="name" class="batch-section">
        <header class="batch-head">
          <h2>{{ name }}</h2>
          <span>{{ books.length }} 部</span>
          <i></i>
        </header>
        <div class="book-grid">
          <article v-for="book in books" :key="book.id" class="book-card" @click="openBook(book.id)">
            <BookCover :book="book" size="sm" />
            <div class="book-card__body">
              <div class="book-card__top">
                <span class="book-category">{{ book.cat3 || book.category }}</span>
                <span v-if="hasDetail(book.id)" class="ready-badge">精读已上线</span>
                <span v-else class="pending-badge">仅简介</span>
              </div>
              <h3>{{ book.title }}</h3>
              <p class="book-meta">{{ book.author }} · {{ book.era }}<template v-if="book.custom"> · AI 自定义</template><template v-else> · {{ book.country }}</template></p>
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
      <article v-for="book in paged" :key="book.id" class="book-card" @click="openBook(book.id)">
        <BookCover :book="book" size="sm" />
        <div class="book-card__body">
          <div class="book-card__top">
            <span class="book-category">{{ book.cat3 || book.category }}</span>
            <span v-if="hasDetail(book.id)" class="ready-badge">精读已上线</span>
            <span v-else class="pending-badge">仅简介</span>
          </div>
          <h3>{{ book.title }}</h3>
          <p class="book-meta">{{ book.author }} · {{ book.era }}<template v-if="book.custom"> · AI 自定义</template><template v-else> · {{ book.country }}</template></p>
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

    <section v-if="totalPages > 1" class="pagination">
      <button class="ghost-btn small" type="button" :disabled="page <= 1" @click="gotoPage(page - 1)">上一页</button>
      <span class="page-info">第 {{ page }} / {{ totalPages }} 页 · 每页最多 {{ PAGE_SIZE }} 部</span>
      <button class="ghost-btn small" type="button" :disabled="page >= totalPages" @click="gotoPage(page + 1)">下一页</button>
    </section>

    <section v-if="!filtered.length" class="empty-state">
      <strong>没有找到匹配的作品</strong>
      <p>换一个关键词，或点击“重置筛选”看看全部目录。</p>
      <button class="ghost-btn" type="button" @click="resetFilters">重置筛选</button>
    </section>

    <section class="catalog-note">
      <h2>关于精读详情</h2>
      <p>
        馆内 {{ summary.total }} 部作品的总目录只提供一句话简介与分类导航；
        其中 {{ summary.covered }} 部已配有精读内容；更多作品可在详情页使用 AI 辅助生成故事梗概、阅读所获、
        章句赏析、演讲书稿与章节导读，生成后还可以继续编辑。
      </p>
    </section>
  </div>
</template>