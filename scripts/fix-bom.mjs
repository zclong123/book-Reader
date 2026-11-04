import fs from 'node:fs'
const file = process.argv[2]
if (!file) process.exit(1)
let s = fs.readFileSync(file, 'utf8')
if (s.charCodeAt(0) === 0xfeff) s = s.slice(1)
fs.writeFileSync(file, s, 'utf8')
console.log('BOM removed:', file)
