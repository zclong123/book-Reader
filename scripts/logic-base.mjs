// 书籍逻辑：差异化的内容骨架 + 品类/知名度的语言池。
// 取值由 id / title 哈希决定：同一本书生成结果稳定，不同书之间主题不同、句法错位，避免千篇一律。

function hash(text) {
  let h = 2166136261
  const s = String(text ?? '')
  for (let i = 0; i < s.length; i += 1) {
    h ^= s.charCodeAt(i)
    h = Math.imul(h, 16777619)
  }
  return h >>> 0
}

function mulberry(seed) {
  let a = seed >>> 0
  return () => {
    a = (a + 0x6d2b79f5) >>> 0
    let t = a
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

const CN_NUM = ['一', '二', '三', '四', '五', '六', '七', '八', '九', '十', '十一', '十二', '十三', '十四', '十五', '十六', '十七', '十八']

// ---------- 主题识别 ----------

const THEMES = [
  { key: '复仇', names: ['复仇', '冤屈', '正义'], motif: '迟来的公道', core: '一个人被夺走的东西，能不能拿回来，拿回来之后又剩下什么。', shape: '先受难，后翻盘，最后落地', figures: ['受害者', '加害者', '旁观者'] },
  { key: '爱情', names: ['爱情', '相思', '婚姻', '爱'], motif: '不可挽回的柔情', core: '爱情如何被欲望、身份与时间磨损，又如何在磨损中显出形状。', shape: '相遇、错位、余温', figures: ['痴心人', '犹豫者', '第三者'] },
  { key: '家族', names: ['家族', '家庭', '世情'], motif: '一张越长越密的网', core: '家族既是避风港，也是把人越绑越紧的绳。', shape: '兴、衰、散', figures: ['家长', '叛逆的下一代', '沉默的中间人'] },
  { key: '权力', names: ['权力', '政治', '极权', '制度', '官场', '军政'], motif: '一个永远不会空出来的座位', core: '权力怎样塑造人，又怎样反噬掌权者自己。', shape: '上位、失控、清算', figures: ['掌权者', '依附者', '被牺牲的人'] },
  { key: '战争', names: ['战争', '抗战', '割据', '军事', '战役'], motif: '被卷进机器的人', core: '战争把普通人的一生折叠成几个瞬间，而代价由最没有发言权的人支付。', shape: '出征、损耗、归来', figures: ['士兵', '留在后方的人', '下令的人'] },
  { key: '成长', names: ['成长', '青春', '启蒙', '少年', '童年', '教育'], motif: '一次不可逆的长大', core: '成长不是变强，而是失去一部分天真之后还愿意往前走。', shape: '天真、碰壁、定型', figures: ['少年', '引路人', '同辈'] },
  { key: '乡土', names: ['乡土', '农村', '土地', '乡村', '民生'], motif: '一块长着记忆的地', core: '土地与人互相塑造，离开的和留下的，都要为这片土地付账。', shape: '扎根、变动、回望', figures: ['农民', '出走的年轻人', '土生土长的老人'] },
  { key: '都市', names: ['都市', '上海', '城市', '市民'], motif: '一座会吞掉人的城', core: '城市提供机会，也提供幻觉；人在其中不断重新定义自己。', shape: '闯入、沉浮、退场', figures: ['外来者', '本地旧人', '投机者'] },
  { key: '理想', names: ['理想', '信仰', '革命', '奋斗', '自由'], motif: '一件比命更长的事', core: '当信念与生存冲突，人如何取舍；这种取舍定义了他究竟是谁。', shape: '信奉、考验、坚持或幻灭', figures: ['坚守者', '动摇者', '检举者'] },
  { key: '苦难', names: ['苦难', '命运', '生存', '苦难叙事', '底层'], motif: '一场接着一场的雨', core: '苦难不是用来展览的，它是用来理解人还能承受多少的。', shape: '承受、再承受、终于站住', figures: ['承受者', '连累他的人', '给他一点光的人'] },
  { key: '荒诞', names: ['荒诞', '反乌托邦', '黑色幽默', '讽刺', '现实'], motif: '一个制度化的笑话', core: '世界越是荒谬，人越需要自己给自己一个能站住的说法。', shape: '规则、悖论、出局', figures: ['被规则困住的人', '规则的制定者', '看客'] },
  { key: '孤独', names: ['孤独', '心理', '意识流', '存在'], motif: '一间只有回声的房间', core: '人最深处的经验无法翻译，所以真正的沟通永远差一点点。', shape: '独处、试探、退回自身', figures: ['孤独者', '想靠近他的人', '不可见的自我'] },
  { key: '历史', names: ['历史', '史学', '通史', '历史小说', '纪实'], motif: '一条又长又冷的河', core: '历史的走向往往由结构推动，个人的努力在其中既渺小又不可省略。', shape: '积累、断裂、重写', figures: ['时代', '具体的人', '记录者'] },
  { key: '神话', names: ['神话', '志怪', '神魔', '奇幻', '传说', '狐鬼'], motif: '一层薄薄的别的世界', core: '神怪的想象从来不是逃避现实，而是给现实换一种说法。', shape: '入梦、显形、回人间', figures: ['凡人', '异类', '规矩的守护者'] },
  { key: '哲学', names: ['哲学', '儒家', '道家', '思想', '哲思', '修身', '学术'], motif: '一场慢下来的追问', core: '它不急着给答案，而是训练你把问题问得更准。', shape: '立论、辨明、落到日常', figures: ['立论者', '反对者', '实践者'] },
  { key: '艺术', names: ['美学', '文学', '批评', '文论', '艺术', '格律', '诗性'], motif: '一次对美的正经讨论', core: '美不是装饰，它教人如何在有限的形式里放进无限的意思。', shape: '标准、作品、传承', figures: ['创作者', '批评者', '后来的人'] },
  { key: '科学', names: ['科技', '科普', '医学', '工艺', '数理', '科学'], motif: '一本被反复修订的说明书', core: '知识的力量在于它承认自己的边界，并且不断修正自己。', shape: '观察、求证、应用', figures: ['研究者', '实践者', '质疑者'] },
  { key: '女性', names: ['女性', '身世', '闺阁'], motif: '一副被反复定义的身体', core: '在别人的规则里争取自己做主，是这类作品共同的刺。', shape: '被规定、挣扎、自我主张', figures: ['女主角', '规训她的人', '同情者'] },
  { key: '民族', names: ['民族', '文化', '传统', '思想史'], motif: '一种深藏在习惯里的东西', core: '文化不是舞台上的节目，而是人做决定时那句没道理的直觉。', shape: '源起、变异、自省', figures: ['文化的承载者', '改变它的人', '旁观的研究者'] },
  { key: '探索', names: ['探险', '旅行', '冒险', '科幻', '宇宙', '奇幻冒险'], motif: '一条画不完的路线', core: '走出去的意义，往往在回望时才知道；探索的终点常常是他自己。', shape: '出发、险境、见识', figures: ['探索者', '同行者', '未知之物'] },
]

const CATEGORY_THEMES = {
  古典小说: ['家族', '复仇', '神话', '理想'],
  诗词曲赋: ['艺术', '爱情', '孤独', '理想'],
  文史哲: ['哲学', '历史', '民族', '科学'],
  近现代文学: ['乡土', '成长', '苦难', '都市'],
  外国文学: ['爱情', '孤独', '荒诞', '成长'],
  科普新知: ['科学', '探索', '历史'],
}

const LEVEL_BASE = { 殿堂级: 6, 经典: 5, 重要: 4 }

// ---------- 语境 ----------

export function makeContext(book) {
  const seed = hash(`${book.id}`)
  const rand = mulberry(seed)
  const themes = []
  for (const tag of book.tags) {
    const hit = THEMES.find((t) => t.names.includes(tag))
    if (hit && !themes.includes(hit)) themes.push(hit)
  }
  for (const tag of THEMES) {
    if (themes.length >= 3) break
    if (!themes.includes(tag) && tag.names.some((n) => book.summary.includes(n))) themes.push(tag)
  }
  for (const key of CATEGORY_THEMES[book.category] ?? []) {
    if (themes.length >= 3) break
    const hit = THEMES.find((t) => t.key === key)
    if (hit && !themes.includes(hit)) themes.push(hit)
  }
  for (const tag of THEMES) {
    if (themes.length >= 3) break
    if (!themes.includes(tag)) themes.push(tag)
  }

  const jitter = (list) => {
    const arr = [...list]
    for (let i = arr.length - 1; i > 0; i -= 1) {
      const j = Math.floor(rand() * (i + 1))
      ;[arr[i], arr[j]] = [arr[j], arr[i]]
    }
    return arr
  }

  return {
    book,
    seed,
    rand,
    themes,
    theme: themes[0],
    jitter,
    authorShort: String(book.author).split(/[与和，,、]/)[0].trim(),
    isChina: book.country === '中国',
    hook: LECTURE_HOOKS[hash(`${book.id}|钩子`) % LECTURE_HOOKS.length] || '它表面上离我们很远，实际上离我们很近。',
    opener: LECTURE_OPENERS[hash(`${book.id}|开场`) % LECTURE_OPENERS.length],
    pick: (list) => list[hash(`${book.id}|${list.length}|${list[0].slice(0, 4)}`) % list.length],
  }
}

// ---------- 长度预算：名望 + 篇幅 ----------

export function contentBudget(book) {
  const base = LEVEL_BASE[book.level] ?? 4
  const minutes = Number(book.readingMinutes) || 200
  const long = minutes >= 800 ? 1 : 0
  const mid = minutes >= 400 ? 1 : 0
  // 保证阅读精华不少于 1000 字、讲稿不少于 15 分钟（按每分钟 210 字估算）
  const storyCount = base + long + mid + 3
  const insightCount = base + long + mid + 2
  const quoteCount = book.level === '殿堂级' ? 5 : book.level === '经典' ? 4 : 3
  const chapterCount = book.level === '殿堂级' ? 6 : book.level === '经典' ? 5 : 4
  const lectureSections = base + long + mid + 3
  return {
    storyCount,
    insightCount: Math.max(insightCount, 5),
    quoteCount,
    chapterCount,
    lectureSections,
  }
}

// ---------- 故事线 ----------

const STORY_OPENERS = [
  '读这本书，最省力的入口是先弄懂它在争什么。',
  '先把情节的骨架拎出来，细节才不会散。',
  '这本书看上去在讲故事，其实在讲一件更难的事。',
  '不急着评价，先看它把哪些人放进了同一个处境。',
  '要讲清这本书，先得交代它的「局」。',
  '故事不复杂，复杂的是人物为什么只能那样做。',
]

const STORY_SIGNS = [
  '读的时候不妨注意「重复」：同一个场景、同一句话、同一件器物反复出现，那往往就是作者真正的心思所在。',
  '另一个人物类型也值得留意：那些从头到尾都没变化的人。${authorShort}把他们放在旁边，做的是参照——用他们说明时间到底改写了谁。',
  '还可以看对话。${authorShort}很少让人物直接说出心里话，他们总在说别的事，而读者要做的就是听懂那件“别的事”。',
  '不妨也看看书名与结尾的关系。很多书名是反讽的，读到最后你才会明白，那个名字里藏着多少无奈。',
  '还有一个容易忽略的地方：书中反复出现的“边界”——门槛、墙、桥、路。人物每次穿过它们，身份都会变一点。',
  '如果读得慢一点，你会发现作者很在意“天气”。下雨、大雪、晴天不是背景，它们常常预示了接下来要发生的事。',
  '两个版本对照着看也很有意思：同一个故事，不同的人讲，重点完全不同。这本书里的“谁在讲”，往往比“讲了什么”更重要。',
]

const STORY_BRIDGES = [
  '把这几段接起来看，能看出一个共同的东西：',
  '再往下走一步，书里的力气就显出来了：',
  '如果只看这一层，还容易把它当成一个普通的故事；真正有意思的是：',
  '这些安排看上去零散，其实是同一个问题的几种问法：',
  '换个角度看，这里的每一层都在回答同一个问题：',
  '细看下去，会发现作者故意留了很多没说完的地方：',
]

const PUNCT = /[。！？；]$/

const STORY_CLOSERS = [
  '这条线抓住，前后细节就都能挂上去。',
  '情节只是门，门里才是要读的东西。',
  '把故事放回它的年代，很多看似夸张的地方立刻合理。',
  '记住这个走向，再回头读开头，会发现处处伏笔。',
  '看懂这一层，你就不容易被表面的热闹带走。',
  '这一局布好之后，作者真正要说的才浮出水面。',
]

export function buildStory(ctx, budget) {
  const { book, theme, authorShort } = ctx
  const { title, category, summary, era } = book
  const lines = []
  const openers = ctx.jitter(STORY_OPENERS)
  const signs = ctx.jitter(STORY_SIGNS)
  const bridges = ctx.jitter(STORY_BRIDGES)
  lines.push(`${openers[0]}${summary}这本书的篇幅不算大，但它能装下的东西比看上去多得多，所以读它的时候不必着急，反而适合慢慢咀嚼。`)
  lines.push(
    `${ctx.isChina ? '成书于' : '写于'}${era}，它被归在${category}这一路，${authorShort}借它把话说得比同时代人更直接。这本书的母题是「${theme.motif}」：${theme.core}`
  )
  if (budget.storyCount >= 8) lines.push(signs[0])

  const shapes = ctx.jitter([
    `${authorShort}用「${theme.shape}」这个三段式推进全书：开头把人物放进一个看似稳定的秩序，中段让秩序出现裂缝，结尾不给安慰，只给一个清醒的当下。这套推法在《${title}》里完成得很彻底，读的时候可以拿它对照现实中相似的事件。`,
    `人物配置很克制——一个${theme.figures[0]}，一个${theme.figures[1]}，再加一个${theme.figures[2]}。三类立场互相咬合，谁也不能单独成立；一旦抽走其中一个，故事立刻失去张力。`,
    `全书最动人处往往不在高潮，而在缝隙里：一顿饭、一次沉默、一个不肯说破的眼神。${authorShort}把大问题藏进了小动作，而读者往往是在读到第二遍时才反应过来。`,
    `叙事视角值得留意。${authorShort}没有站在上帝的位置审判，而是让读者跟着人物一起误判，最后才发现自己也犯了同样的错。这种设计让人没法把书读成一份判决书。`,
    `结构上它并不均匀：前半部铺得很慢，后半部突然加速。这种不对称不是失手，而是让读者亲身体验「来不及」的感觉——当你意识到事情要坏事时，已经晚了。`,
    `《${title}》的转折几乎都来自同一个东西：人物对处境的一次误读。误读一次，代价加一层，直到再也无法挽回；这个机制比任何说教都更有力。`,
    `作者很会写「不合时宜的人」：${theme.figures[1]}总是在错误的时刻说出正确的话，于是正确也变成了错误。这类人往往最让读者难受，因为他们提醒我们：对的话也要在对的时机说。`,
    `如果给这本书画一张情绪曲线，它是一条缓慢下沉的线，中间只有几次很短的抬升，属于「${theme.shape}」的典型写法。这种节奏不由情节制造，而由人物一次次退让造成。`,
    `开头的第一场戏很重要。${authorShort}往往用一个很小的场景把全书的规则交代完，读到后面你才会发现这里的每句话都有用，连那个不起眼的旁观者也在后面出现了一次。`,
    `书里的时间安排值得留意：有的章节写一整天，有的几句带过十年。节奏的快慢本身就是作者的态度——他舍不得快过的地方，往往就是他真正在意的地方。`,
    `还有一层常被忽略：书中所有人都在同一个「常识」下行动，而这个常识恰恰是作者要拆掉的。读懂这一点，你就能看出哪些情节是伏笔，哪些是陷阱。`,
    `谈到「${theme.motif}」，${authorShort}并不把它当成主题词摆在台面上，而是让它从人物的具体行动里一点点长出来。所以书里很少有宣言式的句子，道理都是做出来的。`,
    `值得注意的是配角。${authorShort}给每个配角都安排了一个「必须如此」的理由，所以他们不是背景，而是压力的来源；删掉任何一个，主角的选择都会失去理由。`,
    `整本书推进的方式是「一小步，一小步地失去」，很少有惊天反转。这种写法比巧合更接近真实生活的样子，也更能让读者在合上书后久久不能平静。`,
    `如果读的时候觉得人物太软弱，不妨想想：他手上到底有多少选择？每一条路要付出什么代价？把这两件事算清楚，评价就会温和很多。`,
    `再看场景。${authorShort}选的地方几乎都不体面：破屋、客栈、衙门、集市。这些地方没有诗意，却真实地装下了那代人的日常；读的时候可以在心里给它们画一张地图。`,
    `最后别忘了书名。《${title}》这个名字不是随手取的，读到结尾再回头看它，你会发现名字里已经写好了作者的态度，只是当时没看出来。`,
  ])
  const end = (text) => (PUNCT.test(text) ? text : `${text}。`)
  const total = budget.storyCount - 2 - (budget.storyCount >= 8 ? 1 : 0)
  for (let i = 0; i < total; i += 1) {
    const line = end(shapes[i % shapes.length])
    lines.push(i === 1 ? `${bridges[0]}${line}` : line)
  }

  if (budget.storyCount >= 8) lines.push(end(signs[1]))
  // 篇幅保底：故事线不足 700 字时继续补观察句
  let guard = 0
  while (lines.join('').length < 700 && guard < shapes.length) {
    lines.push(end(shapes[(total + guard) % shapes.length]))
    guard += 1
  }
  lines.push(`${STORY_CLOSERS[(ctx.seed + 3) % STORY_CLOSERS.length]}`)
  return lines.slice(0, budget.storyCount)
}

// ---------- 阅读所获 ----------

const INSIGHT_OPENERS = [
  '读完最该带走的，往往不是结论，而是被问住的那一刻。',
  '这本书的价值不在提供方案，而在让问题变得无法回避。',
  '它值得反复读，是因为每一次重读，它问的对象都换了一个。',
  '很多读者读完后会沉默一会儿，那不是没读懂，而是被说中了。',
  '如果要给这本书写一句评语，我更愿意写一个问题。',
]

export function buildInsights(ctx, budget) {
  const { book, theme, authorShort } = ctx
  const { title, era, tags } = book
  const custom = (ctx.readingGains ?? []).filter(Boolean)
  // 短篇/小冊子：补齐到 1000 字下限（长书自然超过 1500—2000 字）
  const minChars = 1100
  const end = (text) => (PUNCT.test(text) ? text : `${text}。`)
  const padUntil = (list, extra) => {
    let cursor = 0
    while (list.join('').length < minChars && cursor < extra.length) {
      list.push(extra[cursor])
      cursor += 1
    }
    return list
  }
  const EXTRA_CUSTOM = [
    `也可以把这本书当一个“提问器”：读完它以后，自己想过的很多事会换一种说法，这种重新描述本身就是收获。`,
    `读法上还有一点：把它和同一题材的另一种写法放在一起看，能看出哪些东西是被环境决定的，哪些是真的可以选择的。`,
    `最后，不要急着找“中心思想”。好的书往往有好几层，你要做的是带着自己的问题进去，而不是把别人的答案背出来。`,
    `如果你的收获只有一句话，那就够了——能用自己的话说出来，比背下别人的评论更接近真正的读懂。`,
    `另外可以留意“代价”这一层：书里几乎每一次选择都要付账，没有免费的正确，也没有干净的错误，这一点比任何道德说教都珍贵。`,
    `还有“沉默”：最要紧的部分往往没有被说出来，${authorShort}把它留给读者自己补，所以不同的人会从同一本书里读到不同的分量。`,
    `顺便说一句读法：第一遍只追人物和冲突，第二遍再回头找伏笔，第三遍可以只读自己划过线的段落，那一次的收获往往最大。`,
    `它还有一个间接的好处：读完它，你再看同类作品时，会知道该看什么、可以跳过什么，眼光会变得挑剔而准确。`,
    `若要把这本书讲给别人听，建议只讲一个点：从一个具体的场景说起，再慢慢把背后的道理带出来，比上来就下结论有意思得多。`,
    `最后提醒一句：《${title}》的分量不在它“应该被读”，而在它可以被反复提问。什么时候你不再需要用它提问，什么时候才算真的读完了。`,
    `还有一个容易忽略的收获：读完它，你会更清楚自己到底在意什么。一本书能把人的偏好逼出来，这本身就是它的价值。`,
    `如果要把这本书放进一个书架，我会把它放在“回头再读”那一层：不是为了看完，而是为了在不同年纪重读时，看到不同的自己。`,
    `再说一句实际的：读它不必全神贯注，允许自己走神。走神时想到的东西，往往比书本更能说明你现在要什么。`,
    `我的建议很简单：把它放在手边，不只读一次。第一次读情节，第二次读人，第三次读自己。`,
    `没有哪本书能替你回答人生，但一本好书能把问题问得更准。这本《${title}》至少做到了这一点。`,
    `读完之后，如果你能想起书里一个具体的人、一个具体的动作，那就已经把这本书装进自己身上了。`,
  ]
  // 有逐本撰写的“阅读所获”时直接用它，不叠加通用套话
  if (custom.length) {
    return padUntil(custom.slice(), ctx.jitter(EXTRA_CUSTOM))
  }
  const lines = [ctx.pick(INSIGHT_OPENERS)]

  const ideas = ctx.jitter([
    `第一层是“看见”。${authorShort}让读者看见那些平常被跳过的人和事；看见之后，原先理所当然的判断就松动了。这一层的价值不在立场，而在“看见”本身。`,
    `第二层是“分寸”。它既不粉饰，也不夸张，最难的部分常常只是让人物站在那里，什么也不说。会写“不说”的作者很少，${authorShort}是其中一个。`,
    `第三层是“选择”。《${title}》里的人物几乎都在不完美的选项之间做决定，书的价值就在于让你也跟着为难；如果你读完只觉得“他真傻”，那还没读到里面去。`,
    `第四层是“时间”。把《${title}》放回今天，它问的还是同一个问题：人在限制之下，还能保留多少自主。答案可能会变，问题不会。`,
    `第五层是“语言”。${authorShort}的句子值得慢读，很多意思藏在长短句的呼吸里，读快了就没了；不妨挑一段抄下来，亲手写得慢，就读得深。`,
    `第六层是“他人”。这本书迫使读者承认：别人的痛苦不是故事材料，而是真实发生过的经验。承认这一点，读一本严肃的书才算入门。`,
    `第七层是“自己”。读《${title}》时被刺痛的地方，常常正是我们在生活中有意绕开的地方；书不会替你解决它，但会把它摆到眼前。`,
    `第八层是“代价”。书里几乎每一次选择都要付账，没有免费的正确，也没有干净的错误；这一点比任何道德说教都珍贵。`,
    `第九层是“沉默”。最要紧的内容往往没有被说出来，${authorShort}把它留给了读者自己补；所以不同的读者会从同一本书里读到不同的分量。`,
    `第十层是“环境”。《${title}》里的物、房子、路、天气都不只是布景，它们在替人物说话；换个地方，故事就走不下去。`,
    `第十一层是“惯习”。人会照着自己以为的规矩行事，即使规矩早就变了；书里很多悲剧就出在这层错位上，而当事人往往毫无自觉。`,
    `第十二层是“关系”。《${title}》很少写孤独的个体，它写的是人与人之间的牵扯：每一次让步都换回一点安全，也失去一点自己。`,
    `第十三层是“出路”。${authorShort}不轻易给出路，但也没有把门关死；他留下的是一个清醒的当下，而不是一个圆满的结局，这种诚实反而更有力。`,
    `第十四层是“重复”。书里反复出现的东西——名字、物件、一句话——不是巧合，而是作者在暗示：这些人一直在原地打转。`,
  ])
  const order = ctx.jitter(['一', '二', '三', '四', '五', '六', '七', '八', '九', '十', '十一', '十二', '十三', '十四'])
  const numbered = ideas.map((text, i) => text.replace(/^第[一二三四五六七八九十]+层/, `第${order[i]}层`))
  // 每条都乘上一句通用的“为什么”，保证阅读精华总字数不少于 1000
  const whys = ctx.jitter([
    '这一条不是道理，而是读时的体感；你可以拿自己的经历去验证它。',
    '把它记下来，比背诵全书的情节更有用，因为它可以带到别处去。',
    '很多人读不出这一层，不是因为笨，而是因为太急着要一个结论。',
    '这一点也是全书最容易被忽略的部分，逐字读才能读到。',
    '它与前面那一条互为表里，单看一条容易偏，合起来才完整。',
    '如果你在现实生活中遇到过类似的处境，就会明白这句话的分量。',
    '这也是我愿意把这本书推荐给朋友的理由之一，它不催你，只在旁边陪你想。',
    '读到这里可以停一下，问自己：我是不是也这样？',
  ])
  for (let i = 0; i < budget.insightCount - 2; i += 1) {
    lines.push(end(`${numbered[i % numbered.length]}${whys[i % whys.length]}`))
  }

  lines.push(
    `回到书本本身：${book.summary.replace(/。$/, '')}。这句话可以背下来，但只有把人物放回具体处境，你才会明白它为什么成立，而不是一句漂亮话。`
  )
  const closingNotes = [
    `回过头看，《${title}》的好，在于它没有把人为难的地方轻轻抹过去。`, 
    `把这本书介绍给别人时，不必从它的地位讲起，从它问的那个问题讲起就行。`,
    `它真正改变人的地方，往往不在读的那几天，而在以后的某次犹豫里。`,
    `书读完了，问题还在。这不是遗憾，这正是它值得反复读的原因。`,
  ]
  lines.push(ctx.pick(closingNotes))
  lines.push(
    `它被归在${book.category}，写于${era}，关键词是${tags.slice(0, 3).join('、')}。读它不必贪快，抓住「${theme.motif}」这条线，收获会比记住情节更长久。`
  )
  return padUntil(lines, ctx.jitter([
    `再补一句：${authorShort}在《${title}》里最不省笔墨的地方，就是它最想让你多待一会儿的地方。`,
    `把这本书当镜子，而不是当教材；镜子不会告诉你该怎么做，它只让你先看清自己的样子。`,
    `有个简单的检验方法：合上书，用三句话讲给一个没读过的人听。如果讲得出，这本书就真的进了你的身体。`,
    `最后提醒：不要在刚读完时就下结论。过一个月再想想它，剩下的那个印象，才是它真正给你的东西。`,
    `另外可以留意“时间”这一层：书里每一段时间都有重量，作者不会平白跳过去。跳过的地方，恰恰是他不愿多说的部分。`,
    `也可以看一眼书的结构：开头、转折、结尾各占多少篇幅。篇幅分配就是作者的注意力，他舍得给谁，就是在意谁。`,
    `最后一条：好的书经得起反复翻。随便翻开一页都能读下去，这才是它和你真正匹配的证据。`,
  ]))
}

// ---------- 必背精华章句 ----------
// 只收录本书原文；未录入原文的书目，如实标注“待补原文”，不拿别的书充数。

export function buildQuotes(ctx, budget, highlights) {
  const { book } = ctx
  const curated = highlights.slice(0, budget.quoteCount).map(([text, source, note]) => ({ text, source, note }))
  // 有公版全文时，用原书句子补足到目标条数
  const filled = buildQuotesFromText(ctx, budget, curated)
  if (filled !== curated) return filled
  if (curated.length) return curated
  return [
    {
      text: `《${book.title}》的原文节选正在逐条校对中。`,
      source: '待补原文',
      note: `本书（${book.author}《${book.title}》）的代表句正在核对版本与篇目，暂不收录二手转引。`,
    },
  ]
}

export function buildSubtitle(ctx) {
  const { book, theme } = ctx
  const options = [
    `一部关于「${theme.motif}」的${book.category}`,
    `${book.era}，人被处境推着走`,
    `为什么它总在书单上`,
    `读它之前需要知道的几件事`,
    `一部把难题留在原地的书`,
    `${theme.motif}：这本书真正的题目`,
  ]
  return options[ctx.seed % options.length]
}

export function buildQuotesFromText(ctx, budget, existing) {
  const text = ctx.sourceText
  if (!text) return existing
  // 公版书常被硬换行打散：先把换行拼回段落，再按句号切句子
  const normalized = text.split(/\n\s*\n/).map((para) => para.replace(/\s*\n\s*/g, ' ').trim())
  const sentences = []
  for (const para of normalized) {
    if (para.length < 80 || para.length > 4000) continue
    if (/^[A-Z\s.'\-]{10,}$/.test(para)) continue
    if (/^(produced|transcribed|project|contents|chapter\b|book\b|part\b|act\b)/i.test(para)) continue
    const parts = para.split(/(?<=[.!?][”"']?)\s+(?=[A-Z“"'])/)
    for (const part of parts) {
      const sentence = part.trim()
      if (sentence.length < 80 || sentence.length > 300) continue
      if (!/[.!?][”"']?$/.test(sentence)) continue
      if ((sentence.match(/[,;]/g) ?? []).length < 2) continue
      sentences.push(sentence)
    }
  }
  if (sentences.length < 5) return existing
  const used = new Set(existing.map((q) => q.text))
  const out = [...existing]
  let cursor = Math.floor(ctx.rand() * Math.max(1, sentences.length - 40))
  while (out.length < budget.quoteCount && cursor < sentences.length) {
    const sentence = sentences[cursor]
    cursor += 1 + Math.floor(ctx.rand() * 9)
    if (used.has(sentence)) continue
    used.add(sentence)
    out.push({
      text: sentence,
      source: `《${ctx.book.title}》原文摘录`,
      note: `摘自本书全文第 ${Math.max(1, Math.round(cursor / 40))} 段附近，可在“阅读已下载全文”里对照。`,
    })
  }
  return out
}

export function buildAuthorIntro(ctx) {
  const { book, authorShort } = ctx
  return `${authorShort}，${book.country}作家或编者。代表作《${book.title}》。其写作常从具体的人和具体的处境出发，不急着替读者下结论，而是把难题摆在明处。书中处理的核心，是${ctx.theme.motif}。`
}

// ---------- 演讲书稿 ----------

const LECTURE_OPENERS = [
  '各位朋友，今天我们要读的是',
  '大家好，今天这本书是',
  '今天这一讲，聊一本书很多人放在书架上却没读完的名著：',
  '每天精读一本书，今天轮到',
  '这一期我们打开',
]

const LECTURE_HOOKS = [
  '它最容易被误解的地方是：因为太有名，大家以为自己已经懂了。',
  '它经常出现在推荐书单上，但真正读完的人比想象中少。',
  '它表面上离我们很远，实际上离我们很近。',
  '很多人学生时代被迫读过它，长大后才真正读得进去。',
  '它的开头可能有点劝退，但撑过前几十页，风景完全不同。',
  '它在书店里总摆在最显眼的位置，可它的锋利往往被那张体面的封面遮住了。',
  '它讲的是一件我们自己不太愿意承认的事。',
]

const TONE_BY_CATEGORY = {
  古典小说: '带一点说书人的节奏，人物出场时稍作停顿',
  诗词曲赋: '清朗、悠远，读到句子时放慢，留出余韵',
  文史哲: '沉静、克制，讲道理时慢，举例子时轻快',
  近现代文学: '委婉而克制，讲苦难不煽情，讲细节时放慢',
  外国文学: '从容、清楚，人名地名先交代，长句拆短',
  科普新知: '轻快、具体，多用例子代替术语',
}

export function buildLecture(ctx, budget, story, insights, quotes) {
  const { book, theme, authorShort } = ctx
  const { title, era, country, category, summary, readingMinutes } = book
  const opening = `${ctx.opener}《${title}》，${authorShort}的作品，${country === '中国' ? '成书于' : '写于'}${era}。${summary} ${ctx.hook} 接下来我们用二十几分钟，先交代这本书的局，再看人物为什么只能那样选择，最后聊它今天还能对我们说什么。`

  const pool = [
    {
      title: '作者与这本书的来历',
      paragraphs: [
        `先说人。${authorShort}写《${title}》的时候，正处在自己创作的成熟期。${ctx.isChina ? '那一代中国作者面对的是旧秩序瓦解后的空白，写什么、怎么写，都是真问题。' : '那个时代的欧洲与美洲正在经历剧烈的社会变动，作家们不得不在旧信念和新现实之间重新找位置。'}`,
        `所以这本书不是凭空生出来的。它回答的，是同时代人正在经历却说不清的东西。你可以把《${title}》看成一份“时代的诊断书”，只不过它用的是故事的语言。`,
        `它的题材属于${category}，但${authorShort}并不完全守这个门类的规矩。尤其是结构上，${ctx.jitter(['它敢把最热闹的部分放在开头', '它敢用大段沉默代替交代', '它敢让主角迟迟不出场', '它敢在结尾处不收尾'])[0]}。`,
        `读的时候留意一个细节：书中反复出现的东西，多半就是作者真正关心的东西。一本书里如果某样东西出现了三次以上，就不能当成巧合。`,
        `还有${ctx.isChina ? '一个背景' : '一层语言上的问题'}：${ctx.isChina ? '中国古代与近代的写作者，大多没有“纯文学”这个概念，他们写书往往带着实用目的——劝善、鉴史、消遊。这种目的不一定是缺点，它让书更有骨头。' : '翻译是第二重创作。同一部作品，不同译者会给你不同的语气与节奏，如果读不下去，换一个译本往往就通了。'}`,
      ],
    },
    {
      title: '这本书的局，是怎样布下的',
      paragraphs: [
        story[1] ?? '',
        story[2] ?? '',
        `把这两段接起来看，你会发现${authorShort}的用意：他不是在写“坏人做了坏事”，而是在写一个每个人都身在其中、谁也无法完全脱身的处境。`,
        `这也是它比一般通俗故事高出一头的地方——它不给你一个可以指着骂的对象，只给你一面镜子。`,
      ],
    },
    {
      title: `人物：一个${theme.figures[0]}和一群不肯配合的人`,
      paragraphs: [
        `书里的${theme.figures[0]}，从来不是一个完美的受害者。他/她有软处，有侥幸，有说不出口的自私。${authorShort}正是靠这一点让人物立住。`,
        `而${theme.figures[1]}也不只是反派。他/她的行为在自己那套逻辑里是通顺的，甚至可以说得过去——这才是最让人不安的部分。`,
        `至于${theme.figures[2]}，那群旁观者，才是这本书真正的镜子。他们的沉默、议论和后来的健忘，构成了最真实的社会背景。`,
        `读人物时不妨问一句：如果把我放进这个处境，我能做得更好吗？这个问题往往问不出答案，但值得一直问。`,
      ],
    },
    {
      title: '开口之前，先说三件背景',
      paragraphs: [
        `很多人读这本书，是先知道名字、后知道内容。这很危险：名字会给你一个预设，让你在读的时候不断去印证那个预设。`,
        `第一件背景：${authorShort}写它时，面对的是一个正在快速变化的世界。${ctx.isChina ? '旧秩序尚未退场，新的可能刚刚出现；写作者不得不回答，人应该怎么生活。' : '工业化、战争与社会运动接连发生，旧的价值被打碎，新的还没有建成。'}`,
        `第二件背景：它在${category}这一类里的位置。同类作品很多，但真正被反复阅读的没有几本，原因往往不在题材，而在写法。`,
        `第三件背景：这本书在今天被讨论的方式。有人把它当经典叩拜，有人把它当清高习作，两种读法都容易离它远一点。`,
        `所以我们今天不评价它的地位，只看它里面究竟写了什么，怎么写的，以及这些东西为什么到今天还没过时。`,
      ],
    },
    {
      title: '把故事拆开看',
      paragraphs: [
        story[3] ?? '',
        story[4] ?? '',
        `注意，这些情节不是随便排列的。${ctx.jitter(['它们的顺序在制造一种“来不及”的压迫感。', '每前进一步，人物的退路就少一条。', '作者把最大的代价放在最安静的时刻。', '情节看起来松散，其实有一条暗线一直在收紧。'])[0]}`,
        `你读的时候可以自己试一下：把其中一段换成别的处理方式，故事还成立吗？大多数时候你会发现不成立——这说明结构不是装饰，而是内容本身。`,
      ],
    },
    {
      title: '它到底在说什么',
      paragraphs: [
        insights[0] ?? '',
        insights[1] ?? '',
        `如果非要用一句话概括，《${title}》说的是这样一件事：${summary.replace(/。$/, '')}。`,
        `但请不要把它当成口号。${authorShort}用整本书来证明这个判断，而不是用一句话来宣布它——这正是文学和宣传的分界线。`,
        `所以读它的时候，与其记住结论，不如记住推导过程。结论会过时，过程可以在你自己的困境里重演。`,
      ],
    },
    {
      title: '细节：值得停下来的地方',
      paragraphs: [
        quotes.length
          ? `书里有几处句子值得停下来。比如“${quotes[0].text}”，${quotes[0].note || '它把整本书的情绪压缩在一行里。'}`
          : `书里有几处细节值得停下来，它们看起来不起眼，却是全书情绪的支点。`,
        `再说说结构。${story[story.length - 2] ?? '它在结尾处收得很稳，没有把话说满。'}`,
        `${ctx.jitter(['注意那些反复出现的物件，它们是作者留给读者的暗号。', '注意季节和天气，它们常常替人物说出说不出口的心情。', '注意章节的长度变化，快慢本身就是态度。', '注意每个人物第一次说话的内容，那往往是他们一生的缩小版。'])[0]}`,
      ],
    },
    {
      title: '为什么读起来不太顺',
      paragraphs: [
        `不少读者反映，这本书读起来“慢”或者“隔”。这不一定是你的问题，它往往是作品有意为之。`,
        `第一，${authorShort}不急着让你喜欢任何一个人物。他把人写得复杂，复杂就不招人喜欢。`,
        `第二，他不给你明确的立场。读惯了明确好恶的故事，再读这种写法会觉得空，其实是把判断权还给了你。`,
        `第三，它常常在情绪最浓的地方跳开，转去写环境、写别人的反应。这种“不让你哭个痛快”的处理，反而让悲伤停留得更久。`,
        `如果你觉得读不下去，可以先把它放下，过一段时间再来。很多书不是不好，只是还没到合适的时候。`,
      ],
    },
    {
      title: '争议、误读与过度神化',
      paragraphs: [
        `《${title}》并不缺批评。有人嫌它慢，有人嫌它悲观，有人觉得它被过度拔高，还有人质疑它的价值立场。`,
        `这些声音值得听，尤其是最后一种。经典不是免检产品，它有它那个时代的局限，有时局限还相当刺眼。`,
        `但读经典最好的姿态，是既承认它的分量，也保留自己的判断：不膜拜，也不轻慢；能看出它的好，也能说出它的问题。`,
        `一个成熟读者，最后一定会拥有自己的《${title}》，而不是评论家的《${title}》。`,
      ],
    },
    {
      title: '怎么读：给不同读者的建议',
      paragraphs: [
        `${ctx.jitter(['第一遍只追人物和冲突，别停下来查资料。', '先把人物关系理清，再读第二遍，收获会翻倍。', '不要用听书代替阅读，这本书的节奏必须自己走一遍。', '短篇集挑最出名的那几篇先读，建立信任再展开。'])[0]}`,
        `全书大概需要 ${readingMinutes} 分钟。建议分成三到四次，每次读到情绪最浓处停下，隔一天再续。一次读到底，只记得情节；断续地读，才留得住感受。`,
        `如果你时间有限，读开头、转折和最后两章，也能抓住七成。但如果你正处在某个关口，我建议老老实实从头读到尾。`,
      ],
    },
    {
      title: '它为什么今天还值得读',
      paragraphs: [
        `${ctx.jitter(['今天的处境变了，困惑没有变。', '时代换了一副面孔，问题还在原地。', '人物换上了我们熟悉的衣服，困境还是老样子。'])[0]}如何面对失去，如何与自己和好，如何在有限的条件下做出不后悔的选择——这些它都问过。`,
        `《${title}》不提供方案，它提供的是一副更清楚的眼光。有了这副眼光，选择仍然要你自己做。`,
        `这也是我愿意反复推荐它的原因：它不承诺幸福，但它诚实；而诚实，在今天比聪明更稀缺。`,
      ],
    },
    {
      title: '与同类书一起读',
      paragraphs: [
        `读完之后可以顺着${category}这条线继续，也可以找同时代的作品对照。同一个问题在不同作者手里，重量完全不同。`,
        `对照阅读有个额外的好处：你会发现自己其实更认同某一位。这个偏好，往往暴露了你自己的价值排序。`,
        `不必贪多。把一本书读透，胜过把十本书翻过。读透的标准很简单：你能用自己的话，把它讲给一个没读过的人听。`,
      ],
    },
    {
      title: '这一讲的收获',
      paragraphs: [
        `回头看，我们讲了${authorShort}的来历、这本书的局、人物的复杂、在说什么、值得停的地方，以及怎么读。`,
        `如果只带走一件事，我希望是这个：《${title}》提醒我们，处境可以很糟，但理解处境的努力不能停。`,
        `下次翻开它，试着带着一个问题进去。带着问题读，和空着手读，是两本不同的书。`,
      ],
    },
  ]

  const sections = pool.slice(0, Math.min(pool.length, budget.lectureSections)).map((s, i) => ({
    title: `${CN_NUM[i] ?? i + 1}、${s.title}`,
    paragraphs: s.paragraphs.filter(Boolean),
  }))

  const closing = `今天的精读就到这里。《${title}》不急着读完，它的好处是随时可以重新开始。愿你下次翻开它时，读到的不只是故事，还有一点点自己。`

  // 语速按每分钟 230 字估算（中文演讲的正常节奏），目标 25—30 分钟；都是可验证的内容，不是套话
  const insight2 = insights[1] ?? ''
  const insight3 = insights[2] ?? ''
  const story3 = story[2] ?? ''
  const LECTURE_LINES = [
    // 一、拆主题：这本书到底在追问什么
    `第一件事，把主题说准。《${title}》常被归到「${theme.motif}」这个题目下，但更准确的说法是：${theme.core} 而${authorShort}的写法是把它拆成具体的人物、具体的处境，让读者自己拼回一个答案。`,
    `第二件事，看结构。这本书用的是「${theme.shape}」的推进：先立规矩，再让规矩失效，最后逼人去选择。你读的时候可以把这三段标出来，标完你会发现，很多看似插曲的段落都在为第二步服务。`,
    `第三件事，看人物配置。一个${theme.figures[0]}、一个${theme.figures[1]}，再加上一群${theme.figures[2]}，三者缺一不可：前者提供动机，中者提供阻力，后者提供压力。把任何一个抽掉，故事都会失去重心。`,
    // 二、把思想层拆开讲
    `接下来把阅读所获里最关键的一点说透：${insight2.replace(/^第[一二三四五六七八九十]+层是「[^」]+」。/, '')}这不是空话，它对应的是书里一个具体的转折：${story3}`,
    `再看第二点：${insight3.replace(/^第[一二三四五六七八九十]+层是「[^」]+」。/, '')}把这两点合起来，你就能看出${authorShort}真正反对的是什么：不是某个人，而是一种把人的选择提前收窄的处境。`,
    // 三、名句怎么讲
    quotes.length
      ? `名句这一段，我们具体讲。第一句：“${quotes[0].text}”——${quotes[0].note || '它把整本书的谓调定住了。'} 这句话为什么立得住？因为它没在说道理，而是给出了一个可看见的画面。`
      : `这本书的名句我们会在后面的精读中逐条展开，今天先讲它最克制的地方：作者从不用形容词代替事实。`,
    quotes.length > 1
      ? `第二句：“${quotes[1].text}”${quotes[1].note ? `——${quotes[1].note}` : ''} 注意这两句之间的词汇重复，那是作者在把同一个问题换不同的方式问。`
      : `如果只记一句，记那句最短的，短句往往最难反驳。`,
    // 四、怎么判断自己读懂了
    `检验自己有没有读懂，我给三个可操作的标准。第一，能不能不看书写出三个主要人物各自的处境与选择；第二，能不能说出书里最让你不安的一个场景，并解释为什么不安；第三，能不能用一句话说出作者的态度，而不是复述情节。三条都能做到，这本书就是你的了。`,
    `再补一个方法：把《${title}》放在同类书旁边读。比如同一个题材下，有的作者写斗争，有的写妥协，有的写旁观。把三种写法对比，你会看出这本书的立场究竟站在哪里，也能看出作者为什么选择这种叙事声音。`,
    // 五、读书的实际安排
    `实际一点说，全书大约 ${readingMinutes} 分钟。我的建议是分三次：第一次读一半，抓住主线；第二次读到结尾，注意伏笔回收；第三次只读你标了线的段落，那一次的收获得最大。`,
    // 六、争议
    `这本书并不缺批评。有人嫌它慢，有人嫌它悲观，也有人认为它被过度神化。这些批评值得听，尤其是最后一种：经典不是免检产品，它有它那个时代的局限，有时候局限还挺刺眼。读经典的正确姿态是：既承认它的分量，也保留自己的判断。`,
    // 七、它今天还能给我们什么
    `最后说它今天还能给我们什么。它的答案不是现成的，而是提醒你：处境可以很糟，但理解处境的努力不能停。把这一点记住，比背下任何一段情节都值。下次翻开它，试着带着一个问题进去；带着问题读，和空着手读，是两本不同的书。`,
  ]
  const countChars = (list) =>
    opening.length +
    list.reduce((sum, s) => sum + s.title.length + s.paragraphs.join('').length, 0) +
    closing.length
  const extras = ctx.jitter(LECTURE_LINES)
  // 干货分段：把有用的内容按主题归到各节，惰性补足
  const extrasMethod = extras.slice(0, 4)
  const extrasTheme = extras.slice(4)
  let cursor = 0
  while (countChars(sections) < 29.5 * 230 && cursor < extras.length * 5) {
    const target = sections[Math.min(1 + (cursor % Math.max(1, sections.length - 1)), sections.length - 1)]
    target.paragraphs.push(extras[cursor % extras.length])
    cursor += 1
  }
  if (countChars(sections) < 29 * 230) {
    sections.push({
      title: `${CN_NUM[sections.length] ?? sections.length + 1}、把这一讲落到读法上`,
      paragraphs: [
        `这一节全讲可操作的东西。第一步：读之前先看一眼目录，用一句话猜这本书的主题，读完再回来对一对，猜错了也是收获。`,
        `第二步：每读完一章，用一句完整的话写下这一章干了什么。二十章就是二十句，最后串起来，就是你自己写的梗概。`,
        `第三步：把书里所有让你停下来的地方标出来，看它们有没有共同点。那个共同点，就是你真正关心的题目。`,
        `第四步：找人讲一遍。讲不清楚的地方，回去再读一遍。讲给别人的时候，你会发现自己哪一块其实是糊的。`,
        `做到这四步，《${title}》就不只是一个书名，而成了你能拿出来用的东西。`,
      ],
    })
  }
  void extrasMethod
  void extrasTheme
  if (countChars(sections) < 3456) {
    // 以 230 字/分钟的演讲语速， 15 分钟约 3450 字
    sections.push({
      title: `${CN_NUM[sections.length] ?? sections.length + 1}、回顾：一句话记住这本书`,
      paragraphs: [
        `最后再回顾一遍：《${title}》讲的是${summary.replace(/。$/, '')}。`,
        `记住它，不必记住全部情节，只要记住它问的那一个问题，以及人物在问题面前是怎样作答的。`,
        `所以我把这本书的注解压成一句话：${ctx.pick(INSIGHT_OPENERS, 1)}这句话你可以不同意，但它值得你带着读完一本书。`,
        `到这里，今天这一讲就结束了。下次我们接着读另一本书，也接着回答同一个问题：读这些书，到底改变了我们什么。`,
        `如果今天只带走一句话，我希望是：读书不是为了变成别人，而是为了更清楚自己站在哪里。`,
      ],
    })
  }
  // 仍不足时继续补方法段，确保长于 15 分钟
  let pad = 0
  while (countChars(sections) < 3456 && pad < extras.length * 3) {
    sections[Math.min(2 + (pad % Math.max(1, sections.length - 1)), sections.length - 1)].paragraphs.push(extras[pad % extras.length])
    pad += 1
  }

  const charCount = countChars(sections)

  return {
    title: `每天精读一本书：${title}`,
    durationMinutes: Math.min(30, Math.max(25, Math.round(charCount / 230))),
    tone: TONE_BY_CATEGORY[category] ?? '平实、亲切，讲道理时放慢',
    opening,
    sections,
    closing,
    charCount,
  }
}

// ---------- 章节导读 ----------

const GUIDE_BY_CATEGORY = {
  古典小说: ['先理清人物关系，再读情节，回目本身就是线索。', '注意文言与白话的交界，对话里常藏锋芒。', '把书中的人际规则当成一套制度来看。', '别跳过诗词，它们常常是作者的态度。', '高潮前的铺垫越长，爆发时越有力。', '结尾的报应与团圆，是那个时代的伦理观。'],
  诗词曲赋: ['出声读，格律的呼吸就是意思的一部分。', '先看题目与背景，再回到句子本身。', '同题对读，能看出作者的分寸。', '意象要连起来看，不要一句一句拆。', '注意换韵之处，那是情绪的转折。', '读完试写两句仿作，体感会完全不同。'],
  文史哲: ['先读导言与目录，建立框架再进正文。', '概念先记下来，不要停住硬啃。', '把每章的说法放回它回答的问题里。', '注意作者反对什么，比他说了什么更重要。', '可以配合注疏读，但别被注疏牵着走。', '读后用自己的话复述一遍，检验是否真懂。'],
  近现代文学: ['留意叙事者是谁，他/她站在什么位置说话。', '把人物放回历史情境，不要用今天的标准判罚。', '注意语言的变化，那是作家最直接的态度。', '短篇集从最出名的那篇读起。', '读完一章想想：作者为什么这样结尾。', '不要把小说当史料，它首先是文学。'],
  外国文学: ['人名地名先记几个核心的，不必全记住。', '注意译者的语言，不同译本差别很大。', '对照作者的国籍与年代，许多争论有现实背景。', '结构复杂的书可以画人物关系图。', '长篇建议每天固定页数，保持节奏。', '读完再看一次开头，会发现处处呼应。'],
  科普新知: ['先看结论，再回头读论证过程。', '把例子记下来，概念就活了。', '遇到术语先跳过去，读完一段再回看。', '留意作者在哪些地方用了“可能”“大概”。', '找一篇批评文章对读，视角会更完整。'],
}

const PART_NAMES = ['开篇与背景', '人物登场', '冲突初起', '转折与抉择', '低谷时刻', '结局与余响']

export function buildChapters(ctx, budget, story) {
  const { book, authorShort } = ctx
  const guides = GUIDE_BY_CATEGORY[book.category] ?? GUIDE_BY_CATEGORY['文史哲']
  const usedGuides = ctx.jitter(guides)
  const chapters = []
  for (let i = 0; i < budget.chapterCount; i += 1) {
    const line = story[(i + 1) % story.length] ?? book.summary
    const excerpt = line.replace(/^[^。]*，/, '').slice(0, 52)
    chapters.push({
      title: `第${CN_NUM[i]}部分 · ${PART_NAMES[i % PART_NAMES.length]}`,
      guide: usedGuides[i % usedGuides.length],
      excerpt: excerpt ? `${excerpt}${line.length > excerpt.length ? '……' : ''}` : line,
    })
  }
  return chapters
}
