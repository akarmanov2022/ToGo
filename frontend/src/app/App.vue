<script setup lang="ts">
import { useRoute } from 'vue-router'
import { watch } from 'vue'
import {
  AuthGate,
  getTelegramInitData,
  requestTelegramWriteAccess,
  useAuthStore,
} from '@/modules/Auth'
import { MOCK_INIT_DATA, shouldUseMocks } from './mocks/mode'
import AppLayout from './AppLayout.vue'
const route = useRoute()
const auth = useAuthStore()
const mockMode = shouldUseMocks()
let permissionRequested = false
watch(
  () => auth.status,
  (status) => {
    if (!mockMode && status === 'authenticated' && !permissionRequested) {
      permissionRequested = true
      void requestTelegramWriteAccess()
    }
  },
  { immediate: true },
)
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
