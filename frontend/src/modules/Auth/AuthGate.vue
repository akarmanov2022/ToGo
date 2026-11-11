<script setup lang="ts">
import { useAuthStore } from './session'
import AppButton from '@/common/ui/AppButton.vue'
import LoadingText from '@/common/ui/LoadingText.vue'
defineProps<{ mockMode: boolean }>()
defineEmits<{ retry: [] }>()
const auth = useAuthStore()
</script>

<template>
  <slot v-if="auth.status === 'authenticated'" />
  <main v-else class="login" :class="{ 'login--compact': !mockMode && auth.status !== 'outside' }">
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
          <span class="login-badge">Демо-режим</span>
          <h2>Посмотрите, как устроен ToGo</h2>
          <p>
            Здесь тестовые события и профиль. Для настоящих встреч откройте приложение через бота в
            Telegram.
          </p>
          <p v-if="auth.status === 'error'" role="alert">{{ auth.errorMessage }}</p>
          <AppButton @click="$emit('retry')">Войти в демо</AppButton>
          <a
            class="login-bot-link"
            href="https://t.me/togo_devdays_bot"
            target="_blank"
            rel="noopener noreferrer"
            >Открыть ToGo в Telegram ↗</a
          >
        </template>
        <template v-else-if="auth.status === 'idle' || auth.status === 'loading'"
          ><span class="login-badge">{{ mockMode ? 'Демо-режим' : 'Telegram Mini App' }}</span>
          <h2><LoadingText label="Открываем ToGo" /></h2>
          <p>
            {{
              mockMode
                ? 'Загружаем тестовые события.'
                : 'Проверяем данные Telegram. Вход и регистрация происходят автоматически.'
            }}
          </p></template
        >
        <template v-else-if="auth.status === 'outside'"
          ><span class="login-badge">Telegram Mini App</span>
          <h2>Откройте ToGo в Telegram</h2>
          <p>Найдите @togo_devdays_bot и нажмите «Открыть ToGo». Отдельная регистрация не нужна.</p>
          <a
            class="button"
            href="https://t.me/togo_devdays_bot"
            target="_blank"
            rel="noopener noreferrer"
            >Открыть бота ToGo</a
          ></template
        >
        <template v-else-if="auth.status === 'expired'"
          ><h2>Сессия завершена</h2>
          <p>
            Для безопасности нужно обновить данные Telegram. Закройте Mini App и откройте заново
            через @togo_devdays_bot.
          </p></template
        >
        <template v-else
          ><h2>Не удалось войти</h2>
          <p role="alert">{{ auth.errorMessage }}</p>
          <AppButton size="sm" variant="secondary" @click="$emit('retry')"
            >Повторить</AppButton
          ></template
        >
      </div>
    </section>
  </main>
</template>
