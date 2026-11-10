<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { EventDetail } from '@/modules/Events'

const route = useRoute()
const eventId = computed(() => String(route.params.eventId))
</script>
<template>
  <main class="container"><EventDetail :event-id="eventId" /></main>
</template>
