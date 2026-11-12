<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, useId, watch } from 'vue'
import CalendarPicker from './CalendarPicker.vue'
import TimePicker from './TimePicker.vue'
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
const startDay = computed(() => draft.startsAt.date)
const errors = computed(() => validateSchedule(props.modelValue, currentTime.value))
watch(errors, (value) => emit('validation', value), { immediate: true })

function calendarBounds(field: ScheduleField): { min: string; max?: string } {
  if (field === 'startsAt')
    return { min: localDate(earliestEventStart(currentTime.value)), max: maximum.value }
  if (field === 'endsAt')
    return { min: startDay.value || localDate(earliestEventStart(currentTime.value)) }
  return { min: minimum.value, max: startDay.value || undefined }
}

function update(field: ScheduleField) {
  currentTime.value = props.now()
  emit('update:modelValue', { ...props.modelValue, [field]: toScheduleTimestamp(draft[field]) })
}

function applyTime(field: ScheduleField, value: string) {
  draft[field].time = value
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
          <span class="control-label">Дата</span>
          <CalendarPicker
            :model-value="draft[field.key].date"
            :label="`${field.label}: дата`"
            :invalid="Boolean(showErrors && errors[field.key])"
            :described-by="`${id}-${field.key}-error`"
            v-bind="calendarBounds(field.key)"
            @update:model-value="applyDate(field.key, $event)"
          />
        </div>
        <div class="schedule-control">
          <span class="control-label">Время</span>
          <TimePicker
            :model-value="draft[field.key].time"
            :label="`${field.label}: время`"
            :invalid="Boolean(showErrors && errors[field.key])"
            :described-by="`${id}-${field.key}-error`"
            @update:model-value="applyTime(field.key, $event)"
          />
        </div>
      </div>
      <p :id="`${id}-${field.key}-error`" class="schedule-error" aria-live="polite">
        {{ showErrors ? errors[field.key] : '' }}
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
.schedule-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
.schedule-control {
  flex: 1;
  min-width: 140px;
}
.control-label {
  display: block;
  margin-bottom: 7px;
  font-size: 12px;
  font-weight: 800;
}
.schedule-error {
  color: #b42318;
  font-size: 0.875rem;
  margin: 4px 0 0;
}
</style>
