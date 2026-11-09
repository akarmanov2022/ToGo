<script setup lang="ts">
withDefaults(
  defineProps<{ as?: 'button' | 'span'; active?: boolean; tone?: 'navy' | 'pink' }>(),
  { as: 'button', tone: 'navy' },
)
</script>
<template>
  <component
    :is="as"
    :type="as === 'button' ? 'button' : undefined"
    :class="['chip', tone, { active }]"
  >
    <slot />
  </component>
</template>
<style scoped>
.chip {
  border: 0;
  border-radius: 999px;
  background: var(--color-blue-soft);
  color: #252857;
  font-size: 12px;
  font-weight: 800;
  padding: 9px 16px;
  white-space: nowrap;
}
.navy.active {
  background: var(--color-blue);
  color: #fff;
}
.pink.active {
  background: #f8a4ca;
}
</style>
