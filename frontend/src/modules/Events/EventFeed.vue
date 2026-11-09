<script setup lang="ts">
import { computed, ref } from 'vue'
import AppButton from '@/common/ui/AppButton.vue'
import AppChip from '@/common/ui/AppChip.vue'
import ChipGroup from '@/common/ui/ChipGroup.vue'
import { categories, events } from './data'
import EventCard from './EventCard.vue'

const category = ref('Все')
const visible = computed(() =>
  category.value === 'Все' ? events : events.filter((event) => event.category === category.value),
)
</script>
<template>
  <section>
    <div class="heading">
      <div>
        <h1>Куда отправляемся?</h1>
        <p class="demo-label">Демонстрационные события</p>
      </div>
      <ChipGroup scrollable class="categories" aria-label="Категории">
        <AppChip
          v-for="item in ['Все', ...categories]"
          :key="item"
          :active="category === item"
          @click="category = item"
        >
          {{ item }}
        </AppChip>
      </ChipGroup>
    </div>
    <div class="grid">
      <EventCard
        v-for="(event, i) in visible"
        :key="event.title"
        :title="event.title"
        :meta="`${event.category} · ${event.time}`"
        :place="`${event.place} · ещё ${event.places} места`"
        :pink="i % 2 === 1"
      >
        <AppButton block disabled>Присоединиться</AppButton>
      </EventCard>
    </div>
  </section>
</template>
<style scoped>
.heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 24px;
}
.grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 18px;
}
@media (max-width: 700px) {
  .heading {
    display: block;
    margin-bottom: 18px;
  }
  h1 {
    font-size: 25px;
  }
  .categories {
    margin-top: 16px;
  }
  .grid {
    grid-template-columns: 1fr;
    gap: 12px;
  }
}
</style>
