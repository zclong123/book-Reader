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
        <h2>目录与年代</h2>
        <p>
          总目录保存在 <code>data/catalog.csv</code>，以中国经典为主，兼收外国名著。
          当前已录入 {{ summary.total }} 部，覆盖 {{ summary.categories }} 个类别，其中 {{ summary.covered }} 部配有完整精读档案。
        </p>
      </article>
      <article class="about-card">
        <h2>阅读精华</h2>
        <p>
          每部作品的精读档案包含故事情节、阅读所获与必背精华章句（均为本书原文节选，注明篇目）。
          短的不少于 1000 字，长书、名著与影响力大的作品会写到 2000 字以上。
          档案放在 <code>data/books/&lt;类别&gt;/&lt;id&gt;.json</code>，与目录一一对应。
        </p>
      </article>
      <article class="about-card">
        <h2>每日演讲书稿</h2>
        <p>
          每篇讲稿按作品气质调整语气，从温润克制到轻快幽默，时长 25—30 分钟，只讲干货：主题、结构、人物、名句、读法。
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
        <h2>原书阅读与公版下载</h2>
        <p>
          在阅读器中可导入本地的 .txt / .md 文件，系统会按“第X章 / 第X回”自动拆章；
          公版名著（Project Gutenberg 收录的原文）已下载到 <code>public/books/</code>，点“阅读已下载全文”即可直接阅读，也可在线重新下载。
          字号、主题与阅读进度会保存在浏览器中，下次打开可以继续。
        </p>
      </article>
      <article class="about-card">
        <h2>自定义书与免费模型</h2>
        <p>
          输入书名，即可调用兼容 OpenAI 接口的免费模型生成精读档案与演讲书稿。
          默认支持本地 Ollama、OpenRouter 与硅基流动；服务地址与 API Key 只保存在本机浏览器。
          生成结果进入“我的书架”，可随时删除或重新生成。
        </p>
      </article>
      <article class="about-card">
        <h2>隐私说明</h2>
        <p>
          本项目没有服务器，也不收集数据。唯一的持久化存储是浏览器 localStorage，用来记住收藏、字号、阅读进度与自定义书。
          清除浏览器站点数据即可全部删除。
        </p>
      </article>
    </section>

    <section class="about-steps">
      <h2>如何继续扩充目录</h2>
      <ol>
        <li>在 <code>data/catalog.csv</code> 末尾按同样字段追加作品，填写 <code>batch</code> 为下一个批次号。</li>
        <li>运行 <code>npm run new-batch -- 61</code>，自动生成档案模板；或直接运行 <code>npm run dossiers</code> 批量生成完整档案。</li>
        <li>按需修改生成的故事情节、阅读所获、章句、演讲书稿与章节导读，或在 <code>scripts/curated-quotes.json</code> 中补充逐书名句。</li>
        <li>运行 <code>npm run build</code>，目录与详情会自动打包进静态站点。</li>
      </ol>
      <p class="about-note">
        当前馆内 {{ summary.covered }} 部作品全部配有完整精读档案与演讲书稿。
      </p>
    </section>

    <section class="about-usage">
      <h2>使用建议</h2>
      <div class="usage-grid">
        <div>
          <strong>1. 从总目录开始</strong>
          <p>按类别、年代、推荐等级筛选，或直接搜索作者、朝代、主题。</p>
        </div>
        <div>
          <strong>2. 先听一遍讲稿</strong>
          <p>在详情页点击“每日演讲书稿”，再按“从头播放”，适合通勤或做家务时听；右上角可切换“主持人 / 朗诵”语气。</p>
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