export default async function run(page) {
  const out = {}
  await page.goto('http://localhost:5180/')
  await page.waitForTimeout(2500)
  out.cat1 = await page.$$eval('.filter-chip.cat1', (els) => els.map((e) => e.innerText))
  await page.locator('.filter-chip.cat1', { hasText: '外国文学' }).click()
  await page.waitForTimeout(400)
  out.cat2 = await page.$$eval('.filter-chip.cat2', (els) => els.map((e) => e.innerText))
  await page.locator('.filter-chip.cat2', { hasText: '亚非拉文学' }).click()
  await page.waitForTimeout(400)
  out.cat3 = await page.$$eval('.filter-chip.cat3', (els) => els.map((e) => e.innerText))
  await page.locator('.filter-chip.cat3', { hasText: '日本文学' }).click()
  await page.waitForTimeout(700)
  out.result = await page.$eval('.result-line', (e) => e.innerText)
  out.cards = await page.$$eval('.book-card h3', (els) => els.slice(0, 3).map((e) => e.innerText))
  out.pageInfo = await page.$eval('.page-info', (e) => e.innerText).catch(() => 'single page')
  await page.screenshot({ path: 'scripts/shot-catalog.png' })
  return out
}
