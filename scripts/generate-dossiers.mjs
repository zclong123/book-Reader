import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { makeContext, contentBudget, buildStory, buildInsights, buildQuotes, buildLecture, buildChapters, buildSubtitle, buildAuthorIntro } from './logic-base.mjs'
import { QUOTES } from './quotes.mjs'
import { READING_GAINS } from './gains.mjs'
import { EXTERNAL_TEXTS } from './external-texts.mjs'

// 阅读所获、原文节选、公版电子书来源：目录里没有的书一律不编造

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(__dirname, '..')
const csvPath = path.join(root, 'data', 'catalog.csv')
const booksDir = path.join(root, 'data', 'books')

// 必背精华章句：逐本录入本书原文（scripts/quotes.mjs），不再用别的书凑数
const curated = QUOTES

function parseCsv(text) {
  const input = text.replace(/^\uFEFF/, '')
  const rows = []
  let row = []
  let field = ''
  let inQuotes = false
  for (let i = 0; i < input.length; i += 1) {
    const c = input[i]
    if (inQuotes) {
      if (c === '"') {
        if (input[i + 1] === '"') {
          field += '"'
          i += 1
        } else inQuotes = false
      } else field += c
    } else if (c === '"') inQuotes = true
    else if (c === ',') {
      row.push(field)
      field = ''
    } else if (c === '\n') {
      row.push(field)
      rows.push(row)
      row = []
      field = ''
    } else if (c !== '\r') field += c
  }
  if (field !== '' || row.length) {
    row.push(field)
    rows.push(row)
  }
  return rows.filter((r) => r.some((cell) => cell.trim() !== ''))
}

const rows = parseCsv(fs.readFileSync(csvPath, 'utf8'))
const header = rows.shift().map((h) => h.trim())
const idx = Object.fromEntries(header.map((key, i) => [key, i]))

const books = rows.map((row) => {
  const get = (key) => (row[idx[key]] ?? '').trim()
  return {
    id: get('id'),
    title: get('title'),
    author: get('author'),
    country: get('country'),
    era: get('era'),
    category: get('category'),
    level: get('level'),
    batch: Number(get('batch')) || 1,
    readingMinutes: Number(get('reading_minutes')) || 0,
    tags: get('tags').split('|').map((t) => t.trim()).filter(Boolean),
    summary: get('summary'),
  }
})

const only = process.argv[2]
const force = process.argv.includes('--force')
const filter = only && only !== 'all' ? only : null

let created = 0
let skipped = 0
for (const book of books) {
  if (filter && filter !== book.id && filter !== book.category) continue
  const file = path.join(booksDir, book.category, `${book.id}.json`)
  if (fs.existsSync(file) && !force && !filter) {
    skipped += 1
    continue
  }
  const ctx = makeContext(book)
  ctx.readingGains = READING_GAINS[book.id] ?? []
  ctx.external = EXTERNAL_TEXTS[book.id] ?? null
  const localText = path.join(root, 'public', 'books', `${book.id}.txt`)
  ctx.sourceText = ctx.external && fs.existsSync(localText) ? fs.readFileSync(localText, 'utf8').slice(0, 400000) : ''
  const budget = contentBudget(book)
  const highlights = (curated[book.id] ?? []).filter((q) => Array.isArray(q) && q[0])
  const story = buildStory(ctx, budget)
  const insights = buildInsights(ctx, budget)
  const quotes = buildQuotes(ctx, budget, highlights)
  const lecture = buildLecture(ctx, budget, story, insights, quotes)
  const chapters = buildChapters(ctx, budget, story)

  const detail = {
    ...ctx.dossier,
    title: book.title,
    subtitle: buildSubtitle(ctx),
    authorIntro: buildAuthorIntro(ctx),
    country: book.country,
    era: book.era,
    category: book.category,
    tags: book.tags,
    readingMinutes: book.readingMinutes,
    summary: book.summary,
    importance: { 殿堂级: 3, 经典: 2, 重要: 1 }[book.level] ?? 1,
    externalText: ctx.external
      ? {
          source: ctx.external.source,
          sourceId: ctx.external.id,
          sourceUrl: `https://www.gutenberg.org/ebooks/${ctx.external.id}`,
          title: ctx.external.title,
          localFile: ctx.sourceText ? `/books/${book.id}.txt` : '',
        }
      : null,
    id: book.id,
    essence: { story, insights, quotes },
    lecture: {
      title: lecture.title,
      durationMinutes: lecture.durationMinutes,
      tone: lecture.tone,
      opening: lecture.opening,
      sections: lecture.sections,
      closing: lecture.closing,
    },
    chapters,
    similar: [],
  }

  fs.mkdirSync(path.dirname(file), { recursive: true })
  fs.writeFileSync(file, `${JSON.stringify(detail, null, 2)}\n`, 'utf8')
  created += 1
  console.log(`${book.id}\t${story.length}段/${insights.length}思/${quotes.length}句/${chapters.length}章\t${lecture.charCount}字/${lecture.durationMinutes}min`)
}

console.log(`生成档案 ${created} 本，跳过已有 ${skipped} 本`)
