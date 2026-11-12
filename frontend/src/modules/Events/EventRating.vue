<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { ApiError } from '@/common/api/client'
import { rateEvent, type EventDto } from './api'

const props = defineProps<{ event: EventDto }>()
const client = useQueryClient()
const error = ref('')
const canRate = computed(
  () => props.event.state === 'finished' && props.event.isParticipant && !props.event.isOrganizer,
)
const mutation = useMutation({
  mutationFn: ({ id, score }: { id: string; score: number }) => rateEvent(id, score),
  onSuccess: async (event) => {
    client.setQueryData(['events', event.id], event)
    error.value = ''
    await Promise.all([
      client.invalidateQueries({ queryKey: ['events'] }),
      client.invalidateQueries({ queryKey: ['current-user'] }),
      client.invalidateQueries({ queryKey: ['users'] }),
    ])
  },
  onError: (cause) => {
    error.value =
      cause instanceof ApiError ? cause.message : 'Не удалось отправить оценку. Попробуйте ещё раз.'
  },
})
watch(
  () => props.event.id,
  () => {
    error.value = ''
    mutation.reset()
  },
)
function rate(score: number) {
  if (!canRate.value || props.event.myRating != null || mutation.isPending.value) return
  error.value = ''
  mutation.mutate({ id: props.event.id, score })
}
</script>

<template>
  <section v-if="event.rating || canRate" class="event-rating" aria-label="Оценка события">
    <h2>Оценка события</h2>
    <p v-if="event.rating" class="average">
      <span aria-hidden="true">★</span> <strong>{{ event.rating.average.toFixed(1) }}</strong>
      <span class="count">Оценок: {{ event.rating.count }}</span>
    </p>
    <template v-if="canRate">
      <p class="hint">
        {{
          event.myRating != null
            ? 'Спасибо! Ваша оценка сохранена.'
            : 'Как прошла встреча? Оценку нельзя изменить.'
        }}
      </p>
      <div
        class="stars"
        role="group"
        aria-label="Ваша оценка события"
        :aria-busy="mutation.isPending.value"
      >
        <button
          v-for="score in 5"
          :key="score"
          type="button"
          :class="{ filled: score <= (event.myRating ?? 0) }"
          :disabled="event.myRating != null || mutation.isPending.value"
          :aria-label="`${score} из 5`"
          :aria-pressed="event.myRating === score"
          @click="rate(score)"
        >
          ★
        </button>
      </div>
      <p v-if="mutation.isPending.value" class="hint" role="status">Сохраняем оценку…</p>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
    </template>
  </section>
</template>

<style scoped>
.event-rating {
  margin: 24px 0;
  padding: 20px;
  border: 1px solid var(--color-border);
  border-radius: 16px;
  background: var(--color-surface);
}
h2 {
  margin: 0 0 12px;
  font-size: 18px;
}
.average {
  display: flex;
  align-items: center;
  gap: 8px;
}
.average > span:first-child {
  color: #ca900d;
  font-size: 24px;
}
.count,
.hint {
  color: var(--color-text-subtle);
  font-size: 12px;
}
.hint {
  margin: 10px 0;
}
.stars {
  display: flex;
  gap: 6px;
}
.stars button {
  width: 44px;
  height: 44px;
  padding: 0;
  border: 1px solid var(--color-border);
  border-radius: 10px;
  background: var(--color-surface);
  color: #9894ac;
  font-size: 30px;
  cursor: pointer;
}
.stars button:not(:disabled):hover,
.stars button.filled {
  color: #ca900d;
  background: #fff3d6;
  border-color: #e5c77c;
}
.stars button:disabled {
  cursor: default;
}
.stars button:focus-visible {
  outline: 2px solid var(--color-blue);
  outline-offset: 2px;
}
.error {
  color: #c62828;
  font-size: 13px;
  margin-top: 12px;
}
</style>
