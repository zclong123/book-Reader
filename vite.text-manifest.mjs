import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

// 构建时把已下载的公版全文清单注入前端（data/text-manifest.json → src/data/textManifest.json）
export default function textManifest() {
  const root = path.dirname(fileURLToPath(import.meta.url))
  return {
    name: 'text-manifest',
    configResolved() {
      const source = path.join(root, 'data', 'text-manifest.json')
      const target = path.join(root, 'src', 'data', 'textManifest.json')
      const manifest = fs.existsSync(source) ? JSON.parse(fs.readFileSync(source, 'utf8')) : {}
      fs.mkdirSync(path.dirname(target), { recursive: true })
      fs.writeFileSync(target, `${JSON.stringify(manifest, null, 2)}\n`, 'utf8')
    }
  }
}
