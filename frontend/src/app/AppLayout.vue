<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppHeader from './ui/AppHeader.vue'
import FloatingCreate from './ui/FloatingCreate.vue'
import MobileNav from './ui/MobileNav.vue'

const route = useRoute()
const canCreate = computed(() => route.path !== '/create')
</script>
<template>
  <AppHeader :show-create="canCreate" />
  <slot />
  <FloatingCreate v-if="canCreate" />
  <MobileNav />
</template>
