import { createRouter, createWebHashHistory } from 'vue-router'
import CatalogView from './views/CatalogView.vue'
import BookDetailView from './views/BookDetailView.vue'
import ReadView from './views/ReadView.vue'
import AboutView from './views/AboutView.vue'
import CustomBookView from './views/CustomBookView.vue'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', name: 'catalog', component: CatalogView },
    { path: '/book/:id', name: 'book', component: BookDetailView, props: true },
    { path: '/book/:id/read', name: 'read', component: ReadView, props: true },
    { path: '/about', name: 'about', component: AboutView },
    { path: '/custom', name: 'custom', component: CustomBookView },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ],
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) return savedPosition
    return { top: 0 }
  }
})

export default router