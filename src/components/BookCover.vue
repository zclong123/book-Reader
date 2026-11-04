<script setup>
import { computed } from 'vue'

const props = defineProps({
  book: { type: Object, required: true },
  size: { type: String, default: 'md' }
})

const palettes = {
  古典小说: ['#6f2c25', '#c69a63'],
  诗词曲赋: ['#365d55', '#b99b64'],
  文史哲: ['#3c4659', '#b19062'],
  近现代文学: ['#7a4c2f', '#c9a978'],
  外国文学: ['#4a3d63', '#b79b6a']
}

const palette = computed(() => palettes[props.book.category] ?? ['#54483c', '#c3a67b'])

const style = computed(() => ({
  '--cover-dark': palette.value[0],
  '--cover-light': palette.value[1]
}))

const levelText = computed(() => props.book.level || '经典')
</script>

<template>
  <div class="book-cover" :class="`book-cover--${size}`" :style="style">
    <span class="book-cover__rule"></span>
    <span class="book-cover__level">{{ levelText }}</span>
    <span class="book-cover__title">{{ book.title }}</span>
    <span class="book-cover__author">{{ book.author }}</span>
    <span class="book-cover__seal">典</span>
  </div>
</template>