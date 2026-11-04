// 公版电子书下载：从 Project Gutenberg 取回公有领域全文，供“阅读原书”使用。
// 用法：
//   node scripts/download-books.mjs --id bainian
//   node scripts/download-books.mjs --ids bainian,jianai,laoren
//   node scripts/download-books.mjs --all            （加 --force 可覆盖已下载文件）
// 结果写入 public/books/<id>.txt，并更新 data/text-manifest.json。
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { EXTERNAL_TEXTS } from './external-texts.mjs'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(__dirname, '..')
const outDir = path.join(root, 'public', 'books')
const manifestPath = path.join(root, 'data', 'text-manifest.json')

// 正文起点关键字：用来截掉 Gutenberg 的版权页、导言与目录
const LANDMARKS = {
  zhanzheng: 'CHAPTER I',
  zuifa: 'PART I',
  shiritan: 'PROEM',
  bianxingji: 'One morning, when Gregor Samsa woke',
  baofali: 'We were in class when the head-master came in',
  fushide: 'TRANSLATED INTO ENGLISH',
  fuhuo: 'MASLOVA IN PRISON',
  hamuleite: 'ACT I',
  beican: 'In 1815, M. Charles-François-Bienvenu Myriel',
  luomiou: 'Two households, both alike in dignity',
}

const stripGutenberg = (text) => {
  let body = text.replace(/\r\n?/g, '\n')
  const start = body.match(/\*\*\*\s*START OF (?:THE|THIS) PROJECT GUTENBERG EBOOK[\s\S]*?\*\*\*/i)
  const end = body.match(/\*\*\*\s*END OF (?:THE|THIS) PROJECT GUTENBERG EBOOK/i)
  if (start) body = body.slice(start.index + start[0].length)
  if (end && end.index > 0) body = body.slice(0, end.index)
  return body.replace(/\n{4,}/g, '\n\n\n').trim()
}

const applyLandmark = (id, body) => {
  const landmark = LANDMARKS[id]
  if (!landmark) return body
  // 取前 12000 字符中的第一个检索位置，避免匹配到版权页里的同名标题
  const start = body.startsWith(landmark) ? 0 : body.slice(0, 12000).indexOf(landmark)
  return start >= 0 ? body.slice(start).trim() : body
}

const args = process.argv.slice(2)
const has = (name) => args.includes(name)
const value = (name) => {
  const i = args.indexOf(name)
  return i >= 0 ? args[i + 1] ?? '' : ''
}

const targets = has('--all')
  ? Object.keys(EXTERNAL_TEXTS)
  : (value('--ids') || value('--id') || '').split(',').map((s) => s.trim()).filter(Boolean)

if (!targets.length) {
  console.error('用法: node scripts/download-books.mjs --id <id> | --ids a,b | --all')
  console.error(`可下载的公版书目（${Object.keys(EXTERNAL_TEXTS).length} 本）：`)
  console.error(Object.keys(EXTERNAL_TEXTS).join(', '))
  process.exit(1)
}

fs.mkdirSync(outDir, { recursive: true })
const manifest = fs.existsSync(manifestPath) ? JSON.parse(fs.readFileSync(manifestPath, 'utf8')) : {}

let ok = 0
const failed = []
for (const id of targets) {
  const meta = EXTERNAL_TEXTS[id]
  if (!meta) {
    failed.push(id)
    console.error(`✗ ${id}：没有登记公版来源（见 scripts/external-texts.mjs）`)
    continue
  }
  const file = path.join(outDir, `${id}.txt`)
  if (fs.existsSync(file) && fs.statSync(file).size > 2000 && !has('--force')) {
    console.log(`· ${id} 已存在，跳过（--force 可覆盖）`)
    continue
  }
  const url = `https://www.gutenberg.org/cache/epub/${meta.id}/pg${meta.id}.txt`
  let body = ''
  for (let attempt = 0; attempt < 3 && !body; attempt += 1) {
    try {
      const res = await fetch(url, { signal: AbortSignal.timeout(40000) })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      body = applyLandmark(id, stripGutenberg(await res.text()))
      if (body.length < 2000) throw new Error('正文过短，可能不是完整文本')
    } catch (err) {
      body = ''
      if (attempt === 2) console.error(`✗ ${id}：${err.message}`)
      else await new Promise((r) => setTimeout(r, 800))
    }
  }
  if (!body) {
    failed.push(id)
    continue
  }
  fs.writeFileSync(file, `${body}\n`, 'utf8')
  manifest[id] = {
    title: meta.zhTitle,
    source: 'Project Gutenberg',
    sourceId: meta.id,
    sourceUrl: `https://www.gutenberg.org/ebooks/${meta.id}`,
    sourceTitle: meta.title,
    file: `/books/${id}.txt`,
    chars: body.length,
    downloadedAt: new Date().toISOString().slice(0, 10)
  }
  ok += 1
  console.log(`✓ ${id} 《${meta.zhTitle}》 ${body.length} 字符`)
}

fs.writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`, 'utf8')
console.log(`完成：成功 ${ok} 本，失败 ${failed.length} 本${failed.length ? `（${failed.join(', ')}）` : ''}`)
