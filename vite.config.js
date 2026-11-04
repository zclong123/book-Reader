import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import gutenbergProxy from './vite.gutenberg.mjs'
import textManifest from './vite.text-manifest.mjs'

export default {
  base: './',
  plugins: [vue(), textManifest(), gutenbergProxy()],
  server: { port: 5173, open: false },
  build: { outDir: 'dist', assetsDir: 'assets' }
}
