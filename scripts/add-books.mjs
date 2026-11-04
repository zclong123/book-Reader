// 追加书目：以中国作品为主，兼顾榜单热门与近代畅销书。
// 运行：node scripts/add-books.mjs   （自动按 10 本/批往后编号，写入 data/catalog.csv）
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(__dirname, '..')
const csvPath = path.join(root, 'data', 'catalog.csv')
const sourceFiles = fs
  .readdirSync(__dirname)
  .filter((name) => /^books-extra(-\d+)?\.json$/.test(name))
  .map((name) => path.join(__dirname, name))
  .sort((a, b) => a.localeCompare(b, 'en', { numeric: true }))
const batch = {}
for (const file of sourceFiles) {
  const data = JSON.parse(fs.readFileSync(file, 'utf8'))
  for (const [category, list] of Object.entries(data)) {
    batch[category] = [...(batch[category] ?? []), ...list]
  }
}
const rows = []
for (const [category, list] of Object.entries(batch)) {
  for (const item of list) {
    const [id, title, author, era, summary, level, minutes, tags] = item
    const country = category === '外国文学' ? '外国' : '中国'
    rows.push({ id, title, author, country, era, category, level, minutes, tags, summary })
  }
}

function parseCsv(text) {
  const input = text.replace(/^\uFEFF/, '')
  const out = []
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
      out.push(row)
      row = []
      field = ''
    } else if (c !== '\r') field += c
  }
  if (field !== '' || row.length) {
    row.push(field)
    out.push(row)
  }
  return out.filter((r) => r.some((cell) => cell.trim() !== ''))
}

const lines = parseCsv(fs.readFileSync(csvPath, 'utf8'))
const header = lines.shift().map((h) => h.trim())
const idx = Object.fromEntries(header.map((key, i) => [key, i]))
const existing = new Set(lines.map((r) => (r[idx.id] ?? '').trim()))
const maxBatch = lines.reduce((m, r) => Math.max(m, Number(r[idx.batch]) || 0), 0)

const fresh = rows.filter((book) => {
  if (existing.has(book.id)) {
    console.warn(`跳过已存在的 id: ${book.id}`)
    return false
  }
  existing.add(book.id)
  return true
})

const csvCell = (value) => {
  const text = String(value ?? '')
  return /[",\n]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text
}

const appended = []
let batchNo = maxBatch
fresh.forEach((book, i) => {
  if (i % 10 === 0) batchNo += 1
  book.batch = Math.min(batchNo, maxBatch + Math.ceil(fresh.length / 10))
})

// 重新按顺序每 10 本连续编批，避免超过目标批次数量
batchNo = maxBatch
fresh.forEach((book, i) => {
  book.batch = maxBatch + Math.floor(i / 10) + 1
  appended.push(
    [
      book.id,
      book.title,
      book.author,
      book.country,
      book.era,
      book.category,
      book.level,
      book.batch,
      book.minutes,
      book.tags,
      book.summary,
    ]
      .map(csvCell)
      .join(',')
  )
})

const csv = fs.readFileSync(csvPath, 'utf8')
const trimmed = csv.replace(/\s+$/, '')
fs.writeFileSync(csvPath, `${trimmed}\n${appended.join('\n')}\n`, 'utf8')

const china = fresh.filter((b) => b.country === '中国').length
console.log(`新增书目 ${fresh.length} 本（中国 ${china} 本），目录总数 ${lines.length + fresh.length} 本，批次 ${maxBatch} → ${maxBatch + Math.ceil(fresh.length / 10)}`)
