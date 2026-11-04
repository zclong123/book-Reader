<script setup>
import { computed } from 'vue'
import { todayBook } from './data/library.js'

const today = computed(() => todayBook())
</script>

<template>
  <div class="app-shell">
    <header class="site-header">
      <RouterLink class="brand" to="/">
        <span class="brand-mark">典</span>
        <span class="brand-copy">
          <strong>古今名著精读馆</strong>
          <small>读一部 · 懂一部</small>
        </span>
      </RouterLink>

      <nav class="site-nav">
        <RouterLink to="/">总目录</RouterLink>
        <RouterLink :to="`/book/${today.id}`">今日一书</RouterLink>
        <RouterLink to="/about">关于</RouterLink>
      </nav>
    </header>

    <main class="page-main">
      <RouterView />
    </main>

    <footer class="site-footer">
      <p>古今名著精读馆 · 纯前端本地阅读空间</p>
      <p>所有朗读与录音均在本机完成，数据不会上传。</p>
    </footer>
  </div>
</template>