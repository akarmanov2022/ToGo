<script setup lang="ts">
import { computed, ref } from 'vue'
import { categories, events } from './data'
const category = ref('Все')
const visible = computed(() =>
  category.value === 'Все' ? events : events.filter((event) => event.category === category.value),
)
</script>
<template>
  <section class="feed">
    <div class="section-heading">
      <div>
        <h1>Куда отправляемся?</h1>
        <p class="demo-label">Демонстрационные события</p>
      </div>
      <div class="categories" aria-label="Категории">
        <button
          v-for="item in ['Все', ...categories]"
          :key="item"
          :class="['chip', { active: category === item }]"
          @click="category = item"
        >
          {{ item }}
        </button>
      </div>
    </div>
    <div class="event-grid">
      <article v-for="(event, i) in visible" :key="event.title" class="event-card">
        <div :class="['event-icon', { pink: i % 2 }]" />
        <div class="event-info">
          <h2>{{ event.title }}</h2>
          <p>{{ event.category }} · {{ event.time }}</p>
          <p>{{ event.place }} · ещё {{ event.places }} места</p>
        </div>
        <button class="button join-button" disabled>Присоединиться</button>
      </article>
    </div>
  </section>
</template>
