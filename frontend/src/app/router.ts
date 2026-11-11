import { createRouter, createWebHistory } from 'vue-router'
import EventsPage from '@/pages/EventsPage.vue'
import CreatePage from '@/pages/CreatePage.vue'
import ProfilePage from '@/pages/ProfilePage.vue'
import EventPage from '@/pages/EventPage.vue'
import UserPage from '@/pages/UserPage.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', redirect: '/' },
    { path: '/', component: EventsPage },
    { path: '/create', component: CreatePage },
    { path: '/events/:eventId', component: EventPage },
    { path: '/profile', component: ProfilePage },
    { path: '/users/:userId', component: UserPage },
  ],
})
