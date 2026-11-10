<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ApiError } from '@/common/api/client'
import AppButton from '@/common/ui/AppButton.vue'
import AppCard from '@/common/ui/AppCard.vue'
import LoadingText from '@/common/ui/LoadingText.vue'
import {
  cancelEvent,
  fetchCategories,
  fetchEvent,
  fetchParticipants,
  joinEvent,
  leaveEvent,
  updateEvent,
} from './api'
import { formatStart, participantsLine } from './format'
import { availableActions, stateLabel } from './rules'

const props = defineProps<{ eventId: string }>()
const queryClient = useQueryClient()
const actionError = ref('')
const editOpen = ref(false)
const editTitle = ref('')
const editDescription = ref('')
const editMessage = ref('')
const confirmingCancel = ref(false)
const now = ref(new Date())
const clock = setInterval(() => {
  now.value = new Date()
}, 1000)
onUnmounted(() => clearInterval(clock))
watch(
  () => props.eventId,
  () => {
    confirmingCancel.value = false
    editOpen.value = false
    actionError.value = ''
  },
)

const eventQuery = useQuery({
  queryKey: computed(() => ['events', props.eventId]),
  queryFn: () => fetchEvent(props.eventId),
})
const categoriesQuery = useQuery({ queryKey: ['categories'], queryFn: fetchCategories })
const participantsQuery = useQuery({
  queryKey: computed(() => ['events', props.eventId, 'participants']),
  queryFn: () => fetchParticipants(props.eventId),
})

const event = computed(() => eventQuery.data.value)
const categoryName = computed(() => {
  const code = event.value?.category
  if (!code) return ''
  return categoriesQuery.data.value?.find((item) => item.code === code)?.name ?? code
})
const actions = computed(() => (event.value ? availableActions(event.value, now.value) : null))

async function refreshEvent() {
  await queryClient.invalidateQueries({ queryKey: ['events'] })
  await queryClient.invalidateQueries({ queryKey: ['current-user'] })
  await queryClient.invalidateQueries({ queryKey: ['users'] })
}

function explain(error: unknown, fallback: string): string {
  if (error instanceof ApiError) {
    if (error.body?.code === 'SCHEDULE_CONFLICT') {
      return error.message || 'У вас уже есть встреча ближе чем за три часа к этому времени'
    }
    return error.message || fallback
  }
  return fallback
}

const joinMutation = useMutation({
  mutationFn: () => joinEvent(props.eventId),
  onSuccess: async () => {
    actionError.value = ''
    await refreshEvent()
  },
  onError: (error) => {
    actionError.value = explain(error, 'Не удалось присоединиться')
  },
})

const leaveMutation = useMutation({
  mutationFn: async () => {
    await leaveEvent(props.eventId)
    return fetchEvent(props.eventId)
  },
  onSuccess: async () => {
    actionError.value = ''
    await refreshEvent()
  },
  onError: (error) => {
    actionError.value = explain(error, 'Не удалось выйти из события')
  },
})

const cancelMutation = useMutation({
  mutationFn: () => cancelEvent(props.eventId),
  onSuccess: async () => {
    actionError.value = ''
    confirmingCancel.value = false
    await refreshEvent()
  },
  onError: (error) => {
    actionError.value = explain(error, 'Не удалось отменить событие')
  },
})

const editMutation = useMutation({
  mutationFn: () =>
    updateEvent(props.eventId, {
      title: editTitle.value.trim(),
      description: editDescription.value.trim(),
    }),
  onSuccess: async () => {
    editMessage.value = ''
    editOpen.value = false
    await refreshEvent()
  },
  onError: (error) => {
    editMessage.value = explain(error, 'Не удалось сохранить изменения')
  },
})

function openEdit() {
  if (!event.value) return
  editTitle.value = event.value.title
  editDescription.value = event.value.description
  editMessage.value = ''
  editOpen.value = true
}

const busy = computed(
  () =>
    joinMutation.isPending.value ||
    leaveMutation.isPending.value ||
    cancelMutation.isPending.value ||
    editMutation.isPending.value,
)
</script>
<template>
  <section>
    <RouterLink class="back" to="/">‹ <span>К ленте</span></RouterLink>

    <p v-if="eventQuery.isPending.value" class="state">
      <LoadingText label="Загружаем событие" />
    </p>
    <div v-else-if="eventQuery.isError.value" class="state">
      <p>Не удалось загрузить событие.</p>
      <AppButton variant="secondary" @click="eventQuery.refetch()">Повторить</AppButton>
    </div>

    <template v-else-if="event">
      <header class="header">
        <p class="eyebrow">{{ categoryName }} · {{ stateLabel(event.state) }}</p>
        <h1>{{ event.title }}</h1>
        <p class="lead">{{ event.description }}</p>
      </header>

      <div class="layout">
        <AppCard class="facts">
          <p><strong>Начало:</strong> {{ formatStart(event.startsAt) }}</p>
          <p><strong>Окончание:</strong> {{ formatStart(event.endsAt) }}</p>
          <p><strong>Регистрация до:</strong> {{ formatStart(event.registrationClosesAt) }}</p>
          <p><strong>Место:</strong> {{ event.location }}</p>
          <p><strong>Участники:</strong> {{ participantsLine(event) }}</p>
          <p>
            <strong>Организатор:</strong>
            {{ event.organizer.tgLogin ? `@${event.organizer.tgLogin}` : 'без ника' }}
          </p>
        </AppCard>

        <div class="side">
          <div class="actions">
            <AppButton v-if="actions?.canJoin" :disabled="busy" @click="joinMutation.mutate()">
              Присоединиться
            </AppButton>
            <AppButton
              v-else-if="actions?.canLeave"
              variant="secondary"
              :disabled="busy"
              @click="leaveMutation.mutate()"
            >
              Отписаться от события
            </AppButton>
            <AppButton
              v-if="actions?.canEdit"
              variant="secondary"
              :disabled="busy"
              @click="openEdit"
            >
              Изменить
            </AppButton>
            <AppButton
              v-if="actions?.canCancel"
              variant="secondary"
              :disabled="busy"
              @click="confirmingCancel = true"
            >
              Отменить событие
            </AppButton>
          </div>
          <div
            v-if="confirmingCancel && actions?.canCancel"
            class="confirm"
            role="group"
            aria-label="Подтверждение отмены события"
            @keydown.esc="confirmingCancel = false"
          >
            <p>Отменить событие? Участники получат уведомление.</p>
            <div class="actions">
              <AppButton :disabled="busy" @click="cancelMutation.mutate()">Да, отменить</AppButton>
              <AppButton variant="secondary" :disabled="busy" @click="confirmingCancel = false"
                >Нет</AppButton
              >
            </div>
          </div>
          <p v-if="actionError" class="error">{{ actionError }}</p>
          <p
            v-if="
              event.state === 'recruiting' && event.availableSlots === 0 && !event.isParticipant
            "
            class="hint"
          >
            Мест больше нет — вступление недоступно.
          </p>

          <form v-if="editOpen" class="edit" @submit.prevent="editMutation.mutate()">
            <label>
              Название
              <input v-model="editTitle" maxlength="100" required />
            </label>
            <label>
              Описание
              <textarea v-model="editDescription" maxlength="500" rows="4" required />
            </label>
            <div class="actions">
              <AppButton type="submit" :disabled="editMutation.isPending.value"
                >Сохранить</AppButton
              >
              <AppButton type="button" variant="secondary" @click="editOpen = false"
                >Отмена</AppButton
              >
            </div>
            <p v-if="editMessage" class="error">{{ editMessage }}</p>
          </form>
        </div>
      </div>

      <section class="people">
        <h2>Участники</h2>
        <p v-if="participantsQuery.isPending.value" class="state compact">
          <LoadingText label="Загружаем" />
        </p>
        <div v-else-if="participantsQuery.isError.value" class="state compact">
          <p>Не удалось загрузить участников.</p>
          <AppButton variant="secondary" @click="participantsQuery.refetch()">Повторить</AppButton>
        </div>
        <p
          v-else-if="(participantsQuery.data.value?.items.length ?? 0) === 0"
          class="state compact"
        >
          Пока никого нет.
        </p>
        <ul v-else class="list">
          <li v-for="person in participantsQuery.data.value?.items ?? []" :key="person.id">
            <RouterLink :to="`/users/${person.id}`">
              {{ person.tgLogin ? `@${person.tgLogin}` : 'участник' }}
              <span v-if="person.id === event.organizer.id" class="badge">организатор</span>
            </RouterLink>
          </li>
        </ul>
      </section>
    </template>
  </section>
</template>
<style scoped>
.back {
  display: inline-block;
  margin-bottom: 12px;
  font-size: 14px;
}
.header {
  margin-bottom: 24px;
}
.eyebrow {
  font-size: 12px;
  font-weight: 800;
  color: var(--color-text-subtle);
  margin-bottom: 8px;
}
h1 {
  margin-bottom: 10px;
}
.lead {
  color: var(--color-text-muted);
  max-width: 60ch;
}
.layout {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: 24px;
  margin-bottom: 28px;
}
.facts p {
  margin: 8px 0;
  font-size: 14px;
}
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.error {
  margin-top: 10px;
  color: #c62828;
  font-size: 13px;
}
.confirm {
  margin-top: 12px;
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: 12px;
}
.confirm p {
  margin-bottom: 10px;
}
.hint {
  margin-top: 10px;
  font-size: 13px;
  color: var(--color-text-subtle);
}
.edit {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.edit label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 12px;
  font-weight: 800;
}
.edit input,
.edit textarea {
  border: 1px solid var(--color-input-border);
  border-radius: var(--radius-sm);
  padding: 10px 12px;
  font: inherit;
  font-weight: 400;
}
.people h2 {
  margin-bottom: 12px;
  font-size: 18px;
}
.list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.list a {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 14px;
}
.badge {
  font-size: 11px;
  font-weight: 800;
  color: var(--color-text-subtle);
}
.state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 40px 0;
  text-align: center;
  color: var(--color-text-muted);
}
.state.compact {
  align-items: flex-start;
  padding: 16px 0;
  text-align: left;
}
@media (max-width: 700px) {
  h1 {
    font-size: 25px;
  }
  .layout {
    grid-template-columns: 1fr;
  }
}
</style>
