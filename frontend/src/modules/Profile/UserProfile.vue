<script setup lang="ts">
import { ref } from 'vue'
const tabs = ['Я иду', 'Мои события', 'Прошедшие']
const tab = ref(tabs[0])
const events = ['Кофе и десерты', 'Выставка в музее', 'Утренняя пробежка']
</script>
<template>
  <section class="profile-layout">
    <aside class="profile-card">
      <div class="avatar">С</div>
      <h2>София</h2>
      <p>Люблю кофе, выставки и прогулки</p>
      <div class="stats">
        <div><strong>12</strong>создано</div>
        <div><strong>28</strong>встреч</div>
      </div>
      <div class="profile-interests">
        <span
          v-for="item in ['Кафе', 'Выставки', 'Спорт', 'Кино', 'Настолки']"
          :key="item"
          class="chip"
          >{{ item }}</span
        >
      </div>
      <button class="button" disabled>Редактировать профиль</button
      ><RouterLink class="button secondary" to="/login">К экрану входа</RouterLink>
    </aside>
    <div class="profile-events">
      <h1>Профиль</h1>
      <p class="demo-label">Демонстрационный профиль</p>
      <div class="categories">
        <button
          v-for="item in tabs"
          :key="item"
          :class="['chip', { active: tab === item }]"
          @click="tab = item"
        >
          {{ item }}
        </button>
      </div>
      <template v-if="tab === 'Я иду'"
        ><div v-for="(item, i) in events" :key="item" class="profile-event">
          <div :class="['event-icon', { pink: i % 2 === 0 }]" />
          <div>
            <h3>{{ item }}</h3>
            <p>{{ i === 0 ? 'Кафе · Сегодня, 19:00 · Центр' : 'Событие · Скоро' }}</p>
          </div>
          <button class="button secondary" disabled>Подробнее</button>
        </div></template
      >
      <p v-else class="empty">Здесь появятся ваши события</p>
    </div>
  </section>
</template>
