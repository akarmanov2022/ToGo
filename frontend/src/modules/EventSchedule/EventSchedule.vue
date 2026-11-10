<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, useId, watch } from 'vue'
import CalendarPicker from './CalendarPicker.vue'
import {
  addCalendarMonth,
  emptySchedule,
  earliestEventStart,
  localDate,
  toLocalDateTime,
  toScheduleTimestamp,
  validateSchedule,
  type EventScheduleValue,
  type ScheduleErrors,
  type ScheduleField,
} from './dates'

const props = withDefaults(
  defineProps<{
    modelValue?: EventScheduleValue
    showErrors?: boolean
    now?: () => Date
  }>(),
  { modelValue: emptySchedule, showErrors: false, now: () => new Date() },
)
const emit = defineEmits<{
  'update:modelValue': [value: EventScheduleValue]
  validation: [errors: ScheduleErrors]
}>()
const fields = [
  { key: 'startsAt', label: 'Начало события' },
  { key: 'endsAt', label: 'Окончание события' },
  { key: 'registrationClosesAt', label: 'Закрытие регистрации' },
] as const
const id = useId()
const currentTime = ref(props.now())
const touched = reactive<Partial<Record<ScheduleField, boolean>>>({})
const draft = reactive({
  startsAt: toLocalDateTime(props.modelValue.startsAt),
  endsAt: toLocalDateTime(props.modelValue.endsAt),
  registrationClosesAt: toLocalDateTime(props.modelValue.registrationClosesAt),
})
for (const { key } of fields) {
  watch(
    () => props.modelValue[key],
    (value) => {
      if (value !== toScheduleTimestamp(draft[key])) draft[key] = toLocalDateTime(value)
    },
  )
}
watch(
  () => props.now,
  () => {
    currentTime.value = props.now()
  },
)
let timer: ReturnType<typeof setInterval> | undefined
onMounted(() => {
  timer = setInterval(() => {
    currentTime.value = props.now()
  }, 1000)
})
onUnmounted(() => clearInterval(timer))
const minimum = computed(() => localDate(currentTime.value))
const maximum = computed(() => localDate(addCalendarMonth(currentTime.value)))
const errors = computed(() => validateSchedule(props.modelValue, currentTime.value))
watch(errors, (value) => emit('validation', value), { immediate: true })

function update(field: ScheduleField) {
  touched[field] = true
  currentTime.value = props.now()
  emit('update:modelValue', { ...props.modelValue, [field]: toScheduleTimestamp(draft[field]) })
}

function setTimePart(field: ScheduleField, part: number, event: Event) {
  const parts = (draft[field].time || '00:00').split(':')
  parts[part] = (event.target as HTMLInputElement).value.padStart(2, '0')
  draft[field].time = parts.join(':')
  update(field)
}

function applyDate(field: ScheduleField, value: string) {
  draft[field].date = value
  update(field)
}
</script>

<template>
  <div class="event-schedule">
    <fieldset v-for="field in fields" :key="field.key">
      <legend>{{ field.label }}</legend>
      <div class="schedule-row">
        <div class="schedule-control">
          <label :for="`${id}-${field.key}-date`"
            >Дата
            <input
              :id="`${id}-${field.key}-date`"
              v-model="draft[field.key].date"
              type="date"
              required
              :min="field.key === 'startsAt' ? localDate(earliestEventStart(currentTime)) : minimum"
              :max="field.key === 'startsAt' ? maximum : undefined"
              :aria-invalid="Boolean((showErrors || touched[field.key]) && errors[field.key])"
              :aria-describedby="`${id}-${field.key}-error`"
              @input="update(field.key)"
            />
          </label>
          <CalendarPicker
            :model-value="draft[field.key].date"
            :min="field.key === 'startsAt' ? localDate(earliestEventStart(currentTime)) : minimum"
            :max="field.key === 'startsAt' ? maximum : undefined"
            @update:model-value="applyDate(field.key, $event)"
          />
        </div>
        <div class="schedule-control">
          <label :for="`${id}-${field.key}-time`"
            >Время
            <input
              :id="`${id}-${field.key}-time`"
              v-model="draft[field.key].time"
              type="time"
              required
              step="60"
              :aria-invalid="Boolean((showErrors || touched[field.key]) && errors[field.key])"
              :aria-describedby="`${id}-${field.key}-error`"
              @input="update(field.key)"
            />
          </label>
          <details class="time-picker">
            <summary>Выбрать время</summary>
            <label
              >Часы
              <input
                type="range"
                min="0"
                max="23"
                step="1"
                :value="Number(draft[field.key].time.split(':')[0] || 0)"
                @input="setTimePart(field.key, 0, $event)"
              />
            </label>
            <label
              >Минуты
              <input
                type="range"
                min="0"
                max="59"
                step="1"
                :value="Number(draft[field.key].time.split(':')[1] || 0)"
                @input="setTimePart(field.key, 1, $event)"
              />
            </label>
            <output>{{ draft[field.key].time || '00:00' }}</output>
          </details>
        </div>
      </div>
      <p :id="`${id}-${field.key}-error`" class="schedule-error" aria-live="polite">
        {{ showErrors || touched[field.key] ? errors[field.key] : '' }}
      </p>
    </fieldset>
  </div>
</template>

<style scoped>
.event-schedule {
  display: grid;
  gap: 12px;
}
.event-schedule fieldset {
  border: 1px solid #edcfe2;
  border-radius: 14px;
  padding: 14px;
  min-width: 0;
}
.event-schedule legend {
  font-size: 13px;
  font-weight: 800;
  padding: 0 6px;
}
.time-picker {
  margin-top: 8px;
  padding: 8px;
  border-radius: 10px;
  background: #fdf6fa;
  font-size: 12px;
}
.time-picker summary {
  cursor: pointer;
  color: #47456d;
}
.time-picker input {
  accent-color: #a63872;
  width: 100%;
}
.time-picker output {
  display: block;
  text-align: center;
  font-weight: 900;
  font-size: 18px;
}
.schedule-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
.schedule-control {
  flex: 1;
  min-width: 140px;
}
.schedule-error {
  color: #b42318;
  font-size: 0.875rem;
  margin: 4px 0 0;
}
</style>
