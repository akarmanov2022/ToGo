<script setup lang="ts">
import { computed, onUnmounted, reactive, ref, watch } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { useRouter } from 'vue-router'
import { apiRequest, ApiError } from '@/common/api/client'
import type { components } from '@/common/api/schema'
import AppButton from '@/common/ui/AppButton.vue'
import AppField from '@/common/ui/AppField.vue'
import LoadingText from '@/common/ui/LoadingText.vue'
import { useAuthStore } from '@/modules/Auth'
import { PlacePicker, toCoordinateFields, type MeetingPlace } from '@/modules/Map'
import { EventSchedule, emptySchedule } from '@/modules/EventSchedule'
import { creationRestrictions, validateCreation } from './creation'
import { eventCategories } from './categories'
import { demoPlaces } from './demoPlaces'
import EventCard from './EventCard.vue'
import CategorySelect from './CategorySelect.vue'
import { formatStart } from './format'
import { createEvent, fetchCategories } from './api'

const props = withDefaults(defineProps<{ places?: MeetingPlace[] }>(), { places: () => demoPlaces })
const auth = useAuthStore()
const router = useRouter()
const queryClient = useQueryClient()
const draft = reactive({
  title: '',
  description: '',
  icon: '',
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
  queryFn: fetchCategories,
})
const categories = computed<components['schemas']['Category'][]>(
  () => categoryQuery.data.value ?? [...eventCategories],
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
const serverDetails = ref<Record<string, string>>({})
const create = useMutation({
  mutationFn: () =>
    createEvent({
      title: draft.title.trim(),
      description: draft.description.trim(),
      ...(draft.icon.trim() ? { icon: draft.icon.trim() } : {}),
      category: draft.category as components['schemas']['EventCategory'],
      location: draft.place!.address,
      ...toCoordinateFields(draft.place),
      ...draft.schedule,
      minUsers: Number(draft.minUsers),
      maxUsers: Number(draft.maxUsers),
    } satisfies components['schemas']['CreateEventRequest']),
  onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: ['events'] })
    await queryClient.invalidateQueries({ queryKey: ['current-user'] })
    await router.push('/')
  },
  onError: (error) => {
    serverError.value =
      error instanceof ApiError
        ? error.message
        : 'Не удалось создать встречу. Проверьте подключение.'
    serverDetails.value =
      error instanceof ApiError
        ? Object.fromEntries(
            (error.body?.details ?? []).map((detail) => [detail.field, detail.message]),
          )
        : {}
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
  serverDetails.value = {}
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
  <section>
    <RouterLink class="back" to="/">‹ <span>Назад к событиям</span></RouterLink>
    <h1>Новое событие</h1>
    <div class="layout">
      <form class="form" novalidate @submit.prevent="submit" @input="touched = true">
        <AppField
          v-model="draft.title"
          label="Название"
          maxlength="100"
          required
          placeholder="Например, кофе и десерты"
          :aria-invalid="Boolean(touched && errors.title)"
        />
        <p v-if="touched && errors.title" class="error">{{ errors.title }}</p>
        <div class="field">
          <CategorySelect
            :model-value="draft.category"
            :categories="categories"
            @update:model-value="selectCategory"
          />
          <p v-if="categoryQuery.isError.value" class="hint">Не удалось обновить категории.</p>
          <AppButton
            v-if="categoryQuery.isError.value"
            size="sm"
            variant="secondary"
            @click="categoryQuery.refetch()"
            >Повторить</AppButton
          >
        </div>
        <label class="field"
          ><span class="label">Описание</span
          ><textarea
            v-model="draft.description"
            required
            maxlength="500"
            rows="4"
            placeholder="Расскажите о встрече"
            :aria-invalid="Boolean(touched && errors.description)"
          />
        </label>
        <span class="hint">{{ draft.description.length }} / 500</span>
        <p v-if="touched && errors.description" class="error">{{ errors.description }}</p>
        <EventSchedule v-model="draft.schedule" />
        <PlacePicker v-model="draft.place" :places="availablePlaces" />
        <p v-if="touched && errors.place" class="error">{{ errors.place }}</p>
        <AppField
          v-model="draft.icon"
          label="Иконка-эмодзи (необязательно)"
          placeholder="Например: ☕"
          maxlength="8"
        />
        <p v-if="touched && errors.icon" class="error">{{ errors.icon }}</p>
        <div class="row">
          <AppField
            :model-value="draft.minUsers"
            label="Минимум участников"
            inputmode="numeric"
            pattern="[0-9]*"
            required
            maxlength="2"
            :aria-invalid="Boolean(touched && errors.minUsers)"
            @input="digits('minUsers', $event)"
          />
          <AppField
            :model-value="draft.maxUsers"
            label="Максимум участников"
            inputmode="numeric"
            pattern="[0-9]*"
            required
            maxlength="2"
            :aria-invalid="Boolean(touched && errors.maxUsers)"
            @input="digits('maxUsers', $event)"
          />
        </div>
        <p v-if="touched && errors.minUsers" class="error">{{ errors.minUsers }}</p>
        <p v-if="touched && errors.maxUsers" class="error">{{ errors.maxUsers }}</p>
        <div class="actions">
          <AppButton
            type="submit"
            :disabled="create.isPending.value || auth.status !== 'authenticated'"
            ><LoadingText v-if="create.isPending.value" label="Создаём" /><template v-else
              >Создать встречу</template
            ></AppButton
          >
          <AppButton to="/" variant="secondary">Отмена</AppButton>
        </div>
        <p v-if="touched && Object.keys(errors).length" class="error" role="status">
          Пожалуйста, заполните обязательные поля
        </p>
        <p v-for="reason in restrictions" :key="reason" class="error" role="status">{{ reason }}</p>
        <p v-if="participating.isError.value" class="error" role="alert">
          Не удалось проверить ваши встречи.
        </p>
        <AppButton
          v-if="participating.isError.value"
          size="sm"
          variant="secondary"
          @click="participating.refetch()"
          >Повторить</AppButton
        >
        <p v-if="serverError" class="error" role="alert">{{ serverError }}</p>
        <p v-for="(message, field) in serverDetails" :key="field" class="error">{{ message }}</p>
      </form>
      <aside>
        <h2>Так увидят другие</h2>
        <EventCard
          accent
          pink
          :title="draft.title || 'Название события'"
          :meta="`${categories.find((item) => item.code === draft.category)?.name || 'Категория'} · ${draft.schedule.startsAt ? formatStart(draft.schedule.startsAt) : 'Дата и время'}`"
          :place="`${draft.place?.address || 'Место'} · до ${draft.maxUsers || '—'} участников`"
          state="Идёт набор"
          :icon="draft.icon"
        />
        <p class="preview-description">{{ draft.description }}</p>
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
.form,
.field {
  display: flex;
  flex-direction: column;
  gap: 18px;
  min-width: 0;
}
.field {
  gap: 7px;
}
aside h2,
.label {
  font-size: 12px;
  font-weight: 800;
}
aside h2 {
  margin-bottom: 10px;
}
textarea {
  width: 100%;
  border: 1px solid var(--color-input-border);
  border-radius: var(--radius-sm);
  padding: 10px 12px;
  color: var(--color-text);
  background: var(--color-surface);
  resize: vertical;
  font: inherit;
}
.row,
.actions {
  display: flex;
  gap: 15px;
}
.row > * {
  flex: 1;
}
.hint {
  font-size: 12px;
  color: var(--color-text-subtle);
}
.error {
  font-size: 12px;
  color: #b42318;
}
.preview-description {
  margin-top: 12px;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
@media (max-width: 700px) {
  h1 {
    font-size: 25px;
  }
  .back {
    display: inline-block;
    margin-bottom: 10px;
    font-size: 14px;
  }
  .layout {
    display: flex;
    flex-direction: column;
    gap: 24px;
  }
  .actions > * {
    flex: 1;
  }
}
</style>
