// 开发态下载代理：在浏览器里点“在线下载原书”时，由本地 Vite 服务代取公版全文，
// 避开 Gutenberg 对浏览器的跨域限制。只允许下载 data/text-manifest.json 中登记的书目。
import fs from 'node:fs'
import path from 'node:path'

export default function gutenbergProxy() {
  return {
    name: 'gutenberg-proxy',
    configureServer(server) {
      const manifestPath = path.join(server.config.root, 'data', 'text-manifest.json')
      const catalogPath = path.join(server.config.root, 'data', 'catalog.json')

      server.middlewares.use('/api/text', async (req, res) => {
        try {
          const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'))
          const catalog = JSON.parse(fs.readFileSync(catalogPath, 'utf8'))
          const id = new URL(req.url, 'http://localhost').searchParams.get('id') ?? ''
          const meta = manifest[id]
          const book = catalog.find((item) => item.id === id)
          if (!meta || !book) {
            res.statusCode = 404
            res.setHeader('Content-Type', 'application/json; charset=utf-8')
            res.end(JSON.stringify({ error: '这本书没有登记可下载的公版全文' }))
            return
          }
          const url = `https://www.gutenberg.org/cache/epub/${meta.sourceId}/pg${meta.sourceId}.txt`
          const upstream = await fetch(url, { signal: AbortSignal.timeout(45000) })
          if (!upstream.ok) throw new Error(`上游返回 ${upstream.status}`)
          res.setHeader('Content-Type', 'text/plain; charset=utf-8')
          res.setHeader('Content-Disposition', `attachment; filename*=UTF-8''${encodeURIComponent(book.title)}.txt`)
          res.end(await upstream.text())
        } catch (error) {
          res.statusCode = 502
          res.setHeader('Content-Type', 'application/json; charset=utf-8')
          res.end(JSON.stringify({ error: `下载失败：${error.message}` }))
        }
      })
    }
  }
}
