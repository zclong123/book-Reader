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
        汇集古今中外作品，提供精读内容、作者介绍、演讲书稿与原书阅读入口。
        你可以按兴趣探索，也可以把自己的书加入书架。
      </p>
    </section>

    <section class="about-grid">
      <article class="about-card">
        <h2>馆藏目录</h2>
        <p>
          目前收录 {{ summary.total }} 部作品，分为 {{ summary.categories }} 个类别，
          其中 {{ summary.covered }} 部配有精读内容。可按分类、年代与推荐等级浏览，也可搜索书名和作者。
        </p>
      </article>
      <article class="about-card">
        <h2>精读内容</h2>
        <p>精选作品提供故事梗概、阅读所获、章句赏析、章节导读与每日演讲书稿，帮助你更轻松地进入作品。</p>
      </article>
      <article class="about-card">
        <h2>作者与作品</h2>
        <p>作品详情页收录作者简介，并列出本馆中同一作者的其他作品，可直接前往阅读。</p>
      </article>
      <article class="about-card">
        <h2>朗读与跟读</h2>
        <p>支持朗读精读内容和讲稿，也可以录下自己的朗读并回放，调整语速与停顿。</p>
      </article>
      <article class="about-card">
        <h2>原书阅读</h2>
        <p>可阅读馆藏全文，也可以导入自己的电子书，按章节阅读并保存阅读进度。</p>
      </article>
      <article class="about-card">
        <h2>自定义书架</h2>
        <p>添加感兴趣的作品，创建速览或使用 AI 辅助补充精读内容。生成内容建议结合原书核对。</p>
      </article>
      <article class="about-card">
        <h2>隐私与记录</h2>
        <p>收藏、阅读进度和自定义书由你的设备记住；录音可回放或下载，不会自动上传。</p>
      </article>
    </section>

    <section class="about-usage">
      <h2>开始阅读</h2>
      <div class="usage-grid">
        <div>
          <strong>1. 探索目录</strong>
          <p>按类别或年代筛选，也可以搜索作者、朝代与主题。</p>
        </div>
        <div>
          <strong>2. 认识作者与作品</strong>
          <p>打开作品详情，阅读作者介绍与馆藏作品列表。</p>
        </div>
        <div>
          <strong>3. 听读与跟读</strong>
          <p>收听讲稿或精读内容，也可以录下自己的朗读并回放。</p>
        </div>
        <div>
          <strong>4. 阅读原书</strong>
          <p>选择馆藏全文，或导入电子书继续按章节阅读。</p>
        </div>
      </div>
    </section>
  </div>
</template>
