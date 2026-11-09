<script setup lang="ts">
import AppButton from '@/common/ui/AppButton.vue'
</script>
<template>
  <header class="header">
    <div class="inner">
      <RouterLink class="logo" to="/">To<span>Go</span></RouterLink>
      <nav class="nav" aria-label="Основная навигация">
        <RouterLink to="/">События</RouterLink>
        <RouterLink to="/profile">Профиль</RouterLink>
      </nav>
      <AppButton class="create" to="/create">+ Создать событие</AppButton>
      <RouterLink class="avatar" to="/profile" aria-label="Профиль">С</RouterLink>
    </div>
  </header>
</template>
<style scoped>
.header {
  background: var(--color-navy);
  color: #fff;
}
.inner {
  max-width: var(--container-width);
  margin: auto;
  height: 68px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 32px;
}
.logo {
  font-size: 27px;
  font-weight: 900;
  letter-spacing: -0.06em;
  color: #a4c7ff;
}
.logo span {
  color: #fa78b4;
}
.nav {
  display: flex;
  gap: 26px;
  font-size: 14px;
  font-weight: 700;
}
.nav a:hover,
.nav a.router-link-exact-active {
  text-decoration: underline;
}
.create {
  padding: 11px 20px;
}
.avatar {
  display: none;
}
@media (max-width: 700px) {
  .inner {
    height: 58px;
    padding: 0 18px;
  }
  .logo {
    font-size: 23px;
  }
  .nav,
  .create {
    display: none;
  }
  .avatar {
    display: grid;
    place-items: center;
    width: 34px;
    height: 34px;
    border-radius: 50%;
    background: #e0e5ff;
    color: #24245b;
    font-weight: 900;
  }
}
</style>
