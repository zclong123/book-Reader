import { READING_GAINS as G1 } from './gains-1.mjs'
import { READING_GAINS as G2 } from './gains-2.mjs'
import { READING_GAINS as G3 } from './gains-3.mjs'

// 阅读所获：逐本撰写的观点（有则用，无则回落到通用写法并在文件中标记）
export const READING_GAINS = {
  ...G1,
  ...G2,
  ...G3,
}

export default READING_GAINS
