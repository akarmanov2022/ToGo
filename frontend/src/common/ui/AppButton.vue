<script setup lang="ts">
withDefaults(
  defineProps<{
    to?: string
    variant?: 'primary' | 'secondary'
    size?: 'md' | 'sm'
    block?: boolean
    type?: 'button' | 'submit' | 'reset'
    disabled?: boolean
  }>(),
  {
    to: undefined,
    variant: 'primary',
    size: 'md',
    type: 'button',
    disabled: false,
  },
)
</script>
<template>
  <RouterLink
    v-if="to"
    :to="to"
    :class="['button', variant, size, { block, disabled }]"
    :aria-disabled="disabled || undefined"
    :tabindex="disabled ? -1 : undefined"
    @click="disabled && $event.preventDefault()"
  >
    <slot />
  </RouterLink>
  <button v-else :type="type" :disabled="disabled" :class="['button', variant, size, { block }]">
    <slot />
  </button>
</template>
<style scoped>
.button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 10px;
  padding: 12px 20px;
  font-size: 13px;
  font-weight: 900;
  text-align: center;
  color: var(--color-text);
  background: var(--color-pink);
}
.secondary {
  background: var(--color-blue-soft);
}
.sm {
  padding: 8px 14px;
  font-size: 12px;
  border-radius: var(--radius-sm);
}
.block {
  width: 100%;
}
.button:disabled,
.button.disabled {
  opacity: 0.72;
  pointer-events: none;
}
.button {
  transition:
    background 0.15s,
    box-shadow 0.15s;
}
.primary:not(:disabled):not(.disabled):hover {
  background: #f37eaf;
  box-shadow: 0 3px 10px #a6387226;
}
.button:focus-visible {
  outline: 2px solid #a63872;
  outline-offset: 3px;
}
</style>
