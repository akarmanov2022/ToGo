<script setup lang="ts">
import { ref } from 'vue'
import { LocationPicker, type Coordinates } from '@/modules/Map'
import { categories } from './data'
const coordinates = ref<Coordinates | null>(null)
const title = ref(''),
  category = ref('Кафе'),
  place = ref(''),
  min = ref(1),
  max = ref(4)
</script>
<template>
  <section class="create">
    <RouterLink class="back-link" to="/">‹ <span>Назад к событиям</span></RouterLink>
    <h1>Новое событие</h1>
    <div class="create-layout">
      <form class="event-form" @submit.prevent>
        <label>Название<input v-model="title" placeholder="Например, кофе и десерты" /></label>
        <fieldset>
          <legend>Категория</legend>
          <div class="categories">
            <button
              v-for="item in categories"
              :key="item"
              type="button"
              :class="['chip', { selected: category === item }]"
              @click="category = item"
            >
              {{ item }}
            </button>
          </div>
        </fieldset>
        <div class="form-row">
          <label>Дата<input type="date" /></label><label>Время<input type="time" /></label>
        </div>
        <label>Место<input v-model="place" placeholder="Например, Невский проспект, 28" /></label>
        <LocationPicker v-model="coordinates" :address="place" />
        <div class="participants-fields">
          <label
            >Минимум участников<input v-model.number="min" type="number" min="1" max="50" /></label
          ><label
            >Максимум участников<input v-model.number="max" type="number" min="1" max="50"
          /></label>
        </div>
        <div class="form-actions">
          <button type="submit" class="button" disabled>Создать встречу</button
          ><RouterLink class="button secondary" to="/">Отмена</RouterLink>
        </div>
        <p class="demo-label">Сохранение будет доступно после подключения API.</p>
      </form>
      <aside class="preview">
        <h2>Так увидят другие</h2>
        <div class="event-card preview-card">
          <div class="event-icon pink" />
          <div class="event-info">
            <h3>{{ title || 'Название события' }}</h3>
            <p>{{ category }} · Дата и время</p>
            <p>{{ place || 'Место' }} · до {{ max }} участников</p>
          </div>
        </div>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.participants-fields {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
</style>
