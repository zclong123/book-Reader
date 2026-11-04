import catalogData from '../../data/catalog.json'
import { loadCustomBooks } from './customBooks.js'

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

const customBooks = loadCustomBooks()
const customDetails = Object.fromEntries(customBooks.map((book) => [book.id, book]))

export const catalog = [...customBooks, ...catalogData]
export const categories = [...new Set(catalog.map((b) => b.category))]
export const batches = [...new Set(catalogData.map((b) => b.batch))].sort((a, b) => a - b)
export const levels = ['殿堂级', '经典', '重要', '拓展']

/* ---------------- 三级分类体系 ---------------- */
export const TAXONOMY = {
  中国文学: {
    古典文学: {
      古典小说: ['长篇章回', '神魔志怪', '世情讽刺', '侠义公案'],
      诗词曲赋: ['诗词总集', '名家别集', '词曲选本', '戏曲传奇'],
      文史哲: ['经部典籍', '史部史著', '诸子百家', '笔记小品']
    },
    近现代文学: {
      现当代小说: ['长篇小说', '中短篇集', '乡土文学', '都市文学'],
      散文随笔: ['回忆散文', '杂文评论', '游记纪实'],
      话剧戏剧: ['话剧剧本', '戏曲改革']
    },
    武侠与民间: {
      武侠仙侠: ['武侠传奇', '仙侠修行'],
      民间文学: ['民间故事', '说唱曲艺']
    }
  },
  外国文学: {
    欧美文学: {
      英美文学: ['经典长篇', '现代主义', '推理悬疑'],
      法德欧陆: ['现实主义', '浪漫主义', '哲理小说'],
      俄苏文学: ['史诗巨著', '心理小说', '苏维埃文学'],
      古典史诗: ['神话史诗', '古典戏剧']
    },
    亚非拉文学: {
      日本文学: ['物语文学', '近现代小说', '私小说随笔'],
      拉美文学: ['魔幻现实', '结构现实主义'],
      亚非其他: ['阿拉伯故事集', '非洲文学', '南亚文学']
    },
    奇幻科幻: {
      科幻小说: ['硬科幻', '软科幻', '科幻经典'],
      奇幻文学: ['西方奇幻', '童话寓言'],
      反乌托邦: ['反乌托邦', '政治寓言']
    }
  },
  人文社科: {
    思想哲学: {
      西方哲学: ['哲学经典', '存在主义', '政治哲学'],
      中国思想: ['儒家经典', '道家佛学', '近现代思想']
    },
    历史传记: {
      世界历史: ['通史大观', '断代史著', '战争史'],
      人物传记: ['名人传记', '回忆录']
    },
    社会经济: {
      政治法律: ['政治学', '法学经典'],
      经济管理: ['经济学', '管理与商业']
    },
    心理宗教: {
      心理学: ['心理学经典', '大众心理'],
      宗教信仰: ['佛学经典', '基督教经典', '伊斯兰经典']
    }
  },
  科普新知: {
    自然科学: {
      物理宇宙: ['宇宙天文', '理论物理'],
      生命科学: ['生物演化', '医学健康'],
      数学逻辑: ['数学科普', '逻辑思维']
    },
    技术与社会: {
      信息技术: ['人工智能', '网络社会'],
      科学人文: ['科学史话', '环境地理']
    }
  }
}

// 从目录数据提取实际存在的分类树（含数量），供目录页多级浏览
export function buildCategoryTree() {
  const tree = new Map()
  for (const book of catalogData) {
    const c1 = book.cat1 || '其他'
    const c2 = book.cat2 || '综合'
    const c3 = book.cat3 || book.category || '未分类'
    if (!tree.has(c1)) tree.set(c1, new Map())
    const level2 = tree.get(c1)
    if (!level2.has(c2)) level2.set(c2, new Map())
    const level3 = level2.get(c2)
    level3.set(c3, (level3.get(c3) ?? 0) + 1)
  }
  return [...tree.entries()].map(([name, level2]) => ({
    name,
    count: [...level2.values()].reduce((n, m) => n + [...m.values()].reduce((x, y) => x + y, 0), 0),
    children: [...level2.entries()].map(([name2, level3]) => ({
      name: name2,
      count: [...level3.values()].reduce((n, y) => n + y, 0),
      children: [...level3.entries()].map(([name3, count3]) => ({ name: name3, count: count3 }))
    }))
  }))
}

/* ---------------- 读者编辑/生成 的精读详情（本地覆盖层） ---------------- */
const DOSSIER_KEY = 'classics:dossiers'

export function loadDossiers() {
  try {
    const obj = JSON.parse(localStorage.getItem(DOSSIER_KEY) ?? '{}')
    return obj && typeof obj === 'object' ? obj : {}
  } catch {
    return {}
  }
}

export function saveDossier(id, detail) {
  const all = loadDossiers()
  all[id] = { ...detail, savedAt: Date.now() }
  localStorage.setItem(DOSSIER_KEY, JSON.stringify(all))
  return all[id]
}

export function getDossier(id) {
  return loadDossiers()[id] ?? null
}

export function removeDossier(id) {
  const all = loadDossiers()
  delete all[id]
  localStorage.setItem(DOSSIER_KEY, JSON.stringify(all))
}

export function dossierCount() {
  return Object.keys(loadDossiers()).length
}

export function getBook(id) {
  return catalog.find((b) => b.id === id) ?? null
}

export function getDetail(id) {
  return getDossier(id) ?? details[id] ?? customDetails[id] ?? null
}

export function hasDetail(id) {
  return Boolean(getDossier(id) || details[id] || customDetails[id])
}

export function detailSource(id) {
  if (getDossier(id)) return 'local'
  if (details[id] || customDetails[id]) return 'builtin'
  return 'none'
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
      closing: '完整的精读精华与 25—30 分钟演讲书稿正在补充中。你也可以先在“阅读原书”中导入自己的电子书，按章节慢慢读。'
    },
    chapters: [],
    isFallback: true
  }
}

export function getContent(id) {
  const book = getBook(id)
  if (!book) return null
  return getDossier(id) ?? details[id] ?? customDetails[id] ?? fallbackDetail(book)
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