import catalogData from '../../data/catalog.json'

const modules = import.meta.glob('../../data/books/**/*.json', { eager: true })
const details = {}

for (const [path, mod] of Object.entries(modules)) {
  const detail = mod?.default ?? mod
  if (detail?.id) {
    details[detail.id] = detail
  } else {
    const fallbackId = path.split('/').pop()?.replace(/\.json$/, '')
    if (fallbackId && detail && typeof detail === 'object') {
      details[fallbackId] = { ...detail, id: fallbackId }
    }
  }
}

export const catalog = catalogData
export const categories = [...new Set(catalog.map((b) => b.category))]
export const batches = [...new Set(catalog.map((b) => b.batch))].sort((a, b) => a - b)
export const levels = ['殿堂级', '经典', '重要']

export function getBook(id) {
  return catalog.find((b) => b.id === id) ?? null
}

export function getDetail(id) {
  return details[id] ?? null
}

export function hasDetail(id) {
  return Boolean(details[id])
}

export function fallbackDetail(book) {
  const tags = book.tags.join('、')
  return {
    ...book,
    authorIntro: '',
    essence: {
      story: [book.summary],
      insights: [
        `《${book.title}》是${book.era}${book.country}${book.category}中极具代表性的作品，常被列入“必读经典”书单。它值得反复阅读的地方，不只在故事本身，更在于它对人性、社会与时代的长期追问。`,
        `阅读时可以抓住${tags}这几个关键词：先看作品如何提出问题，再看人物如何在处境中做出选择，最后把书中的问题带回自己的生活。`
      ],
      quotes: []
    },
    lecture: {
      title: `每天精读一本书：${book.title}`,
      durationMinutes: 0,
      tone: '平实、亲切',
      opening: `今天要和大家聊的，是${book.country}${book.era}的《${book.title}》。${book.summary} 这本书的精读讲稿正在整理中，下面先带大家从目录、题材和阅读方法三个角度，快速建立对它的整体认识。`,
      sections: [
        {
          title: '一、先认识这本书',
          paragraphs: [
            `《${book.title}》由${book.author}创作，属于${book.category}，全书阅读量约 ${book.readingMinutes} 分钟。它围绕${tags}展开，是理解${book.era}精神面貌的一扇窗口。`,
            '第一次阅读不必追求记住所有细节。先弄清人物关系与核心冲突，再带着问题读第二遍，收获会明显不同。'
          ]
        }
      ],
      closing: '完整的精读精华与 15—30 分钟演讲书稿正在按批次补充。你也可以先在“阅读原书”中导入自己的电子书，按章节慢慢读。'
    },
    chapters: [],
    isFallback: true
  }
}

export function getContent(id) {
  const book = getBook(id)
  if (!book) return null
  return details[id] ?? fallbackDetail(book)
}

export function similarBooks(book, limit = 4) {
  if (!book) return []
  const baseTags = new Set(book.tags)
  return catalog
    .filter((b) => b.id !== book.id)
    .map((b) => {
      let score = 0
      if (b.category === book.category) score += 3
      if (b.country === book.country) score += 1.2
      if (b.era === book.era) score += 0.8
      if (b.level === book.level) score += 0.3
      for (const tag of b.tags) {
        if (baseTags.has(tag)) score += 2
      }
      return { book: b, score }
    })
    .filter((item) => item.score > 0)
    .sort((a, b) => b.score - a.score || a.book.batch - b.book.batch)
    .slice(0, limit)
    .map((item) => item.book)
}

export function stats() {
  const china = catalog.filter((b) => b.country === '中国').length
  const covered = catalog.filter((b) => hasDetail(b.id)).length
  return {
    total: catalog.length,
    batches: batches.length,
    categories: categories.length,
    china,
    covered
  }
}

export function todayBook() {
  const day = Math.floor(Date.now() / 86400000)
  return catalog[day % catalog.length]
}

export function detailBlocks(content) {
  const essence = content?.essence ?? {}
  const story = essence.story ?? []
  const insights = essence.insights ?? []
  const quotes = essence.quotes ?? []
  return {
    story: story.map((text, index) => ({ text, index })),
    insights: insights.map((text, index) => ({ text, index: story.length + index })),
    quotes: quotes.map((q, index) => ({ ...q, index: story.length + insights.length + index }))
  }
}

export function lectureBlocks(content) {
  const lecture = content?.lecture
  if (!lecture) return { blocks: [], segments: [] }
  let index = 0
  const blocks = []
  if (lecture.opening) {
    blocks.push({ type: 'opening', text: lecture.opening, index: index++ })
  }
  for (const section of lecture.sections ?? []) {
    blocks.push({ type: 'heading', text: section.title, index: index++ })
    for (const paragraph of section.paragraphs ?? []) {
      blocks.push({ type: 'paragraph', text: paragraph, index: index++ })
    }
  }
  if (lecture.closing) {
    blocks.push({ type: 'closing', text: lecture.closing, index: index++ })
  }
  return { blocks, segments: blocks.map((b) => ({ text: b.text })) }
}