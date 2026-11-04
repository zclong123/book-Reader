import fs from 'node:fs'

const dir = 'scripts'
const files = fs.readdirSync(dir).filter((f) => /^books-extra-\d+\.json$/.test(f)).sort((a, b) => Number(a.match(/\d+/)[0]) - Number(b.match(/\d+/)[0]))
const clean = []
for (const f of files) {
  const data = JSON.parse(fs.readFileSync(`${dir}/${f}`, 'utf8'))
  for (const [category, list] of Object.entries(data)) {
    for (const item of list) {
      if (!Array.isArray(item) || item.length !== 8) {
        clean.push({ file: f, item })
        continue
      }
      const [id, title, author, era, summary, level, minutes, tags] = item
      if (!id || !title || !/^[a-z0-9-]+$/.test(id) || !summary || !minutes || !/^[^|]+(\|[^|]+)*$/.test(tags)) {
        clean.push({ file: f, item })
      }
    }
  }
}
console.log(`bad entries: ${clean.length}`)
clean.forEach((c) => console.log(c.file, JSON.stringify(c.item)))
