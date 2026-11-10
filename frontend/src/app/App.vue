<script setup lang="ts">
import { useRoute } from 'vue-router'
import { AuthGate, getTelegramInitData, useAuthStore } from '@/modules/Auth'
import { MOCK_INIT_DATA, shouldUseMocks } from './mocks/mode'
import AppLayout from './AppLayout.vue'
const route = useRoute()
const auth = useAuthStore()
const mockMode = shouldUseMocks()
function initialize() {
  return auth.initialize(mockMode ? MOCK_INIT_DATA : getTelegramInitData())
}
if (!mockMode) void initialize()
</script>
<template>
  <AuthGate :mock-mode="mockMode" @retry="initialize">
    <RouterView v-if="route.meta.standalone" /><AppLayout v-else><RouterView /></AppLayout>
  </AuthGate>
</template>
