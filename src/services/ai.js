const SETTINGS_KEY = 'classics:aiSettings'

export const AI_PRESETS = [
  {
    key: 'ollama',
    label: '本地 Ollama（免费）',
    baseURL: 'http://localhost:11434/v1',
    model: 'qwen2.5:7b',
    hint: '需先安装 Ollama 并拉取模型。若浏览器跨域失败，启动时设置 OLLAMA_ORIGINS=*。'
  },
  {
    key: 'openrouter',
    label: 'OpenRouter 模型',
    baseURL: 'https://openrouter.ai/api/v1',
    model: 'deepseek/deepseek-chat-v3-0324:free',
    hint: '需要填写 OpenRouter API Key；可用模型以服务商当前提供的选项为准。'
  },
  {
    key: 'siliconflow',
    label: '硅基流动',
    baseURL: 'https://api.siliconflow.cn/v1',
    model: 'Qwen/Qwen2.5-7B-Instruct',
    hint: '需要硅基流动 API Key，部分小模型有免费额度。'
  },
  {
    key: 'custom',
    label: '自定义 OpenAI 兼容接口',
    baseURL: '',
    model: '',
    hint: '任何兼容 /chat/completions 的 OpenAI 风格接口都可以使用。'
  }
]

export const DEFAULT_AI_SETTINGS = {
  preset: 'ollama',
  baseURL: 'http://localhost:11434/v1',
  model: 'qwen2.5:7b',
  apiKey: '',
  temperature: 0.7
}

export function loadAISettings() {
  try {
    const saved = JSON.parse(localStorage.getItem(SETTINGS_KEY) ?? 'null')
    return saved ? { ...DEFAULT_AI_SETTINGS, ...saved } : { ...DEFAULT_AI_SETTINGS }
  } catch {
    return { ...DEFAULT_AI_SETTINGS }
  }
}

export function saveAISettings(settings) {
  localStorage.setItem(SETTINGS_KEY, JSON.stringify(settings))
}

export function applyPreset(settings, presetKey) {
  const preset = AI_PRESETS.find((item) => item.key === presetKey)
  if (!preset) return settings
  return {
    ...settings,
    preset: preset.key,
    baseURL: preset.baseURL || settings.baseURL,
    model: preset.model || settings.model
  }
}

function chat({ settings, system, user, maxTokens = 6000, timeout = 180000 }) {
  const baseURL = String(settings?.baseURL ?? '').trim().replace(/\/+$/, '')
  const model = String(settings?.model ?? '').trim()
  if (!baseURL) return Promise.reject(new Error('请先填写 AI 服务地址'))
  if (!model) return Promise.reject(new Error('请先填写模型名称'))

  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeout)
  const headers = { 'Content-Type': 'application/json' }
  if (settings.apiKey) headers.Authorization = `Bearer ${settings.apiKey}`

  return fetch(`${baseURL}/chat/completions`, {
    method: 'POST',
    headers,
    signal: controller.signal,
    body: JSON.stringify({
      model,
      temperature: Number(settings.temperature) || 0.7,
      max_tokens: maxTokens,
      messages: [
        { role: 'system', content: system },
        { role: 'user', content: user }
      ]
    })
  })
    .then(async (response) => {
      const payload = await response.json().catch(() => null)
      if (!response.ok) throw new Error(errorMessage(response.status, payload))
      let content = payload?.choices?.[0]?.message?.content ?? payload?.message?.content ?? ''
      if (Array.isArray(content)) content = content.map((part) => part?.text ?? '').join('')
      return content
    })
    .catch((error) => {
      if (error.name === 'AbortError') throw new Error('生成超时，请稍后重试或换一个更快的模型')
      if (error instanceof TypeError) throw new Error('无法连接 AI 服务，请检查地址、网络与跨域设置')
      throw error
    })
    .finally(() => clearTimeout(timer))
}

/** 按需为目录中的书生成完整精读详情（含演讲书稿） */
export async function generateDetailForBook({ book, settings }) {
  const content = await chat({
    settings,
    system: buildPrompt({ title: book.title, author: book.author, category: book.category }).system,
    user: [
      `书名：${book.title}`,
      `作者：${book.author || '佚名'}`,
      `国别：${book.country || ''}`,
      `年代：${book.era || ''}`,
      `类别：${book.category || '文史哲'}`,
      `主题标签：${(book.tags ?? []).join('、')}`,
      '请结合以上信息直接输出完整 JSON。'
    ].join('\n')
  })
  return extractJson(content)
}

/** 根据读者指令修改既有精读详情（可指定只改演讲书稿） */
export async function reviseDetailWithAI({ book, detail, instruction, scope, settings }) {
  const target = scope === 'lecture'
    ? { lecture: detail.lecture }
    : scope === 'essence'
      ? { essence: detail.essence }
      : { essence: detail.essence, lecture: detail.lecture, chapters: detail.chapters }
  const system = [
    '你是一位中文名著精读编辑。读者会给你一份已有的精读档案 JSON 和修改要求。',
    '请按修改要求改写指定部分，保持 JSON 结构与字段名完全一致，未要求修改的部分原样保留。',
    '只输出一个 JSON 对象，不要输出 Markdown 代码块，不要输出解释文字。'
  ].join('\n')
  const user = [
    `书名：${book.title}（作者：${book.author || '佚名'}）`,
    `修改范围：${scope === 'lecture' ? '仅演讲书稿' : scope === 'essence' ? '仅阅读精华' : '精读详情全部'}`,
    `修改要求：${instruction || '请把内容润色得更流畅、更有可读性'}`,
    '现有内容 JSON：',
    JSON.stringify(target),
    '请输出修改后的完整 JSON（结构与输入一致）。'
  ].join('\n')
  const content = await chat({ settings, system, user, maxTokens: 6000 })
  return extractJson(content)
}

export function buildPrompt({ title, author, category }) {
  const system = [
    '你是一位中文名著精读编辑，擅长把一本书整理成适合普通读者收听的精读档案。',
    '请根据用户给出的书名，生成一份结构化精读档案。',
    '只输出一个 JSON 对象，不要输出 Markdown 代码块，不要输出解释文字。',
    'JSON 必须包含以下字段：',
    '{',
    '"title": "书名",',
    '"subtitle": "一句有文采的副标题",',
    '"author": "作者",',
    '"authorIntro": "100字左右作者小传",',
    '"country": "国别",',
    '"era": "年代",',
    '"category": "类别，取值：古典小说、文史哲、诗词曲赋、近现代文学、外国文学",',
    '"tags": ["5个主题标签"],',
    '"readingMinutes": 整数，预估阅读分钟数,',
    '"summary": "一句话简介",',
    '"essence": {',
    '  "story": ["4段故事情节或思想脉络，每段120到200字"],',
    '  "insights": ["4条阅读所获，每条120到200字，要有观点，不要复述简介"],',
    '  "quotes": [{"text":"原文金句","source":"出处","note":"一句短评"}]，共5条',
    '},',
    '"lecture": {',
    '  "title": "每天精读一本书：书名",',
    '  "durationMinutes": 15到22之间的整数,',
    '  "tone": "讲稿语气，要符合作品气质",',
    '  "opening": "300到500字开场",',
    '  "sections": [{"title":"一、小标题","paragraphs":["3到4段，每段100到180字"]}]，共9到11节,',
    '  "closing": "200到300字收束"',
    '},',
    '"chapters": [{"title":"关键章节名","guide":"章节导读","excerpt":"一句节选"}]，共5个',
    '}',
    '讲稿总字数控制在2500到3500字，目标时长15到20分钟。',
    '内容要准确，不确定的细节使用学界通行说法，不要编造具体页码或数据。',
    '如果书名不是公认名著，也要按书名的字面含义生成合理内容，并在副标题中注明“自定义解读”。'
  ].join('\n')

  const user = [
    `书名：${title}`,
    `作者：${author || '佚名'}`,
    `类别：${category || '文史哲'}`,
    '请直接输出完整 JSON。'
  ].join('\n')

  return { system, user }
}

export function extractJson(text) {
  const content = String(text ?? '').trim()
  if (!content) throw new Error('模型没有返回内容')
  const fenced = content.match(/```(?:json)?\s*([\s\S]*?)```/i)
  const candidate = fenced ? fenced[1] : content
  const start = candidate.indexOf('{')
  const end = candidate.lastIndexOf('}')
  if (start < 0 || end <= start) throw new Error('模型返回的内容里没有找到 JSON')
  try {
    return JSON.parse(candidate.slice(start, end + 1))
  } catch {
    throw new Error('模型返回的 JSON 格式不完整，请重试或换一个模型')
  }
}

function errorMessage(status, payload) {
  const detail = payload?.error?.message || payload?.message || payload?.error
  if (detail && typeof detail === 'string') return detail
  if (status === 401 || status === 403) return 'API Key 无效或没有权限'
  if (status === 404) return '接口地址或模型名称不正确'
  if (status === 429) return '请求过于频繁或额度已用完'
  return `请求失败（HTTP ${status}）`
}

export async function generateBookWithAI({ title, author, category, settings }) {
  const baseURL = String(settings?.baseURL ?? '').trim().replace(/\/+$/, '')
  const model = String(settings?.model ?? '').trim()
  if (!baseURL) throw new Error('请先填写 AI 服务地址')
  if (!model) throw new Error('请先填写模型名称')

  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), 180000)
  const { system, user } = buildPrompt({ title, author, category })
  const headers = { 'Content-Type': 'application/json' }
  if (settings.apiKey) headers.Authorization = `Bearer ${settings.apiKey}`

  try {
    const response = await fetch(`${baseURL}/chat/completions`, {
      method: 'POST',
      headers,
      signal: controller.signal,
      body: JSON.stringify({
        model,
        temperature: Number(settings.temperature) || 0.7,
        max_tokens: 6000,
        messages: [
          { role: 'system', content: system },
          { role: 'user', content: user }
        ]
      })
    })
    const payload = await response.json().catch(() => null)
    if (!response.ok) throw new Error(errorMessage(response.status, payload))
    let content = payload?.choices?.[0]?.message?.content ?? payload?.message?.content ?? ''
    if (Array.isArray(content)) content = content.map((part) => part?.text ?? '').join('')
    return extractJson(content)
  } catch (error) {
    if (error.name === 'AbortError') throw new Error('生成超时，请稍后重试或换一个更快的模型')
    if (error instanceof TypeError) throw new Error('无法连接 AI 服务，请检查地址、网络与跨域设置')
    throw error
  } finally {
    clearTimeout(timer)
  }
}