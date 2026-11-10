<script setup lang="ts">
import { computed, onUnmounted, reactive, ref, watch } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { useRouter } from 'vue-router'
import { apiRequest, ApiError } from '@/common/api/client'
import type { components } from '@/common/api/schema'
import { useAuthStore } from '@/modules/Auth'
import { PlacePicker, toCoordinateFields, type MeetingPlace } from '@/modules/Map'
import { EventSchedule, emptySchedule } from '@/modules/EventSchedule'
import { creationCategories, creationRestrictions, validateCreation } from './creation'
import { demoPlaces } from './demoPlaces'

const props = withDefaults(defineProps<{ places?: MeetingPlace[] }>(), { places: () => demoPlaces })
const auth = useAuthStore()
const router = useRouter()
const queryClient = useQueryClient()
const draft = reactive({
  title: '',
  description: '',
  category: '',
  minUsers: '1',
  maxUsers: '2',
  place: null as MeetingPlace | null,
  schedule: emptySchedule(),
})
const touched = ref(false)
const now = ref(new Date())
const timer = setInterval(() => {
  now.value = new Date()
}, 1000)
onUnmounted(() => clearInterval(timer))
const availablePlaces = computed(() =>
  props.places.filter((place) => place.category === draft.category),
)
watch(
  () => draft.category,
  () => {
    draft.place = null
  },
)
const categoryQuery = useQuery({
  queryKey: ['categories'],
  queryFn: () => apiRequest<components['schemas']['Category'][]>('/api/v1/categories'),
})
const categories = computed<{ code: string; name: string }[]>(
  () => categoryQuery.data.value ?? [...creationCategories],
)
const participating = useQuery({
  queryKey: computed(() => ['events', 'creation-availability', auth.user?.id]),
  enabled: computed(() => Boolean(auth.user)),
  queryFn: async ({ signal }) => {
    const events: components['schemas']['Event'][] = []
    let offset = 0
    while (true) {
      const page = await apiRequest<components['schemas']['EventPage']>(
        `/api/v1/events?participating=true&limit=100&offset=${offset}`,
        { signal },
      )
      events.push(...page.items)
      offset += page.items.length
      if (offset >= page.total || page.items.length === 0) return events
    }
  },
})
const errors = computed(() => validateCreation(draft, now.value))
const restrictions = computed(() =>
  creationRestrictions(
    participating.data.value ?? [],
    auth.user?.id ?? '',
    draft.schedule.startsAt,
  ),
)
const serverError = ref('')
const create = useMutation({
  mutationFn: () =>
    apiRequest<components['schemas']['Event']>('/api/v1/events', {
      method: 'POST',
      body: {
        title: draft.title.trim(),
        description: draft.description.trim(),
        category: draft.category as components['schemas']['EventCategory'],
        location: draft.place!.address,
        ...toCoordinateFields(draft.place),
        ...draft.schedule,
        minUsers: Number(draft.minUsers),
        maxUsers: Number(draft.maxUsers),
      } satisfies components['schemas']['CreateEventRequest'],
    }),
  onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: ['events'] })
    await router.push('/')
  },
  onError: (error) => {
    serverError.value =
      error instanceof ApiError
        ? error.message
        : 'Не удалось создать встречу. Проверьте подключение.'
  },
})
const canCreate = computed(
  () =>
    auth.status === 'authenticated' &&
    !Object.keys(errors.value).length &&
    !restrictions.value.length &&
    participating.isSuccess.value &&
    !participating.isFetching.value &&
    !create.isPending.value,
)
function submit() {
  touched.value = true
  now.value = new Date()
  serverError.value = ''
  if (canCreate.value) create.mutate()
}
function selectCategory(code: string) {
  draft.category = code
  touched.value = true
}
function digits(field: 'minUsers' | 'maxUsers', event: Event) {
  const input = event.target as HTMLInputElement
  draft[field] = input.value.replace(/\D/g, '')
  input.value = draft[field]
}
</script>
<template>
  <section class="create">
    <RouterLink class="back-link" to="/">‹ <span>Назад к событиям</span></RouterLink>
    <h1>Новое событие</h1>
    <div class="create-layout">
      <form class="event-form" novalidate @submit.prevent="submit" @input="touched = true">
        <label
          >Название<input
            v-model="draft.title"
            maxlength="100"
            required
            placeholder="Например, кофе и десерты"
            :aria-invalid="Boolean(touched && errors.title)"
        /></label>
        <p v-if="touched && errors.title" class="field-error">{{ errors.title }}</p>
        <fieldset>
          <legend>Категория</legend>
          <div class="categories">
            <button
              v-for="item in categories"
              :key="item.code"
              type="button"
              :class="['chip', { selected: draft.category === item.code }]"
              :aria-pressed="draft.category === item.code"
              @click="selectCategory(item.code)"
            >
              {{ item.name }}
            </button>
          </div>
        </fieldset>
        <label
          >Описание<textarea
            v-model="draft.description"
            required
            maxlength="500"
            rows="4"
            placeholder="Расскажите о встрече"
            :aria-invalid="Boolean(touched && errors.description)"
          />
        </label>
        <span class="field-hint">{{ draft.description.length }} / 500</span>
        <p v-if="touched && errors.description" class="field-error">{{ errors.description }}</p>
        <EventSchedule v-model="draft.schedule" />
        <PlacePicker v-model="draft.place" :places="availablePlaces" />
        <p v-if="touched && errors.place" class="field-error">{{ errors.place }}</p>
        <div class="participants-fields">
          <label
            >Минимум участников<input
              :value="draft.minUsers"
              inputmode="numeric"
              pattern="[0-9]*"
              required
              maxlength="2"
              :aria-invalid="Boolean(touched && errors.minUsers)"
              @input="digits('minUsers', $event)"
          /></label>
          <p v-if="touched && errors.minUsers" class="field-error">{{ errors.minUsers }}</p>
          <label
            >Максимум участников<input
              :value="draft.maxUsers"
              inputmode="numeric"
              pattern="[0-9]*"
              required
              maxlength="2"
              :aria-invalid="Boolean(touched && errors.maxUsers)"
              @input="digits('maxUsers', $event)"
          /></label>
          <p v-if="touched && errors.maxUsers" class="field-error">{{ errors.maxUsers }}</p>
        </div>
        <div class="form-actions">
          <button type="submit" class="button" :disabled="!canCreate">
            {{ create.isPending.value ? 'Создаём…' : 'Создать встречу' }}
          </button>
          <RouterLink class="button secondary" to="/">Отмена</RouterLink>
        </div>
        <p v-if="touched && Object.keys(errors).length" class="field-error" role="status">
          Пожалуйста, заполните обязательные поля
        </p>
        <p v-for="reason in restrictions" :key="reason" class="field-error" role="status">
          {{ reason }}
        </p>
        <p v-if="participating.isError.value" class="field-error" role="alert">
          Не удалось проверить ваши встречи.
          <button type="button" @click="participating.refetch()">Повторить</button>
        </p>
        <p v-if="serverError" class="field-error" role="alert">{{ serverError }}</p>
      </form>
      <aside class="preview">
        <h2>Так увидят другие</h2>
        <div class="event-card preview-card">
          <div class="event-icon pink" />
          <div class="event-info">
            <h3>{{ draft.title || 'Название события' }}</h3>
            <p>
              {{ categories.find((item) => item.code === draft.category)?.name || 'Категория' }} ·
              {{
                draft.schedule.startsAt
                  ? new Date(draft.schedule.startsAt).toLocaleString('ru-RU', {
                      dateStyle: 'short',
                      timeStyle: 'short',
                    })
                  : 'Дата и время'
              }}
            </p>
            <p>{{ draft.place?.address || 'Место' }} · до {{ draft.maxUsers || '—' }} участников</p>
            <p>{{ draft.description }}</p>
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
.field-error {
  color: #b42318;
  font-size: 12px;
}
.field-hint {
  color: #77718d;
  font-size: 12px;
  text-align: right;
}
textarea {
  width: 100%;
  resize: vertical;
  border: 1px solid #dcc8ee;
  border-radius: 10px;
  padding: 12px;
  font: inherit;
  color: inherit;
  background: #fff;
}
[aria-invalid='true'] {
  border-color: #b42318;
}
</style>
