<script setup lang="ts">
defineProps<{ label: string }>()
</script>
<template>
  <span class="loading-text">
    {{ label
    }}<span class="dots" aria-hidden="true"><span>.</span><span>.</span><span>.</span></span>
  </span>
</template>
<style scoped>
.dots span {
  opacity: 0;
  animation: dot-blink 1.2s infinite;
}
.dots span:nth-child(2) {
  animation-delay: 0.2s;
}
.dots span:nth-child(3) {
  animation-delay: 0.4s;
}
@keyframes dot-blink {
  0%,
  20% {
    opacity: 0;
  }
  40%,
  100% {
    opacity: 1;
  }
}
@media (prefers-reduced-motion: reduce) {
  .dots span {
    animation: none;
    opacity: 1;
  }
}
</style>
