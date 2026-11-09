<script setup lang="ts">
withDefaults(
  defineProps<{ to?: string; variant?: 'primary' | 'secondary'; block?: boolean }>(),
  { variant: 'primary' },
)
</script>
<template>
  <RouterLink v-if="to" :to="to" :class="['button', variant, { block }]"><slot /></RouterLink>
  <button v-else type="button" :class="['button', variant, { block }]"><slot /></button>
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
.block {
  width: 100%;
}
.button:disabled {
  opacity: 0.72;
}
</style>
