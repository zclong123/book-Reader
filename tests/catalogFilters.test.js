import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { filterByTaxonomy } from '../src/data/catalogFilters.js'

const catalog = JSON.parse(readFileSync(new URL('../data/catalog.json', import.meta.url), 'utf8'))
const books = [
  { id: 'a1', title: '甲书', cat1: '中国文学', cat2: '古典文学', cat3: '经典小说' },
  { id: 'a2', title: '乙书', cat1: '中国文学', cat2: '古典文学', cat3: '经典小说' },
  { id: 'b1', title: '丙书', cat1: '外国文学', cat2: '欧美文学', cat3: '经典小说' },
  { id: 'a1', title: '重复甲书', cat1: '中国文学', cat2: '古典文学', cat3: '经典小说' }
]

test('taxonomy filters stay scoped to the selected parent and child', () => {
  assert.deepEqual(
    filterByTaxonomy(books, { cat1: '中国文学', cat2: '古典文学', cat3: '经典小说' }).map((book) => book.id),
    ['a1', 'a2']
  )
  assert.deepEqual(
    filterByTaxonomy(books, { cat1: '外国文学', cat2: '欧美文学', cat3: '经典小说' }).map((book) => book.id),
    ['b1']
  )
})

test('taxonomy results never include the same book ID more than once', () => {
  const result = filterByTaxonomy(books)
  assert.deepEqual(result.map((book) => book.id), ['a1', 'a2', 'b1'])
})

test('every catalog taxonomy branch returns unique books from that branch', () => {
  const branches = new Set(catalog.map((book) => `${book.cat1}\t${book.cat2}\t${book.cat3}`))

  for (const branch of branches) {
    const [cat1, cat2, cat3] = branch.split('\t')
    const result = filterByTaxonomy(catalog, { cat1, cat2, cat3 })
    const ids = result.map((book) => book.id)

    assert.ok(result.length > 0, `expected results for ${branch}`)
    assert.ok(result.every((book) => book.cat1 === cat1 && book.cat2 === cat2 && book.cat3 === cat3))
    assert.equal(new Set(ids).size, ids.length, `duplicate IDs for ${branch}`)
  }
})
