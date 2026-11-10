<script setup lang="ts">
import { computed, ref } from 'vue'
import { localDate } from './dates'
const props = defineProps<{ modelValue: string; min: string; max?: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const open = ref(false)
const month = ref(new Date(`${props.modelValue || props.min}T12:00:00`))
const title = computed(() =>
  month.value.toLocaleDateString('ru-RU', { month: 'long', year: 'numeric' }),
)
const days = computed(() => {
  const first = new Date(month.value.getFullYear(), month.value.getMonth(), 1, 12)
  const offset = (first.getDay() + 6) % 7
  return Array.from({ length: 42 }, (_, index) => {
    const date = new Date(first)
    date.setDate(index - offset + 1)
    const value = localDate(date)
    return {
      value,
      day: date.getDate(),
      outside: date.getMonth() !== first.getMonth(),
      disabled: value < props.min || Boolean(props.max && value > props.max),
    }
  })
})
function shift(delta: number) {
  month.value = new Date(month.value.getFullYear(), month.value.getMonth() + delta, 1, 12)
}
function choose(value: string) {
  emit('update:modelValue', value)
  open.value = false
}
function toggle() {
  if (!open.value) month.value = new Date(`${props.modelValue || props.min}T12:00:00`)
  open.value = !open.value
}
</script>
<template>
  <div class="calendar" @keydown.esc.stop.prevent="open = false">
    <button type="button" class="calendar-toggle" :aria-expanded="open" @click="toggle">
      ▦ Открыть календарь
    </button>
    <div v-if="open" class="calendar-panel" aria-label="Выбор даты">
      <div class="calendar-header">
        <button
          type="button"
          aria-label="Предыдущий месяц"
          :disabled="localDate(new Date(month.getFullYear(), month.getMonth(), 0, 12)) < min"
          @click="shift(-1)"
        >
          ‹
        </button>
        <strong>{{ title }}</strong>
        <button
          type="button"
          aria-label="Следующий месяц"
          :disabled="
            Boolean(
              max && localDate(new Date(month.getFullYear(), month.getMonth() + 1, 1, 12)) > max,
            )
          "
          @click="shift(1)"
        >
          ›
        </button>
      </div>
      <div class="calendar-grid">
        <span v-for="day in ['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Вс']" :key="day">{{ day }}</span>
        <button
          v-for="day in days"
          :key="day.value"
          type="button"
          :aria-label="day.value"
          :aria-pressed="day.value === modelValue"
          :disabled="day.disabled"
          :class="{ outside: day.outside }"
          @click="choose(day.value)"
        >
          {{ day.day }}
        </button>
      </div>
    </div>
  </div>
</template>
<style scoped>
.calendar {
  margin-top: 8px;
  font-size: 12px;
}
.calendar-toggle {
  border: 0;
  background: #dce8ff;
  color: #292657;
  padding: 7px 12px;
  border-radius: 8px;
}
.calendar-panel {
  margin-top: 8px;
  padding: 10px;
  background: #fff;
  border: 1px solid #edcfe2;
  border-radius: 12px;
  max-width: 320px;
}
.calendar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 6px;
  text-transform: capitalize;
}
.calendar-header button,
.calendar-grid button {
  border: 0;
  border-radius: 8px;
  background: #fdf6fa;
  color: #292657;
  padding: 7px 0;
}
.calendar-header button {
  padding: 5px 12px;
  font-size: 20px;
}
.calendar-grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 3px;
  text-align: center;
  margin-top: 10px;
}
.calendar-grid span {
  padding: 6px 0;
  color: #77718d;
}
.calendar-grid button[aria-pressed='true'] {
  background: #f58cba;
  font-weight: 900;
}
.calendar-grid button:disabled,
.calendar-header button:disabled {
  opacity: 0.3;
}
.calendar-grid .outside {
  color: #77718d;
}
</style>
