<script setup lang="ts">
import AppCard from '@/common/ui/AppCard.vue'

defineProps<{ title: string; meta: string; place: string; pink?: boolean; accent?: boolean }>()
</script>
<template>
  <AppCard as="article" :accent="accent" class="event-card">
    <div :class="['icon', { pink }]" />
    <div class="info">
      <h2>{{ title }}</h2>
      <p>{{ meta }}</p>
      <p>{{ place }}</p>
    </div>
    <div v-if="$slots.default" class="action"><slot /></div>
  </AppCard>
</template>
<style scoped>
.event-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.icon {
  width: 44px;
  height: 44px;
  flex: none;
  border-radius: var(--radius-md);
  background: var(--color-blue-soft);
}
.pink {
  background: var(--color-pink-soft);
}
.info {
  flex: 1;
}
h2 {
  font-size: 16px;
  margin-bottom: 3px;
}
p {
  font-size: 12px;
  color: var(--color-text-muted);
  margin-top: 3px;
}
.action {
  margin-top: 8px;
}
@media (max-width: 700px) {
  .event-card {
    display: grid;
    grid-template-columns: 44px 1fr;
    --card-padding: 14px;
  }
  .action {
    grid-column: 1 / -1;
    margin: 0;
  }
}
</style>
