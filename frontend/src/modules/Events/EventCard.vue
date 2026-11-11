<script setup lang="ts">
import AppCard from '@/common/ui/AppCard.vue'
import type { components } from '@/common/api/schema'

defineProps<{
  title: string
  meta: string
  place: string
  pink?: boolean
  accent?: boolean
  to?: string
  state?: string
  organizer?: components['schemas']['UserSummary']
  icon?: string | null
}>()
</script>
<template>
  <AppCard as="article" :accent="accent" class="event-card">
    <RouterLink v-if="to" class="hit" :to="to" :aria-label="title" />
    <div :class="['icon', { pink }]" aria-hidden="true">{{ icon }}</div>
    <div class="info">
      <div class="title-row">
        <h2>{{ title }}</h2>
        <span v-if="state" class="state">{{ state }}</span>
      </div>
      <p>{{ meta }}</p>
      <p>{{ place }}</p>
      <RouterLink v-if="organizer" class="organizer" :to="`/users/${organizer.id}`"
        >Организатор:
        {{ organizer.tgLogin ? `@${organizer.tgLogin}` : 'профиль участника' }}</RouterLink
      >
    </div>
    <div v-if="$slots.default" class="action"><slot /></div>
  </AppCard>
</template>
<style scoped>
.event-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.hit {
  position: absolute;
  inset: 0;
  z-index: 1;
  border-radius: inherit;
}
.icon {
  display: grid;
  place-items: center;
  font-size: 25px;
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
.title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 3px;
}
h2 {
  font-size: 16px;
}
.state {
  flex: none;
  font-size: 11px;
  font-weight: 800;
  color: var(--color-text-subtle);
}
p {
  font-size: 12px;
  color: var(--color-text-muted);
  margin-top: 3px;
}
.action {
  position: relative;
  z-index: 2;
  margin-top: 8px;
}
.organizer {
  display: inline-block;
  position: relative;
  z-index: 2;
  margin-top: 8px;
  font-size: 12px;
  font-weight: 700;
  color: var(--color-blue);
}
.organizer:hover {
  text-decoration: underline;
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
