import http from 'node:http'

const payload = {
  essence: {
    story: ['AI 改写后的第一段', 'AI 改写后的第二段'],
    insights: ['AI 改写后的阅读所获'],
    quotes: [{ text: '改写的金句', source: '书中', note: '测试' }]
  },
  lecture: {
    title: 'AI 改写的讲稿',
    durationMinutes: 20,
    tone: '亲切',
    opening: '这是 AI 改写后的开场白，更口语化。',
    sections: [{ title: '一、开场', paragraphs: ['AI 改写后的段落一', 'AI 改写后的段落二'] }],
    closing: 'AI 改写后的收束。'
  }
}

const server = http.createServer((req, res) => {
  res.setHeader('Access-Control-Allow-Origin', '*')
  res.setHeader('Access-Control-Allow-Headers', '*')
  res.setHeader('Access-Control-Allow-Methods', '*')
  if (req.method === 'OPTIONS') {
    res.writeHead(204).end()
    return
  }
  let body = ''
  req.on('data', (c) => (body += c))
  req.on('end', () => {
    const parsed = JSON.parse(body || '{}')
    console.log('模型调用:', parsed.model, '| messages:', parsed.messages.length)
    res.setHeader('Content-Type', 'application/json')
    res.end(JSON.stringify({ choices: [{ message: { content: JSON.stringify(payload) } }] }))
  })
})

server.listen(5199, () => console.log('mock model on http://localhost:5199/v1/chat/completions'))
