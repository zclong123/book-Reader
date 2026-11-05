/**
 * Android App 界面快速预览（开发辅助）
 *
 * 把 android/ 下的 Compose 页面结构用浏览器可渲染的 HTML 一比一复刻出来，
 * 方便在没有模拟器时检查布局、文案与阅读数据。仅用于开发预览，不参与打包。
 *
 *   node src/android-preview/mock/server.mjs   → 打开 http://localhost:5199
 */
import fs from 'node:fs'
import http from 'node:http'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..')
const assets = path.join(root, 'android', 'assets')
const port = Number(process.env.PORT || 5199)

const readBomless = (file) => fs.readFileSync(file, 'utf8').replace(/^\uFEFF/, '')

let catalogCache = null
function catalog() {
  if (!catalogCache) catalogCache = JSON.parse(readBomless(path.join(assets, 'catalog.json')))
  return catalogCache
}

let dossierOffsets = null
function indexDossiers() {
  if (dossierOffsets) return dossierOffsets
  const text = readBomless(path.join(assets, 'dossiers.json'))
  const map = new Map()
  let start = 0
  while (true) {
    const found = text.indexOf('\n@@DOSSIER@@\n', start)
    const end = found < 0 ? text.length : found
    const chunk = text.slice(start, end)
    const id = /"id":"([^"]+)"/.exec(chunk)?.[1]
    if (id) map.set(id, chunk)
    if (found < 0) break
    start = found + 13
  }
  dossierOffsets = map
  return map
}

function similar(book, limit = 6) {
  const baseTags = new Set(book.tags ?? [])
  return catalog()
    .filter((item) => item.id !== book.id)
    .map((item) => {
      let score = 0
      if (item.category === book.category) score += 3
      if (item.cat3 && item.cat3 === book.cat3) score += 2
      if (item.country === book.country) score += 1.2
      if (item.era === book.era) score += 0.8
      if (item.author === book.author) score += 2.5
      for (const tag of item.tags ?? []) if (baseTags.has(tag)) score += 2
      return { item, score }
    })
    .filter((entry) => entry.score > 0)
    .sort((a, b) => b.score - a.score)
    .slice(0, limit)
    .map((entry) => entry.item)
}

function fallback(book) {
  return {
    id: book.id,
    title: book.title,
    subtitle: book.summary,
    author: book.author,
    authorIntro: '',
    category: book.category,
    summary: book.summary,
    tags: book.tags,
    story: [book.summary],
    insights: ['精读内容整理中。'],
    quotes: [],
    lecture: { title: `每天精读一本书：${book.title}`, durationMinutes: 0, tone: '平实、亲切', opening: book.summary, sections: [], closing: '讲稿整理中。' },
    chapters: [],
    isFallback: true
  }
}

function api(url) {
  if (url.pathname === '/api/stats') {
    const books = catalog()
    return { total: books.length, categories: new Set(books.map((b) => b.category)).size, covered: indexDossiers().size }
  }
  if (url.pathname === '/api/books') {
    const keyword = (url.searchParams.get('q') || '').trim().toLowerCase()
    const page = Number(url.searchParams.get('page') || 1)
    const list = catalog().filter((book) => !keyword || `${book.title} ${book.author} ${book.summary}`.toLowerCase().includes(keyword))
    return { total: list.length, page, items: list.slice((page - 1) * 20, page * 20) }
  }
  if (url.pathname.startsWith('/api/book/')) {
    const id = decodeURIComponent(url.pathname.slice('/api/book/'.length))
    const book = catalog().find((item) => item.id === id)
    if (!book) return null
    const raw = indexDossiers().get(id)
    return { book, dossier: raw ? JSON.parse(raw) : fallback(book), similar: similar(book) }
  }
  return undefined
}

const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8' }

http
  .createServer((req, res) => {
    const url = new URL(req.url, `http://localhost:${port}`)
    if (url.pathname.startsWith('/api/')) {
      const data = api(url)
      res.writeHead(data === undefined ? 404 : 200, { 'Content-Type': 'application/json; charset=utf-8' })
      res.end(JSON.stringify(data ?? {}))
      return
    }
    const file = url.pathname === '/' ? 'index.html' : url.pathname.slice(1)
    const full = path.join(path.dirname(fileURLToPath(import.meta.url)), file)
    if (!full.startsWith(path.dirname(fileURLToPath(import.meta.url))) || !fs.existsSync(full)) {
      res.writeHead(404)
      res.end('not found')
      return
    }
    res.writeHead(200, { 'Content-Type': types[path.extname(full)] || 'application/octet-stream' })
    res.end(fs.readFileSync(full))
  })
  .listen(port, () => console.log(`Android 界面预览：http://localhost:${port}`))
