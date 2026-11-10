<script setup lang="ts">
import { computed, reactive, ref, useId } from 'vue'
import { usePopover } from './popover'

const props = withDefaults(
  defineProps<{
    modelValue: string
    label?: string
    invalid?: boolean
    describedBy?: string
  }>(),
  { label: 'Время', invalid: false, describedBy: undefined },
)
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const id = useId()
const root = ref<HTMLElement | null>(null)
const trigger = ref<HTMLButtonElement | null>(null)
const panel = ref<HTMLElement | null>(null)
const open = ref(false)
const draft = reactive({ hours: '12', minutes: '00' })
const { offset } = usePopover(open, root, panel)
const fields = [
  { key: 'hours', label: 'Часы', max: 23, step: 1 },
  { key: 'minutes', label: 'Минуты', max: 55, step: 5 },
] as const
type Part = (typeof fields)[number]['key']
const pad = (value: string | number) => String(Number(value)).padStart(2, '0')
const steppedMinutes = () => Math.floor(Number(draft.minutes) / 5) * 5
const preview = computed(() => `${pad(draft.hours)}:${pad(steppedMinutes())}`)

function adjust(part: Part, delta: number, max: number) {
  const step = part === 'minutes' ? 5 : 1
  const current = part === 'minutes' ? steppedMinutes() : Number(draft[part])
  draft[part] = pad((current + delta * step + max + step) % (max + step))
}
function normalize(part: Part) {
  draft[part] = pad(part === 'minutes' ? steppedMinutes() : draft[part])
}
function edit(part: Part, max: number, event: Event) {
  const input = event.target as HTMLInputElement
  const digits = input.value.replace(/\D/g, '').slice(0, 2)
  draft[part] = digits ? String(Math.min(Number(digits), max)) : ''
  input.value = draft[part]
}

function toggle() {
  if (!open.value) {
    // Значение предпросмотра не заполняет обязательное поле до явного выбора.
    const [hour, minute] = (props.modelValue || '12:00').split(':')
    draft.hours = hour!
    draft.minutes = pad(Math.floor(Number(minute) / 5) * 5)
  }
  open.value = !open.value
}
function close() {
  open.value = false
  trigger.value?.focus()
}
function choose() {
  emit('update:modelValue', preview.value)
  close()
}
function clear() {
  emit('update:modelValue', '')
  close()
}
function leave(event: FocusEvent) {
  if (event.relatedTarget === null) return
  if (!root.value?.contains(event.relatedTarget as Node)) open.value = false
}
</script>
<template>
  <div ref="root" class="time-picker" @keydown.esc.stop.prevent="close" @focusout="leave">
    <button
      ref="trigger"
      type="button"
      class="time-trigger"
      :aria-label="`${label}: ${modelValue || 'Выбрать время'}`"
      :aria-expanded="open"
      :aria-controls="id"
      :aria-invalid="invalid"
      :aria-describedby="describedBy"
      @click="toggle"
    >
      <span :class="{ placeholder: !modelValue }">{{ modelValue || 'Выбрать время' }}</span>
      <svg
        viewBox="0 0 24 24"
        width="18"
        height="18"
        fill="none"
        stroke="currentColor"
        stroke-width="1.7"
        aria-hidden="true"
      >
        <circle cx="12" cy="12" r="9" />
        <path d="M12 6v6l4 2" />
      </svg>
    </button>
    <div
      v-if="open"
      :id="id"
      ref="panel"
      class="time-panel"
      role="group"
      :aria-label="label"
      :style="offset ? { left: `${offset}px` } : undefined"
    >
      <div class="time-columns">
        <div v-for="field in fields" :key="field.key" class="time-column">
          <label :for="`${id}-${field.key}`"
            >{{ field.label }}<span v-if="field.step === 5"></span
          ></label>
          <div class="time-stepper">
            <button
              type="button"
              class="time-step"
              :aria-label="`Увеличить ${field.label.toLowerCase()}`"
              @click="adjust(field.key, 1, field.max)"
            >
              <svg
                viewBox="0 0 20 20"
                width="20"
                height="20"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                aria-hidden="true"
              >
                <path d="m5 12 5-5 5 5" />
              </svg>
            </button>
            <input
              :id="`${id}-${field.key}`"
              :value="draft[field.key]"
              class="time-number"
              type="text"
              inputmode="numeric"
              maxlength="2"
              role="spinbutton"
              :aria-valuemin="0"
              :aria-valuemax="field.max"
              :aria-valuenow="Number(draft[field.key])"
              @input="edit(field.key, field.max, $event)"
              @blur="normalize(field.key)"
              @keydown.up.prevent="adjust(field.key, 1, field.max)"
              @keydown.down.prevent="adjust(field.key, -1, field.max)"
            />
            <button
              type="button"
              class="time-step"
              :aria-label="`Уменьшить ${field.label.toLowerCase()}`"
              @click="adjust(field.key, -1, field.max)"
            >
              <svg
                viewBox="0 0 20 20"
                width="20"
                height="20"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                aria-hidden="true"
              >
                <path d="m5 8 5 5 5-5" />
              </svg>
            </button>
          </div>
        </div>
        <span class="time-colon" aria-hidden="true">:</span>
      </div>
      <div class="time-actions">
        <button type="button" class="time-apply" @click="choose">Выбрать</button>
        <button type="button" class="time-clear" :disabled="!modelValue" @click="clear">
          Очистить
        </button>
      </div>
    </div>
  </div>
</template>
<style scoped>
.time-picker {
  position: relative;
  min-width: 0;
}
.time-trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  min-height: 43px;
  padding: 10px 12px;
  border: 1px solid #dcc8ee;
  border-radius: 10px;
  background: #fff;
  color: #292657;
  font-size: 13px;
  font-weight: 700;
}
.placeholder {
  color: #77718d;
  font-weight: 400;
}
.time-trigger[aria-invalid='true'] {
  border-color: #b42318;
}
.time-panel {
  position: absolute;
  top: calc(100% + 8px);
  left: 0;
  z-index: 40;
  width: 308px;
  max-width: calc(100vw - 24px);
  padding: 14px;
  border: 1px solid #edcfe2;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 18px 44px rgba(41, 38, 87, 0.16);
}
.time-columns {
  position: relative;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
  margin-bottom: 16px;
}
.time-column {
  min-width: 0;
  text-align: center;
}
.time-column label {
  display: block;
  margin-bottom: 8px;
  color: #77718d;
  font-size: 11px;
  font-weight: 800;
}
.time-stepper {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  border: 1px solid #e5d8f0;
  border-radius: 14px;
  background: linear-gradient(180deg, #fdf6fa, #f0f4ff);
  overflow: hidden;
}
.time-step {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 34px;
  padding: 4px;
  border: 0;
  background: transparent;
  color: #77718d;
  transition:
    background 0.15s,
    color 0.15s;
}
.time-step:hover {
  background: #edddf1;
  color: #a63872;
}
.time-number {
  width: 100%;
  min-width: 0;
  border: 0;
  border-radius: 0;
  padding: 6px 0;
  background: transparent;
  text-align: center;
  font-size: 30px;
  font-weight: 900;
  font-variant-numeric: tabular-nums;
  color: #292657;
}
.time-number:focus {
  background: #fff;
}
.time-colon {
  position: absolute;
  left: 50%;
  top: calc(50% + 10px);
  transform: translate(-50%, -50%);
  color: #a63872;
  font-size: 28px;
  font-weight: 900;
}
.time-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.time-actions button {
  border: 0;
  border-radius: 8px;
  padding: 9px 12px;
  font-size: 12px;
  font-weight: 800;
}
.time-apply {
  background: #f58cba;
  color: #292657;
}
.time-clear {
  background: #fdf6fa;
  color: #47456d;
}
button:disabled {
  opacity: 0.4;
}
button:focus-visible,
input:focus-visible {
  outline: 2px solid #a63872;
  outline-offset: 3px;
}
</style>
