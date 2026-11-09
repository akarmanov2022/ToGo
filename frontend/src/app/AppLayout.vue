<script setup lang="ts">
import AppHeader from './ui/AppHeader.vue'
import FloatingCreate from './ui/FloatingCreate.vue'
import MobileNav from './ui/MobileNav.vue'
</script>
<template>
  <AppHeader />
  <slot />
  <FloatingCreate />
  <MobileNav />
</template>
