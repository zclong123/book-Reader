const STORAGE_KEY = 'classics:customBooks'

export function loadCustomBooks() {
  try {
    const list = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]')
    return Array.isArray(list) ? list.filter((book) => book && book.id && book.title) : []
  } catch {
    return []
  }
}

export function saveCustomBooks(list) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(list))
}

export function addCustomBook(book) {
  const next = [book, ...loadCustomBooks().filter((item) => item.id !== book.id)]
  saveCustomBooks(next)
  return next
}

export function removeCustomBook(id) {
  const next = loadCustomBooks().filter((item) => item.id !== id)
  saveCustomBooks(next)
  return next
}

export function createCustomId() {
  return `custom-${Date.now().toString(36)}`
}

function text(value, fallback = '') {
  return typeof value === 'string' && value.trim() ? value.trim() : fallback
}

function list(value) {
  return Array.isArray(value) ? value.filter((item) => item !== null && item !== undefined) : []
}

function paragraphs(value) {
  return list(value)
    .map((item) => (typeof item === 'string' ? item.trim() : ''))
    .filter(Boolean)
}

export function normalizeCustomBook(raw = {}, base = {}) {
  const title = text(raw.title, text(base.title, '未命名书籍'))
  const author = text(raw.author, text(base.author, '佚名'))
  const essence = raw.essence ?? {}
  const lecture = raw.lecture ?? {}
  const sections = list(lecture.sections)
    .map((section) => ({
      title: text(section?.title, '精读段落'),
      paragraphs: paragraphs(section?.paragraphs)
    }))
    .filter((section) => section.paragraphs.length)
  const quotes = list(essence.quotes)
    .map((quote) => ({
      text: text(quote?.text),
      source: text(quote?.source, '书中'),
      note: text(quote?.note)
    }))
    .filter((quote) => quote.text)

  return {
    id: text(raw.id, text(base.id, createCustomId())),
    title,
    subtitle: text(raw.subtitle, text(base.subtitle, 'AI 自定义精读档案')),
    author,
    authorIntro: text(raw.authorIntro, text(base.authorIntro, '')),
    country: text(raw.country, text(base.country, '中国')),
    era: text(raw.era, text(base.era, '当代')),
    category: text(raw.category, text(base.category, '文史哲')),
    tags: list(raw.tags).map((tag) => text(tag)).filter(Boolean).slice(0, 6),
    readingMinutes: Number(raw.readingMinutes) > 0 ? Number(raw.readingMinutes) : 120,
    summary: text(raw.summary, text(base.summary, `《${title}》的 AI 自定义精读档案。`)),
    essence: {
      story: paragraphs(essence.story),
      insights: paragraphs(essence.insights),
      quotes
    },
    lecture: {
      title: text(lecture.title, `每天精读一本书：${title}`),
      durationMinutes: Number(lecture.durationMinutes) > 0 ? Number(lecture.durationMinutes) : 15,
      tone: text(lecture.tone, '平实、亲切'),
      opening: text(lecture.opening, ''),
      sections,
      closing: text(lecture.closing, '')
    },
    chapters: list(raw.chapters)
      .map((chapter) => ({
        title: text(chapter?.title),
        guide: text(chapter?.guide),
        excerpt: text(chapter?.excerpt)
      }))
      .filter((chapter) => chapter.title),
    similar: [],
    custom: true,
    batch: 0,
    level: '自定义',
    generatedAt: Date.now()
  }
}

export function createLocalBook({ title, author, category }) {
  const bookTitle = text(title, '未命名书籍')
  const who = text(author, '佚名')
  return normalizeCustomBook({
    title: bookTitle,
    author: who,
    category: text(category, '文史哲'),
    subtitle: '自定义书籍 · 本地速览',
    summary: `《${bookTitle}》的自定义速览档案，完整内容可通过 AI 生成。`,
    tags: ['自定义', '速览'],
    readingMinutes: 120,
    essence: {
      story: [
        `《${bookTitle}》由${who}创作，目前保存在本机书架中，是一份本地速览档案。`,
        '完成 AI 服务设置后，可以继续生成故事梗概、阅读所获、章句赏析、演讲书稿与章节导读。'
      ],
      insights: [
        '自定义书的完整精读内容可以使用 AI 辅助生成，生成后可随时删除或重新生成。',
        '在生成之前，你仍然可以在“阅读原书”中导入本地电子书，按章节阅读。'
      ],
      quotes: []
    },
    lecture: {
      title: `每天精读一本书：${bookTitle}`,
      durationMinutes: 0,
      tone: '平实、亲切',
      opening: `今天要和大家聊的，是《${bookTitle}》。这份档案保存在本机，目前只有速览内容。`,
      sections: [],
      closing: '配置 AI 服务后，可以生成完整的精读讲稿。'
    },
    chapters: []
  })
}