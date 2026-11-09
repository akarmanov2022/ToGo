<script setup lang="ts">
import { ref } from 'vue'
import AppButton from '@/common/ui/AppButton.vue'
import AppChip from '@/common/ui/AppChip.vue'
import AppField from '@/common/ui/AppField.vue'
import ChipGroup from '@/common/ui/ChipGroup.vue'
import { LocationPicker, type Coordinates } from '@/modules/Map'
import { categories } from './data'
import EventCard from './EventCard.vue'

const title = ref('')
const category = ref('Кафе')
const place = ref('')
const min = ref(1)
const max = ref(4)
const coordinates = ref<Coordinates | null>(null)
</script>
<template>
  <section>
    <RouterLink class="back" to="/">‹ <span>Назад к событиям</span></RouterLink>
    <h1>Новое событие</h1>
    <div class="layout">
      <form class="form" @submit.prevent>
        <AppField v-model="title" label="Название" placeholder="Например, кофе и десерты" />
        <fieldset>
          <legend>Категория</legend>
          <ChipGroup>
            <AppChip
              v-for="item in categories"
              :key="item"
              tone="pink"
              :active="category === item"
              @click="category = item"
            >
              {{ item }}
            </AppChip>
          </ChipGroup>
        </fieldset>
        <div class="row">
          <AppField label="Дата" type="date" />
          <AppField label="Время" type="time" />
        </div>
        <AppField v-model="place" label="Место" placeholder="Например, Невский проспект, 28" />
        <LocationPicker v-model="coordinates" :address="place" />
        <div class="row">
          <AppField v-model="min" label="Минимум участников" type="number" min="1" max="50" />
          <AppField v-model="max" label="Максимум участников" type="number" min="1" max="50" />
        </div>
        <div class="actions">
          <AppButton type="submit" disabled>Создать встречу</AppButton>
          <AppButton to="/" variant="secondary">Отмена</AppButton>
        </div>
        <p class="demo-label">Сохранение будет доступно после подключения API.</p>
      </form>
      <aside>
        <h2>Так увидят другие</h2>
        <EventCard
          accent
          pink
          :title="title || 'Название события'"
          :meta="`${category} · Дата и время`"
          :place="`${place || 'Место'} · до ${max} участников`"
        />
      </aside>
    </div>
  </section>
</template>
<style scoped>
h1 {
  margin-bottom: 22px;
}
.back {
  display: none;
}
.layout {
  display: grid;
  grid-template-columns: 1.35fr 1fr;
  gap: 48px;
}
.form {
  display: flex;
  flex-direction: column;
  gap: 18px;
}
fieldset {
  border: 0;
  margin: 0;
  padding: 0;
}
legend,
aside h2 {
  font-size: 12px;
  font-weight: 800;
}
legend {
  margin-bottom: 8px;
}
aside h2 {
  margin-bottom: 10px;
}
.row {
  display: flex;
  gap: 15px;
}
.actions {
  display: flex;
  gap: 10px;
}
@media (max-width: 700px) {
  h1 {
    font-size: 25px;
  }
  .back {
    display: inline-block;
    font-size: 22px;
    margin-bottom: 10px;
  }
  .back span {
    font-size: 12px;
    vertical-align: middle;
  }
  .layout {
    display: flex;
    flex-direction: column;
    gap: 24px;
  }
  .form {
    gap: 16px;
  }
  .actions > * {
    flex: 1;
  }
}
</style>
