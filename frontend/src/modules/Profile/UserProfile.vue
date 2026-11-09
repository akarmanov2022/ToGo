<script setup lang="ts">
import { ref } from 'vue'
import AppButton from '@/common/ui/AppButton.vue'
import AppCard from '@/common/ui/AppCard.vue'
import AppChip from '@/common/ui/AppChip.vue'
import ChipGroup from '@/common/ui/ChipGroup.vue'

const tabs = ['Я иду', 'Мои события', 'Прошедшие']
const tab = ref(tabs[0])
const events = ['Кофе и десерты', 'Выставка в музее', 'Утренняя пробежка']
const interests = ['Кафе', 'Выставки', 'Спорт', 'Кино', 'Настолки']
</script>
<template>
  <section class="layout">
    <AppCard as="aside" class="card">
      <div class="avatar">С</div>
      <h2>София</h2>
      <p>Люблю кофе, выставки и прогулки</p>
      <div class="stats">
        <div><strong>12</strong>создано</div>
        <div><strong>28</strong>встреч</div>
      </div>
      <div class="interests">
        <AppChip v-for="item in interests" :key="item" as="span">{{ item }}</AppChip>
      </div>
      <AppButton block disabled>Редактировать профиль</AppButton>
      <AppButton block to="/login" variant="secondary">К экрану входа</AppButton>
    </AppCard>
    <div>
      <h1>Профиль</h1>
      <p class="demo-label">Демонстрационный профиль</p>
      <ChipGroup class="tabs">
        <AppChip v-for="item in tabs" :key="item" :active="tab === item" @click="tab = item">
          {{ item }}
        </AppChip>
      </ChipGroup>
      <template v-if="tab === 'Я иду'">
        <AppCard v-for="(item, i) in events" :key="item" class="event">
          <div :class="['icon', { pink: i % 2 === 0 }]" />
          <div class="event-info">
            <h3>{{ item }}</h3>
            <p>{{ i === 0 ? 'Кафе · Сегодня, 19:00 · Центр' : 'Событие · Скоро' }}</p>
          </div>
          <AppButton variant="secondary" disabled>Подробнее</AppButton>
        </AppCard>
      </template>
      <p v-else class="empty">Здесь появятся ваши события</p>
    </div>
  </section>
</template>
<style scoped>
.layout {
  display: grid;
  grid-template-columns: 300px 1fr;
  gap: 40px;
}
.card {
  --card-radius: 16px;
  --card-padding: 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  text-align: center;
}
.card h2 {
  font-size: 22px;
}
.card p {
  font-size: 12px;
}
.avatar {
  height: 90px;
  width: 90px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, #fbcde3, #cddfff);
  font-size: 38px;
  font-weight: 900;
  color: var(--color-blue);
}
.stats {
  display: flex;
  gap: 30px;
}
.stats div {
  font-size: 11px;
  display: flex;
  flex-direction: column;
}
.stats strong {
  font-size: 23px;
  font-weight: 900;
}
.interests {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 6px;
}
h1 {
  margin-bottom: 4px;
}
.tabs {
  margin: 16px 0;
}
.event {
  --card-radius: var(--radius-md);
  --card-padding: 14px;
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 12px;
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
.event-info {
  flex: 1;
}
.event-info h3 {
  font-size: 16px;
  margin-bottom: 3px;
}
.event-info p {
  font-size: 12px;
  color: var(--color-text-muted);
  margin-top: 3px;
}
.empty {
  font-size: 14px;
  color: var(--color-text-subtle);
  padding: 24px 0;
}
@media (max-width: 700px) {
  .layout {
    display: flex;
    flex-direction: column;
    gap: 24px;
  }
  .card {
    gap: 10px;
  }
  .avatar {
    width: 70px;
    height: 70px;
    font-size: 30px;
  }
  h1 {
    font-size: 25px;
  }
  .event {
    gap: 10px;
  }
}
</style>
