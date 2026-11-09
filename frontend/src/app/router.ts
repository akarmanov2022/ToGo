import { createRouter, createWebHistory } from 'vue-router'
import LoginPage from '@/pages/LoginPage.vue'
import EventsPage from '@/pages/EventsPage.vue'
import CreatePage from '@/pages/CreatePage.vue'
import ProfilePage from '@/pages/ProfilePage.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: LoginPage, meta: { standalone: true } },
    { path: '/', component: EventsPage },
    { path: '/create', component: CreatePage },
    { path: '/profile', component: ProfilePage },
  ],
})
