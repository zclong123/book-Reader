import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(__dirname, '..')
const csvPath = path.join(root, 'data', 'catalog.csv')
const outPath = path.join(root, 'data', 'catalog.json')
const publicDir = path.join(root, 'public', 'data')

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
        } else {
          inQuotes = false
        }
      } else {
        field += c
      }
    } else if (c === '"') {
      inQuotes = true
    } else if (c === ',') {
      row.push(field)
      field = ''
    } else if (c === '\n') {
      row.push(field)
      rows.push(row)
      row = []
      field = ''
    } else if (c !== '\r') {
      field += c
    }
  }
  if (field !== '' || row.length) {
    row.push(field)
    rows.push(row)
  }
  return rows.filter((r) => r.some((cell) => cell.trim() !== ''))
}

function toNumber(value, fallback = 0) {
  const n = Number(value)
  return Number.isFinite(n) ? n : fallback
}

const csv = fs.readFileSync(csvPath, 'utf8')
const rows = parseCsv(csv)
const header = rows.shift()
const index = Object.fromEntries(header.map((key, i) => [key.trim(), i]))

const required = ['id', 'title', 'author', 'country', 'era', 'category', 'level', 'batch', 'reading_minutes', 'tags', 'summary']
const optional = ['cat1', 'cat2', 'cat3']
const missing = required.filter((key) => !(key in index))
if (missing.length) {
  console.error(`catalog.csv 缺少字段: ${missing.join(', ')}`)
  process.exit(1)
}

const books = rows.map((row) => {
  const get = (key) => (row[index[key]] ?? '').trim()
  return {
    id: get('id'),
    title: get('title'),
    author: get('author'),
    country: get('country'),
    era: get('era'),
    category: get('category'),
    level: get('level'),
    batch: toNumber(get('batch'), 1),
    readingMinutes: toNumber(get('reading_minutes'), 0),
    tags: get('tags').split('|').map((t) => t.trim()).filter(Boolean),
    summary: get('summary'),
    ...(optional.every((k) => k in index)
      ? { cat1: get('cat1'), cat2: get('cat2'), cat3: get('cat3') }
      : {})
  }
})

const ids = new Set()
for (const book of books) {
  if (!book.id) {
    console.error('发现缺少 id 的书籍记录')
    process.exit(1)
  }
  if (ids.has(book.id)) {
    console.error(`发现重复 id: ${book.id}`)
    process.exit(1)
  }
  ids.add(book.id)
}

fs.mkdirSync(path.dirname(outPath), { recursive: true })
fs.writeFileSync(outPath, `${JSON.stringify(books, null, 2)}\n`, 'utf8')
fs.mkdirSync(publicDir, { recursive: true })
fs.copyFileSync(csvPath, path.join(publicDir, 'catalog.csv'))

const booksDir = path.join(root, 'data', 'books')
function collectDetailIds(dir, ids = []) {
  if (!fs.existsSync(dir)) return ids
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      collectDetailIds(full, ids)
    } else if (entry.name.endsWith('.json')) {
      ids.push(entry.name.replace(/\.json$/, ''))
    }
  }
  return ids
}
const detailIds = collectDetailIds(booksDir)
const covered = books.filter((b) => detailIds.includes(b.id)).length
const chinaCount = books.filter((b) => b.country === '中国').length
const categories = [...new Set(books.map((b) => b.category))]
const batches = [...new Set(books.map((b) => b.batch))]

console.log(`目录已生成: ${books.length} 本作品 / ${batches.length} 个批次 / ${categories.length} 个类别 / 中国作品 ${chinaCount} 本`)
console.log(`精读档案: ${covered} 本已就绪，${books.length - covered} 本等待扩充`)
console.log(`输出: ${path.relative(root, outPath)}`)