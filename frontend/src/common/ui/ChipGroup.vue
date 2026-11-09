<script setup lang="ts">
defineProps<{ scrollable?: boolean }>()
</script>
<template>
  <div :class="['chip-group', { scrollable }]"><slot /></div>
</template>
<style scoped>
.chip-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
@media (max-width: 700px) {
  .scrollable {
    flex-wrap: nowrap;
    overflow-x: auto;
    padding-bottom: 4px;
  }
}
</style>
