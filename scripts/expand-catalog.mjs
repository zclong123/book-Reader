// 将目录扩展至 10000 部，并为每本书补充三级分类（cat1/cat2/cat3）。
// 现有 catalog.json 中的真实书目作为种子保留；其余按分类模板规则化生成，
// 只保留简介（summary），精读详情由应用内按需调用大模型生成。
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(__dirname, '..')
const jsonPath = path.join(root, 'data', 'catalog.json')
const csvPath = path.join(root, 'data', 'catalog.csv')
const TARGET = 10000

/* ---------------- 三级分类体系 ---------------- */
export const TAXONOMY = {
  中国文学: {
    古典文学: {
      古典小说: ['长篇章回', '神魔志怪', '世情讽刺', '侠义公案'],
      诗词曲赋: ['诗词总集', '名家别集', '词曲选本', '戏曲传奇'],
      文史哲: ['经部典籍', '史部史著', '诸子百家', '笔记小品']
    },
    近现代文学: {
      现当代小说: ['长篇小说', '中短篇集', '乡土文学', '都市文学'],
      散文随笔: ['回忆散文', '杂文评论', '游记纪实'],
      话剧戏剧: ['话剧剧本', '戏曲改革']
    },
    武侠与民间: {
      武侠仙侠: ['武侠传奇', '仙侠修行'],
      民间文学: ['民间故事', '说唱曲艺']
    }
  },
  外国文学: {
    欧美文学: {
      英美文学: ['经典长篇', '现代主义', '推理悬疑'],
      法德欧陆: ['现实主义', '浪漫主义', '哲理小说'],
      俄苏文学: ['史诗巨著', '心理小说', '苏维埃文学'],
      古典史诗: ['神话史诗', '古典戏剧']
    },
    亚非拉文学: {
      日本文学: ['物语文学', '近现代小说', '私小说随笔'],
      拉美文学: ['魔幻现实', '结构现实主义'],
      亚非其他: ['阿拉伯故事集', '非洲文学', '南亚文学']
    },
    奇幻科幻: {
      科幻小说: ['硬科幻', '软科幻', '科幻经典'],
      奇幻文学: ['西方奇幻', '童话寓言'],
      反乌托邦: ['反乌托邦', '政治寓言']
    }
  },
  人文社科: {
    思想哲学: {
      西方哲学: ['哲学经典', '存在主义', '政治哲学'],
      中国思想: ['儒家经典', '道家佛学', '近现代思想']
    },
    历史传记: {
      世界历史: ['通史大观', '断代史著', '战争史'],
      人物传记: ['名人传记', '回忆录']
    },
    社会经济: {
      政治法律: ['政治学', '法学经典'],
      经济管理: ['经济学', '管理与商业']
    },
    心理宗教: {
      心理学: ['心理学经典', '大众心理'],
      宗教信仰: ['佛学经典', '基督教经典', '伊斯兰经典']
    }
  },
  科普新知: {
    自然科学: {
      物理宇宙: ['宇宙天文', '理论物理'],
      生命科学: ['生物演化', '医学健康'],
      数学逻辑: ['数学科普', '逻辑思维']
    },
    技术与社会: {
      信息技术: ['人工智能', '网络社会'],
      科学人文: ['科学史话', '环境地理']
    }
  }
}

const CAT1S = Object.keys(TAXONOMY)

/* ---------------- 种子书的分类映射 ---------------- */
const COUNTRY_GROUP = {
  英国: ['欧美文学', '英美文学'], 美国: ['欧美文学', '英美文学'], 爱尔兰: ['欧美文学', '英美文学'],
  加拿大: ['欧美文学', '英美文学'], 澳大利亚: ['欧美文学', '英美文学'],
  法国: ['欧美文学', '法德欧陆'], 德国: ['欧美文学', '法德欧陆'], 西班牙: ['欧美文学', '法德欧陆'],
  奥地利: ['欧美文学', '法德欧陆'], 捷克: ['欧美文学', '法德欧陆'], 挪威: ['欧美文学', '法德欧陆'],
  丹麦: ['欧美文学', '法德欧陆'], 意大利: ['欧美文学', '法德欧陆'], 瑞士: ['欧美文学', '法德欧陆'],
  俄国: ['欧美文学', '俄苏文学'], 苏联: ['欧美文学', '俄苏文学'],
  古希腊: ['欧美文学', '古典史诗'], 古罗马: ['欧美文学', '古典史诗'],
  日本: ['亚非拉文学', '日本文学'],
  哥伦比亚: ['亚非拉文学', '拉美文学'], 秘鲁: ['亚非拉文学', '拉美文学'], 危地马拉: ['亚非拉文学', '拉美文学'],
  阿根廷: ['亚非拉文学', '拉美文学'], 墨西哥: ['亚非拉文学', '拉美文学'], 巴西: ['亚非拉文学', '拉美文学'],
  阿拉伯: ['亚非拉文学', '亚非其他'], 以色列: ['亚非拉文学', '亚非其他'], 印度: ['亚非拉文学', '亚非其他'],
  埃及: ['亚非拉文学', '亚非其他'], 尼日利亚: ['亚非拉文学', '亚非其他'], 南非: ['亚非拉文学', '亚非其他']
}

const GENRE_THIRD = [
  { match: /科幻|宇宙|文明|技术|星际/, cat3: '科幻经典' },
  { match: /反乌托邦|极权|政治寓言/, cat3: '反乌托邦' },
  { match: /童话|寓言|奇幻|魔法/, cat3: '童话寓言' },
  { match: /推理|侦探|悬疑/, cat3: '推理悬疑' }
]

function classifySeed(book) {
  const cat = book.category
  const tags = (book.tags ?? []).join('|')
  if (cat === '古典小说') {
    const cat3 = /神魔|志怪|神话/.test(tags) ? '神魔志怪' : /侠义|公案/.test(tags) ? '侠义公案' : /讽刺|世情/.test(tags) ? '世情讽刺' : '长篇章回'
    return ['中国文学', '古典文学', '古典小说', cat3]
  }
  if (cat === '诗词曲赋') {
    const cat3 = /戏曲|戏剧/.test(tags) ? '戏曲传奇' : /词|元曲/.test(tags) ? '词曲选本' : /总集|选本|三百首|诗经/.test(tags + book.title) ? '诗词总集' : '名家别集'
    return ['中国文学', '古典文学', '诗词曲赋', cat3]
  }
  if (cat === '文史哲') {
    const cat3 = /历史|纪传|编年|通史/.test(tags) ? '史部史著' : /哲学|儒家|道家|法家|墨家|心学/.test(tags) ? '诸子百家' : /笔记|游记|随笔/.test(tags) ? '笔记小品' : '经部典籍'
    return ['中国文学', '古典文学', '文史哲', cat3]
  }
  if (cat === '近现代文学') {
    const cat3 = /话剧|戏剧/.test(tags) ? '话剧剧本' : /散文|回忆/.test(tags) ? '回忆散文' : /都市|上海|市民/.test(tags) ? '都市文学' : /乡土|农村|农民/.test(tags) ? '乡土文学' : /短篇/.test(tags) ? '中短篇集' : '长篇小说'
    return ['中国文学', '近现代文学', '现当代小说', cat3]
  }
  if (cat === '外国文学') {
    const group = book.country === '外国' ? ['欧美文学', '法德欧陆'] : (COUNTRY_GROUP[book.country] ?? ['欧美文学', '法德欧陆'])
    const genre = GENRE_THIRD.find((g) => g.match.test(tags))
    let cat3 = genre?.cat3
    if (!cat3) {
      const pool = TAXONOMY['外国文学'][group[1]] ?? TAXONOMY['外国文学']['欧美文学']
      cat3 = Object.values(pool).flat()[0]
      if (group[1] === '日本文学' && /随笔|日记/.test(tags)) cat3 = '私小说随笔'
      if (group[1] === '拉美文学' && !/魔幻/.test(tags)) cat3 = '结构现实主义'
    }
    return ['外国文学', group[0], group[1], cat3]
  }
  if (cat === '科普新知') {
    const cat3 = /宇宙|天文|物理|时间/.test(tags) ? '宇宙天文' : /生物|进化|基因|人类/.test(tags) ? '生物演化' : /数学|逻辑/.test(tags) ? '数学科普' : /人工智能|网络|数字/.test(tags) ? '人工智能' : '科学史话'
    const cat2 = ['宇宙天文', '生物演化', '数学科普'].includes(cat3) ? '自然科学' : '技术与社会'
    return ['科普新知', cat2, cat3, cat3]
  }
  return ['人文社科', '思想哲学', '中国思想', '近现代思想']
}

/* ---------------- 规则化生成其余书目 ---------------- */
function mulberry32(seed) {
  return function () {
    seed |= 0; seed = (seed + 0x6d2b79f5) | 0
    let t = Math.imul(seed ^ (seed >>> 15), 1 | seed)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}
const rand = mulberry32(20261104)
const pick = (arr) => arr[Math.floor(rand() * arr.length)]

const TITLE_MORPH = {
  '中国文学': {
    prefix: ['长安', '江南', '山河', '京华', '故园', '沧海', '中原', '云梦', '西塞', '南渡', '九衢', '寒江', '孤城', '春风', '烟雨', '长风', '落日', '秋水', '雪原', '青灯'],
    core: ['旧事', '行旅', '笔记', '岁时', '棋局', '灯影', '书简', '船歌', '山川', '人间', '长卷', '残卷', '问月', '听雪', '望乡', '观潮', '耕读', '市声', '野渡', '柴门'],
    suffix: ['记', '录', '传', '志', '谈', '考', '话', '谱', '笺', '集', '钞', '略']
  },
  '外国文学': {
    prefix: ['雾都', '北方', '海上', '平原', '孤岛', '远山', '白夜', '黄昏', '车站', '灯塔', '旷野', '城堡', '河边', '麦田', '大桥', '酒馆', '钟楼', '花园', '村庄', '海边'],
    core: ['来信', '年代', '之夏', '之路', '的冬天', '的黄昏', '的遗产', '的告别', '的旅程', '的守望', '的孩子们', '的妻子', '的背影', '的房间', '的街道', '的秋天', '的黎明', '的名字', '的旅行', '的终结'],
    suffix: ['']
  },
  '人文社科': {
    prefix: ['论', '浅说', '简史', '新解', '重访', '叩问', '通览', '概观', '漫谈', '札记', '导论', '讲演', '沉思录', '对话录', '审思'],
    core: ['文明', '自由', '正义', '时间', '权力', '秩序', '理性', '信仰', '人性', '社会', '知识', '语言', '记忆', '制度', '市场', '教育', '心灵', '历史', '美德', '幸福'],
    suffix: ['']
  },
  '科普新知': {
    prefix: ['走近', '看见', '解读', '探秘', '常识', '图解', '小史', '新知', '漫话', '十讲', '入门', '指南', '再发现', '简明', '百问'],
    core: ['宇宙', '地球', '海洋', '大脑', '基因', '细胞', '时间', '光', '引力', '量子', '病毒', '气候', '物种', '星辰', '元素', '进化', '能量', '算法', '网络', '概率'],
    suffix: ['']
  }
}

const AUTHORS = {
  '中国文学': ['佚名', '柳岸', '沈砚秋', '顾云帆', '陆听澜', '程雪衣', '白鹿生', '秦望舒', '苏晚晴', '韩江雪', '周慕云', '许清和', '江疏影', '孟长风', '裴松龄', '方闻道', '叶知秋', '赵秉烛', '杜若洲', '温故人'],
  '外国文学': ['A. 莫罗', 'E. 温特斯', 'J. 卡尔弗特', 'L. 布拉西尼', 'M. 科尔文', 'N. 阿尔托', 'P. 范德林', 'R. 奥克斯利', 'S. 温特伯格', 'T. 霍尔曼', 'V. 兰道尔', 'W. 哈灵顿', 'K. 米勒', 'D. 索恩顿', 'G. 费拉里', 'H. 林德奎斯特'],
  '人文社科': ['陈述之', '李慎思', '徐理明', '何观澜', '林致知', '张秉文', '王格物', '蔡明辨', 'R. 桑德尔曼', 'T. 基尔南', 'J. 奥斯特'],
  '科普新知': ['吴格物', '郑明理', '许知远航', '冯探微', 'S. 霍尔登', 'M. 卡拉汉', 'L. 佩雷拉', 'A. 沃克']
}

const ERAS = {
  '中国文学': ['先秦', '汉代', '魏晋', '唐代', '宋代', '元代', '明代', '清代', '民国时期', '当代'],
  '外国文学': ['古代', '中世纪', '文艺复兴', '18世纪', '19世纪', '20世纪初', '20世纪中叶', '当代'],
  '人文社科': ['古典时期', '启蒙时代', '19世纪', '20世纪', '当代'],
  '科普新知': ['20世纪', '当代']
}

const LEVELS = ['殿堂级', '经典', '重要', '重要', '拓展', '拓展']

function makeBook(index) {
  const cat1 = CAT1S[Math.floor(index / 2500) % CAT1S.length]
  const cat2s = Object.keys(TAXONOMY[cat1])
  const cat2 = cat2s[Math.floor(rand() * cat2s.length)]
  const cat3s = Object.values(TAXONOMY[cat1][cat2]).flat()
  const cat3 = cat3s[Math.floor(rand() * cat3s.length)]
  const morph = TITLE_MORPH[cat1]
  // 组合空间：prefix * core * suffix，加序号后缀保证唯一
  const p = morph.prefix[index % morph.prefix.length]
  const c = morph.core[Math.floor(index / morph.prefix.length) % morph.core.length]
  const s = morph.suffix[Math.floor(index / (morph.prefix.length * morph.core.length)) % Math.max(1, morph.suffix.length)]
  const vol = Math.floor(index / (morph.prefix.length * morph.core.length * Math.max(1, morph.suffix.length)))
  const volText = vol === 0 ? '' : `·${['初集','二集','三集','四集','五集','六集','七集','八集','九集','十集','续集','外集','别集','补集','遗集','新集','又集','再集','重集','终集'][Math.min(vol - 1, 19)]}`
  const title = `${p}${c}${s}${volText}`
  const country = cat1 === '中国文学' ? '中国' : cat1 === '外国文学' ? pick(['英国', '美国', '法国', '德国', '俄国', '日本', '意大利', '哥伦比亚', '阿根廷']) : cat1 === '人文社科' ? pick(['中国', '英国', '美国', '德国', '法国']) : pick(['英国', '美国', '中国'])
  const author = pick(AUTHORS[cat1])
  const era = pick(ERAS[cat1])
  const tagPool = [cat2, cat3, pick(morph.core), pick(morph.prefix), pick(['成长', '命运', '人性', '记忆', '自由', '社会', '家庭', '旅程'])]
  const tags = [...new Set(tagPool)].slice(0, 5)
  const minutes = 40 + Math.floor(rand() * 46) * 10
  const summary = `《${title}》是一部${era}${country}的${cat2}作品，主题集中在${tags.slice(2, 4).join('、')}等方向。${author}以平实而绵密的笔触，把个人境遇放进更大的时代与思想背景之中，读来既见故事，也见世相与人心。`
  const id = `gen${String(index).padStart(5, '0')}`
  const level = pick(LEVELS)
  return { id, title, author, country, era, category: cat2, level, batch: 0, readingMinutes: minutes, tags, summary, cat1, cat2, cat3 }
}

/* ---------------- 主流程 ---------------- */
const seeds = JSON.parse(fs.readFileSync(jsonPath, 'utf8'))
console.log(`读取种子书目 ${seeds.length} 本`)

for (const book of seeds) {
  const [cat1, cat2, cat3] = classifySeed(book)
  book.cat1 = cat1
  book.cat2 = cat2
  book.cat3 = cat3
}

const existing = new Set(seeds.map((b) => b.title))
const generated = []
let i = 0
let guard = 0
while (seeds.length + generated.length < TARGET && guard < TARGET * 30) {
  guard += 1
  const book = makeBook(i++)
  if (existing.has(book.title)) continue
  existing.add(book.title)
  generated.push(book)
}

// 批次：种子书保留原批次；生成书按 100 本一批顺延
const maxBatch = Math.max(0, ...seeds.map((b) => Number(b.batch) || 0))
generated.forEach((b, idx) => { b.batch = maxBatch + Math.floor(idx / 100) + 1 })

const all = [...seeds, ...generated]
// 同名书加版本后缀，保证书名唯一
const seenTitles = new Map()
for (const b of all) {
  const count = seenTitles.get(b.title) ?? 0
  seenTitles.set(b.title, count + 1)
  if (count > 0) b.title = `${b.title}（别本${count > 1 ? count : ''}）`
}
fs.writeFileSync(jsonPath, `${JSON.stringify(all, null, 1)}\n`, 'utf8')

// 同步重建 catalog.csv（供 npm run catalog / CSV 下载使用）
const csvCell = (v) => {
  const s = String(v ?? '')
  return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s
}
const header = 'id,title,author,country,era,category,level,batch,reading_minutes,tags,summary,cat1,cat2,cat3'
const rows = all.map((b) =>
  [b.id, b.title, b.author, b.country, b.era, b.category, b.level, b.batch, b.readingMinutes, (b.tags ?? []).join('|'), b.summary, b.cat1, b.cat2, b.cat3].map(csvCell).join(',')
)
fs.writeFileSync(csvPath, `\uFEFF${header}\n${rows.join('\n')}\n`, 'utf8')

console.log(`目录已扩展至 ${all.length} 部（种子 ${seeds.length} + 生成 ${generated.length}）`)
const c1n = CAT1S.length
const c2n = CAT1S.reduce((n, c) => n + Object.keys(TAXONOMY[c]).length, 0)
const c3n = CAT1S.reduce((n, c) => n + Object.values(TAXONOMY[c]).reduce((m, arr) => m + Object.values(arr).reduce((x, list) => x + (list?.length ?? 0), 0), 0), 0)
console.log(`分类体系：${c1n} 个一级 / ${c2n} 个二级 / ${c3n} 个三级`)
