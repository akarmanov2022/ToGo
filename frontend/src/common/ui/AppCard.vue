<script setup lang="ts">
withDefaults(defineProps<{ as?: string; accent?: boolean }>(), { as: 'div' })
</script>
<template>
  <component :is="as" :class="['card', { accent }]"><slot /></component>
</template>
<style scoped>
.card {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--card-radius, var(--radius-lg));
  padding: var(--card-padding, 17px);
}
.accent {
  border: 2px solid var(--color-pink-strong);
}
</style>
