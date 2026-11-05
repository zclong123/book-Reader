// 用 HTML 复刻 Android 端各页面结构，便于快速检查布局与数据（仅开发预览）
const phoneA = document.getElementById('stage')
let stats = {}
let catalogPage = { items: [], total: 0 }
let detail = null
let keyword = ''
let page = 1
let tab = 0

const esc = (text) =>
  String(text ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]))

const cover = (book, size = '') =>
  `<div class="cover ${size}"><span class="lv">${esc(book.level || '经典')}</span>${esc(book.title)}<span class="au">${esc(book.author)}</span></div>`

const shell = (title, subtitle, body, active = 0) => `
  <div class="phone">
    <div class="topbar"><h1>${esc(title)}</h1><small>${esc(subtitle)}</small></div>
    <div class="body">${body}</div>
    <div class="bottom">
      ${['总目录', '我的书架', '添加书籍', '关于'].map((label, i) => `<div class="${i === active ? 'on' : ''}"><b>${['册', '藏', '＋', '≡'][i]}</b>${label}</div>`).join('')}
    </div>
  </div>`

function catalogBody() {
  return `
    <div class="hero">
      <div class="eye">${stats.total} 部名著 · ${stats.categories} 个类别</div>
      <h2>把一部名著，读成自己的东西。</h2>
      <p>阅读精华、二十五到三十分钟的每日讲稿、可跟读的语音，也可以按章节读原书。</p>
      <div class="row"><button class="btn primary">今日精读</button><button class="btn">＋ 添加自定义书</button></div>
    </div>
    <input class="search" id="q" placeholder="搜索书名、作者、朝代、主题" value="${esc(keyword)}" />
    <div class="chips"><span class="chip on">全部分类 ${stats.total}</span><span class="chip">中国文学</span><span class="chip">外国文学</span><span class="chip">人文社科</span></div>
    <div class="chips"><span class="chip on">全部年代</span><span class="chip">先秦</span><span class="chip">宋元</span><span class="chip">清代及以前</span><span class="chip">当代</span></div>
    <div class="muted">共 ${catalogPage.total} 部作品 · 每页最多 20 部</div>
    ${catalogPage.items
      .map(
        (book) => `<div class="card book" data-id="${esc(book.id)}"><div>${cover(book)}</div><div>
          <span class="muted" style="color:var(--primary)">${esc(book.cat3 || book.category)}</span>
          <span class="badge">仅简介</span>
          <h3>${esc(book.title)}</h3>
          <div class="meta">${esc(book.author)} · ${esc(book.era)} · ${esc(book.country)}</div>
          <div class="sum">${esc(book.summary)}</div>
          <div class="row" style="justify-content:space-between"><span class="muted">约 ${book.readingMinutes} 分钟读完</span><span class="muted">☆ 收藏</span></div>
        </div></div>`
      )
      .join('')}
    <div class="pager">上一页 · 第 ${catalogPage.page} 页 · 下一页</div>`
}

function detailBody() {
  const { book, dossier, similar } = detail
  const essence = `
    <div class="sec">壹 · 故事情节</div>
    ${dossier.story.map((text) => `<div class="p">${esc(text)}</div>`).join('')}
    <div class="sec">贰 · 阅读所获</div>
    ${dossier.insights.map((text, i) => `<div class="p"><b>${String(i + 1).padStart(2, '0')}</b> ${esc(text)}</div>`).join('')}
    <div class="sec">叁 · 必背精华章句</div>
    ${dossier.quotes.map((q) => `<div class="quote">“${esc(q.text)}”<div class="muted" style="margin-top:6px">—— ${esc(q.source)}</div></div>`).join('')}`
  const lecture = `<div class="card"><b style="color:var(--primary);font-size:11px">每日精读 · 演讲书稿</b>
      <div class="sec" style="margin-top:4px">${esc(dossier.lecture.title)}</div>
      <div class="muted">语气：${esc(dossier.lecture.tone)} · 预计 ${dossier.lecture.durationMinutes} 分钟</div></div>
    <div class="p">${esc(dossier.lecture.opening)}</div>
    ${dossier.lecture.sections.map((s) => `<div class="sec">${esc(s.title)}</div>${s.paragraphs.map((p) => `<div class="p">${esc(p)}</div>`).join('')}`).join('')}
    <div class="p">${esc(dossier.lecture.closing)}</div>`
  const chapters = dossier.chapters
    .map((c, i) => `<div class="card"><b>${String(i + 1).padStart(2, '0')} ${esc(c.title)}</b><div class="muted" style="margin:6px 0">${esc(c.guide)}</div><div style="font-family:var(--serif);font-size:12px;color:var(--muted)">「${esc(c.excerpt)}」</div></div>`)
    .join('')
  return `
    <div class="row" style="align-items:flex-start">
      ${cover(book, 'lg')}
      <div>
        <div class="muted" style="color:var(--primary)">${esc(book.country)} · ${esc(book.era)} · ${esc(book.category)}</div>
        <h2 style="font-family:var(--serif);margin:4px 0">${esc(book.title)}</h2>
        <div class="muted">${esc(book.author)}</div>
        <div style="font-size:11px;margin:6px 0">${esc(book.summary)}</div>
        <div class="row"><button class="btn primary">▶ 朗读精华</button><button class="btn">☆ 收藏</button></div>
      </div>
    </div>
    <div class="card"><div class="row" style="justify-content:space-between"><span class="muted">馆内精读档案</span>
      <div class="row"><button class="btn">编辑精读详情</button><button class="btn">模型修改</button></div></div></div>
    <div class="speech"><span class="dot"></span><b>语音朗读</b><div class="muted">系统合成语音，可调语速与语气</div>
      <div style="margin-top:6px">语速 1.0×</div><input class="slider" type="range" min="0.6" max="1.8" value="1" step="0.1" />
      <div class="row"><span class="chip">主持人（平稳）</span><span class="chip">普通话 · 音色</span></div></div>
    <div class="tabs">${['阅读精华', '每日演讲书稿', '章节导读'].map((label, i) => `<div class="${i === tab ? 'on' : ''}" data-tab="${i}">${label}</div>`).join('')}</div>
    ${tab === 0 ? essence : tab === 1 ? lecture : chapters}
    <div class="card"><b>跟读练习</b><div class="muted">戴上耳机，用自己的声音读一段；录音只保存在本机。</div>
      <div class="recorder" style="margin-top:10px"><div class="rec-btn"><i></i></div><div><b style="font-size:13px">开始录音</b>
        <div class="wave">${Array.from({ length: 18 }, () => '<i></i>').join('')}</div></div></div></div>
    <div class="card"><div class="sec" style="margin:0 0 6px">作者介绍</div><div style="font-size:13px">${esc(dossier.authorIntro || '作者简介正在整理中。')}</div></div>
    <div class="card side"><div class="sec" style="margin:0">作品档案</div>
      ${[['作者', book.author], ['年代', book.era], ['国别', book.country], ['类别', `${book.cat1} / ${book.cat2} / ${book.cat3}`], ['推荐', book.level], ['篇幅', `约 ${book.readingMinutes} 分钟读完`]]
      .map(([k, v]) => `<div class="kv"><b>${k}</b><span>${esc(v)}</span></div>`)
      .join('')}</div>
    <div class="card"><div class="sec" style="margin:0 0 8px">相似的书</div><div class="grid-sim">
      ${similar.map((item) => `<div>${cover(item)}<div style="margin-top:4px">${esc(item.title)}</div><div class="muted">${esc(item.author)}</div></div>`).join('')}</div></div>`
}

function render() {
  const catalogHtml = shell('古今名著精读馆', '读一部 · 懂一部', catalogBody(), 0)
  const detailHtml = detail
    ? `<div class="phone"><div class="topbar"><h1>古今名著精读馆</h1><small>${esc(detail.book.title)}</small></div><div class="body">${detailBody()}</div></div>`
    : ''
  const shelfHtml = shell(
    '古今名著精读馆',
    '我的书架',
    `<div class="card">收藏与自定义书会记在这台设备上。</div>
     <div class="sec">收藏的书</div><div class="muted">还没有收藏。在总目录中点“☆ 收藏”，书就会出现在这里。</div>
     <div class="sec">自定义书</div>
     <div class="card"><b>还没有自定义书</b><div class="muted">输入书名即可加入书架，也可以用模型生成一份完整精读档案。</div></div>`,
    1
  )
  phoneA.innerHTML = catalogHtml + detailHtml + shelfHtml
  bind()
}

function bind() {
  document.querySelectorAll('.book[data-id]').forEach((node) => {
    node.addEventListener('click', async () => {
      detail = await fetch(`/api/book/${encodeURIComponent(node.dataset.id)}`).then((r) => r.json())
      tab = 0
      render()
    })
  })
  document.querySelectorAll('[data-tab]').forEach((node) => {
    node.addEventListener('click', () => {
      tab = Number(node.dataset.tab)
      render()
    })
  })
  const input = document.getElementById('q')
  if (input) {
    input.addEventListener('input', async () => {
      keyword = input.value
      detail = null
      catalogPage = await fetch(`/api/books?q=${encodeURIComponent(keyword)}&page=${page}`).then((r) => r.json())
      render()
    })
  }
}

const params = new URLSearchParams(location.search)
if (params.get('book')) {
  detail = await fetch(`/api/book/${encodeURIComponent(params.get('book'))}`).then((r) => r.json())
}
stats = await fetch('/api/stats').then((r) => r.json())
catalogPage = await fetch('/api/books').then((r) => r.json())
render()
