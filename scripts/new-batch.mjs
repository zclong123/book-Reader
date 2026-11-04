import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(__dirname, '..')
const csvPath = path.join(root, 'data', 'catalog.csv')
const booksDir = path.join(root, 'data', 'books')

const arg = process.argv[2]
const batch = Number(arg)
if (!Number.isFinite(batch) || batch < 1) {
  console.error('用法: npm run new-batch -- <批次号>')
  process.exit(1)
}

const lines = fs.readFileSync(csvPath, 'utf8').replace(/^\uFEFF/, '').trim().split(/\r?\n/)
const header = lines[0].split(',')
const rows = lines.slice(1).map((line) => {
  const cells = line.split(',')
  const item = {}
  header.forEach((key, i) => {
    item[key] = (cells[i] ?? '').trim()
  })
  return item
})

const targets = rows.filter((r) => Number(r.batch) === batch)
if (!targets.length) {
  console.error(`批次 ${batch} 没有找到书籍，请先补充 catalog.csv`)
  process.exit(1)
}

fs.mkdirSync(booksDir, { recursive: true })
let created = 0
for (const book of targets) {
  const categoryDir = path.join(booksDir, book.category)
  const file = path.join(categoryDir, `${book.id}.json`)
  if (fs.existsSync(file)) continue
  fs.mkdirSync(categoryDir, { recursive: true })
  const template = {
    id: book.id,
    title: book.title,
    subtitle: `第 ${batch} 批 · ${book.category}`,
    author: book.author,
    authorIntro: '',
    country: book.country,
    era: book.era,
    category: book.category,
    tags: book.tags.split('|').filter(Boolean),
    readingMinutes: Number(book.reading_minutes) || 0,
    summary: book.summary,
    essence: {
      story: [],
      insights: [],
      quotes: []
    },
    lecture: {
      title: `每天精读一本书：${book.title}`,
      durationMinutes: 0,
      tone: '',
      opening: '',
      sections: [],
      closing: ''
    },
    chapters: [],
    similar: []
  }
  fs.writeFileSync(file, `${JSON.stringify(template, null, 2)}\n`, 'utf8')
  created += 1
}

console.log(`批次 ${batch}: 新建 ${created} 个档案模板，跳过 ${targets.length - created} 个已存在档案`)