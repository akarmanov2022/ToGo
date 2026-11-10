<script setup lang="ts">
import { computed, ref, useId } from 'vue'
import { localDate } from './dates'
import { usePopover } from './popover'
const props = withDefaults(
  defineProps<{
    modelValue: string
    min: string
    max?: string
    label?: string
    invalid?: boolean
    describedBy?: string
  }>(),
  { label: 'Дата', invalid: false, max: undefined, describedBy: undefined },
)
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const open = ref(false)
const id = useId()
const root = ref<HTMLElement | null>(null)
const trigger = ref<HTMLButtonElement | null>(null)
const panel = ref<HTMLElement | null>(null)
const { offset } = usePopover(open, root, panel)
const display = computed(() =>
  props.modelValue ? props.modelValue.split('-').reverse().join('.') : 'Выбрать дату',
)
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
  if (value && (value < props.min || Boolean(props.max && value > props.max))) return
  emit('update:modelValue', value)
  close()
}
function close() {
  open.value = false
  trigger.value?.focus()
}
function leave(event: FocusEvent) {
  if (event.relatedTarget === null) return
  if (!root.value?.contains(event.relatedTarget as Node)) open.value = false
}
function toggle() {
  if (!open.value) month.value = new Date(`${props.modelValue || props.min}T12:00:00`)
  open.value = !open.value
}
</script>
<template>
  <div ref="root" class="calendar" @keydown.esc.stop.prevent="close" @focusout="leave">
    <button
      ref="trigger"
      type="button"
      class="calendar-toggle"
      :aria-label="`${label}: ${display}`"
      :aria-expanded="open"
      :aria-controls="id"
      :aria-invalid="invalid"
      :aria-describedby="describedBy"
      @click="toggle"
    >
      <span :class="{ placeholder: !modelValue }">{{ display }}</span>
      <svg
        viewBox="0 0 24 24"
        width="18"
        height="18"
        fill="none"
        stroke="currentColor"
        stroke-width="1.7"
        aria-hidden="true"
      >
        <rect x="3" y="5" width="18" height="16" rx="3" />
        <path d="M7 2v6M17 2v6M3 11h18" />
      </svg>
    </button>
    <div
      v-if="open"
      :id="id"
      ref="panel"
      class="calendar-panel"
      role="group"
      :aria-label="label"
      :style="offset ? { left: `${offset}px` } : undefined"
    >
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
      <button type="button" class="calendar-clear" :disabled="!modelValue" @click="choose('')">
        Очистить
      </button>
    </div>
  </div>
</template>
<style scoped>
.calendar {
  position: relative;
  font-size: 12px;
}
.calendar-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  min-height: 43px;
  border: 1px solid #dcc8ee;
  background: #fff;
  color: #292657;
  padding: 10px 12px;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 700;
}
.placeholder {
  color: #77718d;
  font-weight: 400;
}
.calendar-toggle[aria-invalid='true'] {
  border-color: #b42318;
}
.calendar-clear {
  margin-top: 10px;
  border: 0;
  background: #fdf6fa;
  color: #47456d;
  border-radius: 8px;
  padding: 9px 12px;
  font-size: 12px;
}
.calendar-clear:disabled {
  opacity: 0.4;
}
.calendar button:focus-visible {
  outline: 2px solid #a63872;
  outline-offset: 3px;
}
.calendar-panel {
  position: absolute;
  top: calc(100% + 8px);
  left: 0;
  z-index: 40;
  width: 308px;
  max-width: calc(100vw - 24px);
  padding: 10px;
  background: #fff;
  border: 1px solid #edcfe2;
  border-radius: 12px;
  box-shadow: 0 18px 44px rgba(41, 38, 87, 0.16);
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
