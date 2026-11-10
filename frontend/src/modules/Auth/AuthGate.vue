<script setup lang="ts">
import { useAuthStore } from './session'
defineProps<{ mockMode: boolean }>()
defineEmits<{ retry: [] }>()
const auth = useAuthStore()
</script>

<template>
  <slot v-if="auth.status === 'authenticated'" />
  <main v-else class="login">
    <section class="login-hero">
      <div class="login-hero-content">
        <div class="animated-logo" aria-label="ToGo">
          <span>To</span><span class="together">gether</span><span class="go">Go</span>
        </div>
        <h1>Просто пойти вместе</h1>
        <p>Находи компанию для кафе, выставок и спорта по интересам, месту и времени.</p>
      </div>
    </section>
    <section class="login-panel" aria-live="polite">
      <div class="login-panel-content">
        <template v-if="mockMode && auth.status !== 'loading'">
          <h2>Добро пожаловать в ToGo</h2>
          <p>Посмотрите приложение без Telegram с тестовым профилем.</p>
          <p v-if="auth.status === 'error'" role="alert">{{ auth.errorMessage }}</p>
          <button type="button" class="button" @click="$emit('retry')">Войти в демо</button>
        </template>
        <template v-else-if="auth.status === 'idle' || auth.status === 'loading'"
          ><h2>Открываем ToGo</h2>
          <p>{{ mockMode ? 'Входим в демо…' : 'Выполняется вход через Telegram…' }}</p></template
        >
        <template v-else-if="auth.status === 'outside'"
          ><h2>Откройте ToGo в Telegram</h2>
          <p>Запустите приложение кнопкой в чате бота.</p></template
        >
        <template v-else-if="auth.status === 'expired'"
          ><h2>Сессия завершена</h2>
          <p>Закройте Mini App и откройте заново через бота.</p></template
        >
        <template v-else
          ><h2>Не удалось войти</h2>
          <p role="alert">{{ auth.errorMessage }}</p>
          <button class="button" @click="$emit('retry')">Повторить</button></template
        >
      </div>
    </section>
  </main>
</template>
