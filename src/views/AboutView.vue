<script setup>
import { computed } from 'vue'
import { stats } from '../data/library.js'

const summary = computed(() => stats())
</script>

<template>
  <div class="about-view">
    <section class="about-hero">
      <span class="eyebrow">关于本馆</span>
      <h1>一个只属于你的名著阅读空间</h1>
      <p>
        古今名著精读馆是一个纯前端项目：没有后端、没有账号，也不需要上传任何文件。
        目录、精读档案、讲稿、朗读与录音，全部在浏览器本机完成。
      </p>
    </section>

    <section class="about-grid">
      <article class="about-card">
        <h2>目录与批次</h2>
        <p>
          总目录保存在 <code>data/catalog.csv</code>，每 10 部为一批，按批次逐步扩充，目标收录 1000 部作品，并以中国经典为主。
          当前已录入 {{ summary.total }} 部，覆盖 {{ summary.batches }} 个批次。
        </p>
      </article>
      <article class="about-card">
        <h2>阅读精华</h2>
        <p>
          每部作品的精读档案包含故事情节、阅读所获与必背章句。较长的作品、影响力更大的作品会写得更充分。
          档案放在 <code>data/books/&lt;类别&gt;/&lt;id&gt;.json</code>，与目录一一对应。
        </p>
      </article>
      <article class="about-card">
        <h2>每日演讲书稿</h2>
        <p>
          每篇讲稿按作品气质调整语气，从温润克制到轻快幽默，目标时长 15—30 分钟。
          支持整篇朗读，也可以点击任意段落从该段开始听。
        </p>
      </article>
      <article class="about-card">
        <h2>朗读与跟读</h2>
        <p>
          朗读使用浏览器内置的 Web Speech API，可切换中文音色与语速。录音使用 MediaRecorder，
          录制完成后可以立即回放、下载，录音不会离开你的电脑。
        </p>
      </article>
      <article class="about-card">
        <h2>原书阅读</h2>
        <p>
          在阅读器中导入本地的 .txt 或 .md 文件，系统会按“第X章 / 第X回”等标题自动拆章；
          字号、主题与阅读进度会保存在浏览器中，下次打开可以继续。
        </p>
      </article>
      <article class="about-card">
        <h2>隐私说明</h2>
        <p>
          本项目没有服务器，也不收集数据。唯一的持久化存储是浏览器 localStorage，用来记住收藏、字号与阅读进度。
          清除浏览器站点数据即可全部删除。
        </p>
      </article>
    </section>

    <section class="about-steps">
      <h2>如何继续扩充到 1000 部</h2>
      <ol>
        <li>在 <code>data/catalog.csv</code> 末尾按同样字段追加 10 部作品，填写 <code>batch</code> 为下一个批次号。</li>
        <li>运行 <code>npm run new-batch -- 41</code>，自动生成 10 个档案模板。</li>
        <li>补全模板中的故事情节、阅读所获、章句、演讲书稿与章节导读。</li>
        <li>运行 <code>npm run build</code>，目录与详情会自动打包进静态站点。</li>
      </ol>
      <p class="about-note">
        当前完整精读档案已上线 {{ summary.covered }} 部；其余作品会先展示目录速览，并在详情页提示“档案整理中”。
      </p>
    </section>

    <section class="about-usage">
      <h2>使用建议</h2>
      <div class="usage-grid">
        <div>
          <strong>1. 从总目录开始</strong>
          <p>按类别、批次、推荐等级筛选，或直接搜索作者、朝代、主题。</p>
        </div>
        <div>
          <strong>2. 先听一遍讲稿</strong>
          <p>在详情页点击“每日演讲书稿”，再按“从头播放”，适合通勤或做家务时听。</p>
        </div>
        <div>
          <strong>3. 跟读一段</strong>
          <p>用手机或电脑麦克风录下自己的朗读，回放时最容易发现语速与停顿的问题。</p>
        </div>
        <div>
          <strong>4. 读原书</strong>
          <p>导入本地电子书，按章节慢慢读；读完再回来看精读档案，会有第二次收获。</p>
        </div>
      </div>
    </section>
  </div>
</template>