<template>
  <RouterLink class="floating" to="/create" aria-label="Создать событие"
    >＋ <span>Создать</span></RouterLink
  >
</template>
<style scoped>
.floating {
  display: none;
}
@media (max-width: 700px) {
  .floating {
    position: fixed;
    right: 18px;
    bottom: 76px;
    display: block;
    background: var(--color-pink);
    border-radius: 30px;
    padding: 12px 17px;
    font-size: 13px;
    font-weight: 900;
    box-shadow: 0 4px 12px #3c234b33;
    z-index: 2;
  }
}
</style>
