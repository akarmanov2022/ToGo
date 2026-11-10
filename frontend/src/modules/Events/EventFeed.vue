<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ApiError, apiRequest } from '@/common/api/client'
import type { components } from '@/common/api/schema'
import { cardAction, isVisibleInFeed, stateLabel } from './actions'
import { formatStart, placesLine } from './format'

type Event = components['schemas']['Event']

const queryClient = useQueryClient()
const category = ref<string | null>(null)
const now = ref(new Date())

const categoriesQuery = useQuery({
  queryKey: ['categories'],
  queryFn: () => apiRequest<components['schemas']['Category'][]>('/api/v1/categories'),
})
const categoryNames = computed(
  () => new Map((categoriesQuery.data.value ?? []).map((item) => [item.code, item.name])),
)

const feed = useQuery({
  queryKey: ['events', 'feed', category],
  queryFn: ({ signal }) => {
    const params = new URLSearchParams({ startsFrom: now.value.toISOString(), limit: '100' })
    if (category.value) params.set('category', category.value)
    return apiRequest<components['schemas']['EventPage']>(`/api/v1/events?${params}`, { signal })
  },
})
const events = computed(() => (feed.data.value?.items ?? []).filter(isVisibleInFeed))
watch(
  () => feed.data.value,
  () => {
    now.value = new Date()
  },
)

const actionError = ref('')
const confirmingId = ref<string | null>(null)

function report(error: unknown, fallback: string) {
  actionError.value = error instanceof ApiError ? error.message : fallback
}

const join = useMutation({
  mutationFn: (eventId: string) =>
    apiRequest<Event>(`/api/v1/events/${eventId}/participants/me`, { method: 'PUT' }),
  onSuccess: async () => {
    actionError.value = ''
    await queryClient.invalidateQueries({ queryKey: ['events'] })
  },
  onError: (error) => report(error, 'Не удалось присоединиться. Проверьте подключение.'),
})
const leave = useMutation({
  mutationFn: (eventId: string) =>
    apiRequest<void>(`/api/v1/events/${eventId}/participants/me`, { method: 'DELETE' }),
  onSuccess: async () => {
    actionError.value = ''
    await queryClient.invalidateQueries({ queryKey: ['events'] })
  },
  onError: (error) => report(error, 'Не удалось отписаться. Проверьте подключение.'),
})
const cancel = useMutation({
  // Отмена организатором по аналитике — только после подтверждения (см. confirm ниже).
  mutationFn: (eventId: string) =>
    apiRequest<Event>(`/api/v1/events/${eventId}/cancel`, { method: 'POST' }),
  onSuccess: async () => {
    actionError.value = ''
    confirmingId.value = null
    await queryClient.invalidateQueries({ queryKey: ['events'] })
  },
  onError: (error) => report(error, 'Не удалось отменить событие. Проверьте подключение.'),
})
const busy = computed(() => join.isPending.value || leave.isPending.value || cancel.isPending.value)

function run(event: Event) {
  const action = cardAction(event, now.value)
  if (action.kind === 'join') join.mutate(event.id)
  else if (action.kind === 'leave') leave.mutate(event.id)
  else if (action.kind === 'cancel') confirmingId.value = event.id
}
</script>

<template>
  <section class="feed">
    <div class="section-heading">
      <div>
        <h1>Куда отправляемся?</h1>
      </div>
      <div class="categories" aria-label="Категории">
        <button
          type="button"
          :class="['chip', { active: category === null }]"
          @click="category = null"
        >
          Все
        </button>
        <button
          v-for="item in categoriesQuery.data.value ?? []"
          :key="item.code"
          type="button"
          :class="['chip', { active: category === item.code }]"
          @click="category = item.code"
        >
          {{ item.name }}
        </button>
      </div>
    </div>

    <p v-if="actionError" class="feed-message feed-error" role="alert">{{ actionError }}</p>

    <p v-if="feed.isPending.value" class="feed-message">Загружаем события…</p>
    <div v-else-if="feed.isError.value" class="feed-message">
      <p>Не удалось загрузить события.</p>
      <button type="button" class="button secondary" @click="feed.refetch()">Повторить</button>
    </div>
    <p v-else-if="events.length === 0" class="feed-message">Событий не найдено</p>

    <div v-else class="event-grid">
      <article v-for="(event, i) in events" :key="event.id" class="event-card">
        <div :class="['event-icon', { pink: i % 2 }]" />
        <div class="event-info">
          <h2>{{ event.title }}</h2>
          <p>
            {{ stateLabel(event.state) }} ·
            {{ categoryNames.get(event.category) ?? event.category }} ·
            {{ formatStart(event.startsAt) }}
          </p>
          <p>{{ event.location }} · {{ placesLine(event) }}</p>
        </div>
        <div
          v-if="confirmingId === event.id"
          class="confirm"
          role="dialog"
          aria-label="Подтверждение отмены события"
        >
          <p>Отменить событие? Участники получат уведомление.</p>
          <div class="confirm-actions">
            <button type="button" class="button" :disabled="busy" @click="cancel.mutate(event.id)">
              Да, отменить
            </button>
            <button
              type="button"
              class="button secondary"
              :disabled="busy"
              @click="confirmingId = null"
            >
              Нет
            </button>
          </div>
        </div>
        <button
          v-else
          class="button join-button"
          :class="{ secondary: cardAction(event, now).kind !== 'join' }"
          :disabled="busy || cardAction(event, now).kind === 'unavailable'"
          @click="run(event)"
        >
          {{ cardAction(event, now).label }}
        </button>
      </article>
    </div>
  </section>
</template>

<style scoped>
.feed-message {
  color: #77718d;
  font-size: 14px;
  padding: 24px 0;
}
.feed-error {
  color: #b42318;
}
.confirm {
  border: 1px solid #edcfe2;
  border-radius: 12px;
  padding: 12px;
  font-size: 13px;
}
.confirm p {
  margin: 0 0 10px;
}
.confirm-actions {
  display: flex;
  gap: 8px;
}
.confirm-actions .button {
  padding: 9px 14px;
  font-size: 12px;
}
</style>
