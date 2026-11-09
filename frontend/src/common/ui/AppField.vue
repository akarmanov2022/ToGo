<script setup lang="ts">
defineOptions({ inheritAttrs: false })
defineProps<{ label: string }>()
const model = defineModel<string | number>()
</script>
<template>
  <label class="field">
    <span class="label">{{ label }}</span>
    <input v-model="model" v-bind="$attrs" />
  </label>
</template>
<style scoped>
.field {
  display: flex;
  flex-direction: column;
  gap: 7px;
  flex: 1;
}
.label {
  font-size: 12px;
  font-weight: 800;
}
input {
  width: 100%;
  height: 43px;
  border: 1px solid var(--color-input-border);
  border-radius: var(--radius-sm);
  padding: 0 12px;
  color: #252857;
  background: var(--color-surface);
}
</style>
