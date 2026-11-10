<script setup lang="ts">
import { computed, nextTick, ref, useId, watch } from 'vue'
import { usePopover } from '@/modules/EventSchedule'
import type { Category, EventCategory } from './api'

const props = withDefaults(
  defineProps<{ modelValue: string; categories: readonly Category[]; disabled?: boolean }>(),
  { disabled: false },
)
const emit = defineEmits<{ 'update:modelValue': [value: EventCategory] }>()
const id = useId()
const labelId = `${id}-label`
const open = ref(false)
const active = ref(0)
const root = ref<HTMLElement | null>(null)
const trigger = ref<HTMLButtonElement | null>(null)
const panel = ref<HTMLElement | null>(null)
const { offset } = usePopover(open, root, panel)
const chosen = computed(() => props.categories.find((item) => item.code === props.modelValue))
watch([open, active], async ([expanded]) => {
  if (!expanded) return
  await nextTick()
  panel.value?.querySelector<HTMLElement>('button.active')?.scrollIntoView?.({ block: 'nearest' })
})

function show(activate?: number) {
  if (props.disabled) return
  active.value =
    activate ??
    Math.max(
      0,
      props.categories.findIndex((item) => item.code === props.modelValue),
    )
  open.value = true
}
function close() {
  open.value = false
  trigger.value?.focus()
}
function toggle() {
  if (open.value) close()
  else show()
}
function choose(code: string) {
  emit('update:modelValue', code as EventCategory)
  close()
}
function leave(event: FocusEvent) {
  // relatedTarget = null — тап в мобильном вебвью без передачи фокуса: не закрываем
  if (event.relatedTarget === null) return
  if (!root.value?.contains(event.relatedTarget as Node)) open.value = false
}
function move(delta: number) {
  const count = props.categories.length
  if (!count) return
  active.value = (active.value + delta + count) % count
}
function onKey(event: KeyboardEvent) {
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault()
    const step = event.key === 'ArrowDown' ? 1 : -1
    if (open.value) move(step)
    else if (event.key === 'ArrowDown') show()
    else show(props.categories.length - 1)
  } else if (!open.value) {
    return
  } else if (event.key === 'Home' || event.key === 'End') {
    event.preventDefault()
    active.value = event.key === 'Home' ? 0 : props.categories.length - 1
  } else if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    const item = props.categories[active.value]
    if (item) choose(item.code)
  }
}
</script>
<template>
  <div
    ref="root"
    class="category-field"
    @keydown="onKey"
    @keydown.esc.stop.prevent="close"
    @focusout="leave"
  >
    <span :id="labelId" class="category-label">Категория</span>
    <button
      ref="trigger"
      type="button"
      class="category-toggle"
      :class="{ placeholder: !modelValue }"
      :disabled="disabled"
      role="combobox"
      :aria-labelledby="labelId"
      aria-haspopup="listbox"
      :aria-expanded="open"
      :aria-controls="id"
      :aria-activedescendant="open ? `${id}-option-${active}` : undefined"
      @click="toggle"
    >
      <span>{{ chosen?.name ?? 'Выберите категорию' }}</span>
      <svg
        viewBox="0 0 24 24"
        width="18"
        height="18"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
        stroke-linecap="round"
        stroke-linejoin="round"
        aria-hidden="true"
        :class="{ flipped: open }"
      >
        <path d="m6 9 6 6 6-6" />
      </svg>
    </button>
    <div
      v-if="open"
      :id="id"
      ref="panel"
      class="category-panel"
      role="listbox"
      :aria-labelledby="labelId"
      :style="offset ? { left: `${offset}px` } : undefined"
    >
      <button
        v-for="(category, index) in categories"
        :id="`${id}-option-${index}`"
        :key="category.code"
        type="button"
        role="option"
        :aria-selected="category.code === modelValue"
        :class="{ active: index === active, chosen: category.code === modelValue }"
        @click="choose(category.code)"
        @mousemove="active = index"
      >
        {{ category.name }}
      </button>
    </div>
  </div>
</template>
<style scoped>
.category-field {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 7px;
  min-width: 0;
  font-size: 12px;
}
.category-label {
  font-size: 12px;
  font-weight: 800;
}
.category-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  min-height: 43px;
  border: 1px solid var(--color-input-border);
  background: var(--color-surface);
  color: var(--color-text);
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  font: inherit;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: border-color 0.15s;
}
.category-toggle:hover:not(:disabled) {
  border-color: #b79cd6;
}
.category-toggle:focus-visible {
  outline: 2px solid #a63872;
  outline-offset: 2px;
}
.category-toggle:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.category-toggle.placeholder {
  color: var(--color-text-subtle);
  font-weight: 400;
}
.category-toggle svg {
  flex: none;
  color: var(--color-text-subtle);
  transition: transform 0.15s;
}
.category-toggle svg.flipped {
  transform: rotate(180deg);
}
.category-panel {
  position: absolute;
  top: calc(100% + 2px);
  left: 0;
  z-index: 40;
  display: flex;
  flex-direction: column;
  gap: 2px;
  width: 100%;
  max-height: 264px;
  overflow-y: auto;
  padding: 6px;
  background: var(--color-surface);
  border: 1px solid #edcfe2;
  border-radius: var(--radius-md);
  box-shadow: 0 18px 44px rgba(41, 38, 87, 0.16);
}
.category-panel button {
  border: 0;
  border-radius: 8px;
  background: none;
  color: var(--color-text);
  padding: 10px 12px;
  font: inherit;
  font-size: 13px;
  text-align: left;
  cursor: pointer;
}
.category-panel button.active {
  background: var(--color-bg);
}
.category-panel button.chosen {
  background: var(--color-pink);
  color: var(--color-navy);
  font-weight: 800;
}
</style>
