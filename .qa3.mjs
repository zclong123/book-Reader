export default async function run(page) {
  const errors = []
  page.on('console', (m) => m.type() === 'error' && errors.push(m.text()))
  const out = {}

  // 1) 有公版全文的书：详情页“必背精华章句”
  await page.goto('http://localhost:4312/#/book/bainian', { waitUntil: 'networkidle' })
  await page.waitForSelector('.book-hero', { timeout: 40000 })
  out.quotes = await page.evaluate(() =>
    [...document.querySelectorAll('.quote-card, .quote-item, blockquote, .quote')].slice(0, 4).map((e) => e.innerText.replace(/\s+/g, ' ').slice(0, 110))
  )
  out.insight = await page.evaluate(() => {
    const sections = document.querySelectorAll('.essence-section')
    return sections[1]?.innerText.slice(0, 240).replace(/\s+/g, ' ')
  })

  // 2) 阅读页：已下载全文
  await page.goto('http://localhost:4312/#/book/bainian/read', { waitUntil: 'networkidle' })
  await page.waitForSelector('.download-note', { timeout: 30000 })
  out.reader = await page.evaluate(() => ({
    note: document.querySelector('.download-note')?.innerText.replace(/\s+/g, ' ').slice(0, 180),
    buttons: [...document.querySelectorAll('.reader-head__actions button, .reader-head__actions a')].map((b) => b.innerText),
  }))
  const openBtn = page.getByRole('button', { name: '阅读已下载全文' })
  await openBtn.click()
  await page.waitForTimeout(1500)
  out.opened = await page.evaluate(() => {
    const nav = [...document.querySelectorAll('.chapter-nav-item strong')].map((e) => e.innerText)
    const body = document.querySelector('.reader-text')?.innerText ?? ''
    return { chapters: nav.length, first: nav.slice(0, 3), sample: body.slice(0, 160).replace(/\s+/g, ' ') }
  })
  await page.screenshot({ path: '.qa-reader.png' })

  // 3) 没有公版全文的书：如实提示
  await page.goto('http://localhost:4312/#/book/hongloumeng/read', { waitUntil: 'networkidle' })
  await page.waitForTimeout(800)
  out.noText = await page.evaluate(() => ({
    note: document.querySelector('.download-note')?.innerText.replace(/\s+/g, ' ').slice(0, 140),
    buttons: [...document.querySelectorAll('.reader-head__actions button')].map((b) => b.innerText),
  }))

  out.errors = errors
  return out
}
