<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Profile } from '@/modules/Profile'

const route = useRoute()
const userId = computed(() => String(route.params.userId))
</script>
<template>
  <main class="container"><Profile :user-id="userId" /></main>
</template>
