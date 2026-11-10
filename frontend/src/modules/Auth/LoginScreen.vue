<script setup lang="ts">
import { useAuthStore } from './session'
const auth = useAuthStore()
</script>

<template>
  <main class="container">
    <h1>Добро пожаловать, {{ auth.user?.name }}</h1>
    <p>Вход выполнен автоматически.</p>
    <RouterLink class="button" to="/">К событиям</RouterLink>
  </main>
</template>
