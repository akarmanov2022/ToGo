<script setup lang="ts">
import { useRoute } from 'vue-router'
import AppLayout from './AppLayout.vue'
const route = useRoute()
</script>
<template>
  <RouterView v-if="route.meta.standalone" /><AppLayout v-else><RouterView /></AppLayout>
</template>
