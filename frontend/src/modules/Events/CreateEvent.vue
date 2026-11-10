<script setup lang="ts">
import { computed, ref } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { useRouter } from 'vue-router'
import { ApiError } from '@/common/api/client'
import AppButton from '@/common/ui/AppButton.vue'
import AppChip from '@/common/ui/AppChip.vue'
import AppField from '@/common/ui/AppField.vue'
import ChipGroup from '@/common/ui/ChipGroup.vue'
import LoadingText from '@/common/ui/LoadingText.vue'
import { LocationPicker, type Coordinates } from '@/modules/Map'
import {
  createEvent,
  fetchCategories,
  type CreateEventBody,
  type EventCategory,
} from './api'
import EventCard from './EventCard.vue'
import {
  combineDateTime,
  hasRequiredGaps,
  toDateInputValue,
  addCalendarMonths,
  validateEventForm,
  type EventFormErrors,
} from './eventForm'
import { formatStart } from './format'
import { stateLabel } from './rules'

const router = useRouter()
const queryClient = useQueryClient()
const categoriesQuery = useQuery({ queryKey: ['categories'], queryFn: fetchCategories })

const title = ref('')
const description = ref('')
const category = ref<EventCategory | ''>('')
const startDate = ref('')
const startTime = ref('')
const endDate = ref('')
const endTime = ref('')
const closeDate = ref('')
const closeTime = ref('')
const place = ref('')
const icon = ref('')
const minUsers = ref(1)
const maxUsers = ref(4)
const coordinates = ref<Coordinates | null>(null)

const fieldErrors = ref<EventFormErrors>({})
const requiredMessage = ref(false)
const serverMessage = ref('')
const serverDetails = ref<Record<string, string>>({})
const submitting = ref(false)

const now = new Date()
const minDate = toDateInputValue(now)
const maxDate = toDateInputValue(addCalendarMonths(now, 1))

const previewMeta = computed(() => {
  const name =
    categoriesQuery.data.value?.find((item) => item.code === category.value)?.name ??
    (category.value || 'Категория')
  const starts = combineDateTime(startDate.value, startTime.value)
  const when = starts ? formatStart(starts) : 'Дата и время'
  return `${name} · ${when} · ${stateLabel('recruiting')}`
})

const previewPlace = computed(
  () => `${place.value || 'Место'} · до ${Number(maxUsers.value) || 0} участников`,
)

function digitsOnly(value: string | number): number {
  const digits = String(value).replace(/\D/g, '')
  return digits === '' ? Number.NaN : Number(digits)
}

function buildValues() {
  return {
    title: title.value,
    description: description.value,
    location: place.value,
    category: category.value,
    startsAt: combineDateTime(startDate.value, startTime.value),
    endsAt: combineDateTime(endDate.value, endTime.value),
    registrationClosesAt: combineDateTime(closeDate.value, closeTime.value),
    minUsers: digitsOnly(minUsers.value),
    maxUsers: digitsOnly(maxUsers.value),
    icon: icon.value,
  }
}

async function onSubmit() {
  if (submitting.value) return
  serverMessage.value = ''
  serverDetails.value = {}
  const values = buildValues()
  const errors = validateEventForm(values, new Date())
  fieldErrors.value = errors
  requiredMessage.value = hasRequiredGaps(errors)
  if (Object.keys(errors).length > 0) return

  const body: CreateEventBody = {
    title: values.title.trim(),
    description: values.description.trim(),
    location: values.location.trim(),
    startsAt: values.startsAt,
    endsAt: values.endsAt,
    registrationClosesAt: values.registrationClosesAt,
    minUsers: values.minUsers,
    maxUsers: values.maxUsers,
    category: values.category as EventCategory,
  }
  if (icon.value.trim()) body.icon = icon.value.trim()
  if (coordinates.value) {
    body.latitude = coordinates.value.latitude
    body.longitude = coordinates.value.longitude
  }

  submitting.value = true
  try {
    const created = await createEvent(body)
    await queryClient.invalidateQueries({ queryKey: ['events'] })
    await router.push(`/events/${created.id}`)
  } catch (error) {
    if (error instanceof ApiError) {
      serverMessage.value = error.message
      serverDetails.value = error.details
      if (error.code === 'SCHEDULE_CONFLICT') {
        serverMessage.value =
          error.message || 'У вас уже есть встреча ближе чем за три часа к этому времени'
      }
    } else {
      serverMessage.value = 'Не удалось создать событие'
    }
  } finally {
    submitting.value = false
  }
}
</script>
<template>
  <section>
    <RouterLink class="back" to="/">‹ <span>Назад к событиям</span></RouterLink>
    <h1>Новое событие</h1>
    <div class="layout">
      <form class="form" @submit.prevent="onSubmit">
        <AppField
          v-model="title"
          label="Название"
          placeholder="Например, кофе и десерты"
          maxlength="100"
          required
        />
        <p v-if="fieldErrors.title || serverDetails.title" class="error">
          {{ fieldErrors.title || serverDetails.title }}
        </p>

        <label class="field">
          <span class="label">Описание</span>
          <textarea
            v-model="description"
            maxlength="500"
            rows="4"
            placeholder="Коротко расскажите, что будет на встрече"
            required
          />
          <span class="hint">{{ description.length }} / 500</span>
          <span v-if="fieldErrors.description || serverDetails.description" class="error">
            {{ fieldErrors.description || serverDetails.description }}
          </span>
        </label>

        <div class="field" role="group" aria-labelledby="create-category-label">
          <span id="create-category-label" class="label">Категория</span>
          <p v-if="categoriesQuery.isPending.value" class="hint">
            <LoadingText label="Загружаем категории" />
          </p>
          <p v-else-if="categoriesQuery.isError.value" class="error categories-error">
            <span>Не удалось загрузить категории.</span>
            <button type="button" class="link" @click="categoriesQuery.refetch()">Повторить</button>
          </p>
          <ChipGroup v-else>
            <AppChip
              v-for="item in categoriesQuery.data.value ?? []"
              :key="item.code"
              tone="pink"
              :active="category === item.code"
              @click="category = item.code"
            >
              {{ item.name }}
            </AppChip>
          </ChipGroup>
          <p
            v-if="
              categoriesQuery.isSuccess.value && (fieldErrors.category || serverDetails.category)
            "
            class="error"
          >
            {{ fieldErrors.category || serverDetails.category }}
          </p>
        </div>

        <div class="row">
          <AppField v-model="startDate" label="Дата начала" type="date" :min="minDate" :max="maxDate" />
          <AppField v-model="startTime" label="Время начала" type="time" />
        </div>
        <p class="hint">Время в формате 24 часа</p>
        <p v-if="fieldErrors.startsAt || serverDetails.startsAt" class="error">
          {{ fieldErrors.startsAt || serverDetails.startsAt }}
        </p>

        <div class="row">
          <AppField v-model="endDate" label="Дата окончания" type="date" :min="minDate" />
          <AppField v-model="endTime" label="Время окончания" type="time" />
        </div>
        <p v-if="fieldErrors.endsAt || serverDetails.endsAt" class="error">
          {{ fieldErrors.endsAt || serverDetails.endsAt }}
        </p>

        <div class="row">
          <AppField
            v-model="closeDate"
            label="Закрытие регистрации"
            type="date"
            :min="minDate"
            :max="maxDate"
          />
          <AppField v-model="closeTime" label="Время закрытия" type="time" />
        </div>
        <p v-if="fieldErrors.registrationClosesAt || serverDetails.registrationClosesAt" class="error">
          {{ fieldErrors.registrationClosesAt || serverDetails.registrationClosesAt }}
        </p>

        <AppField v-model="place" label="Место" placeholder="Например, Невский проспект, 28" />
        <p v-if="fieldErrors.location || serverDetails.location" class="error">
          {{ fieldErrors.location || serverDetails.location }}
        </p>
        <LocationPicker v-model="coordinates" :address="place" />

        <AppField
          v-model="icon"
          label="Иконка-эмодзи (необязательно)"
          placeholder="Например: ☕"
          maxlength="8"
        />
        <p v-if="fieldErrors.icon || serverDetails.icon" class="error">
          {{ fieldErrors.icon || serverDetails.icon }}
        </p>

        <div class="row">
          <AppField
            v-model="minUsers"
            label="Минимум участников"
            type="number"
            min="1"
            max="50"
            step="1"
            inputmode="numeric"
          />
          <AppField
            v-model="maxUsers"
            label="Максимум участников"
            type="number"
            min="1"
            max="50"
            step="1"
            inputmode="numeric"
          />
        </div>
        <p v-if="fieldErrors.minUsers || serverDetails.minUsers" class="error">
          {{ fieldErrors.minUsers || serverDetails.minUsers }}
        </p>
        <p v-if="fieldErrors.maxUsers || serverDetails.maxUsers" class="error">
          {{ fieldErrors.maxUsers || serverDetails.maxUsers }}
        </p>

        <div class="actions">
          <AppButton type="submit" :disabled="submitting">
            <LoadingText v-if="submitting" label="Создаём" />
            <template v-else>Создать встречу</template>
          </AppButton>
          <AppButton to="/" variant="secondary">Отмена</AppButton>
        </div>
        <p v-if="requiredMessage" class="error required">
          Пожалуйста, заполните обязательные поля
        </p>
        <p v-if="serverMessage" class="error">{{ serverMessage }}</p>
      </form>
      <aside>
        <h2>Так увидят другие</h2>
        <EventCard
          accent
          pink
          :title="title || 'Название события'"
          :meta="previewMeta"
          :place="previewPlace"
          :state="stateLabel('recruiting')"
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
aside h2,
.label {
  font-size: 12px;
  font-weight: 800;
}
aside h2 {
  margin-bottom: 10px;
}
.field {
  display: flex;
  flex-direction: column;
  gap: 7px;
  min-width: 0;
}
textarea {
  width: 100%;
  border: 1px solid var(--color-input-border);
  border-radius: var(--radius-sm);
  padding: 10px 12px;
  color: #252857;
  background: var(--color-surface);
  resize: vertical;
  font: inherit;
}
.row {
  display: flex;
  gap: 15px;
}
.row > * {
  flex: 1;
}
.actions {
  display: flex;
  gap: 10px;
}
.hint {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-subtle);
}
.error {
  margin: 0;
  font-size: 12px;
  color: #c62828;
}
.categories-error {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 0.35em;
}
.required {
  font-weight: 700;
}
.link {
  border: 0;
  background: none;
  color: inherit;
  text-decoration: underline;
  cursor: pointer;
  font: inherit;
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
