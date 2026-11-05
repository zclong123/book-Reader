export function filterByTaxonomy(books, { cat1 = '全部', cat2 = '全部', cat3 = '全部' } = {}) {
  const seenIds = new Set()

  return books.filter((book) => {
    if (seenIds.has(book.id)) return false
    if (cat1 !== '全部' && book.cat1 !== cat1) return false
    if (cat2 !== '全部' && book.cat2 !== cat2) return false
    if (cat3 !== '全部' && book.cat3 !== cat3) return false
    seenIds.add(book.id)
    return true
  })
}
