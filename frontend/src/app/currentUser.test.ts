import { defineComponent } from 'vue'
import { enableAutoUnmount, flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { afterEach, expect, it, vi } from 'vitest'
import { currentUserKey, useAuthStore, type CurrentUser } from '@/modules/Auth'
import { Profile } from '@/modules/Profile'
import AppHeader from './ui/AppHeader.vue'

const { apiRequest } = vi.hoisted(() => ({ apiRequest: vi.fn() }))
vi.mock('@/common/api/client', () => ({ apiRequest, ApiError: class extends Error {} }))
enableAutoUnmount(afterEach)
afterEach(() => vi.clearAllMocks())

it('шапка и профиль разделяют один GET me, а PUT обновляет оба без изменения снимка сессии', async () => {
  let serverProfile: CurrentUser = {
    id: 'anna',
    tgId: 1,
    tgLogin: 'anna',
    name: 'Анна',
    photoUrl: null,
    city: null,
    categories: ['CINEMA'],
    createdEventsCount: 0,
    visitedEventsCount: 0,
  }
  apiRequest.mockImplementation(
    (path: string, options?: { method?: string; body?: Partial<CurrentUser> }) => {
      if (path === '/api/v1/users/me') {
        if (options?.method === 'PUT') serverProfile = { ...serverProfile, ...options.body }
        return Promise.resolve({ ...serverProfile })
      }
      if (path === '/api/v1/categories') return Promise.resolve([])
      return Promise.resolve({ items: [], total: 0, limit: 100, offset: 0 })
    },
  )
  const pinia = createPinia()
  const auth = useAuthStore(pinia)
  auth.user = { ...serverProfile }
  auth.status = 'authenticated'
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = mount(
    defineComponent({
      components: { AppHeader, Profile },
      template: '<AppHeader :show-create="false" /><Profile />',
    }),
    {
      global: {
        plugins: [pinia, [VueQueryPlugin, { queryClient }]],
        stubs: { RouterLink: { template: '<a><slot /></a>' } },
      },
    },
  )
  try {
    await flushPromises()
    expect(
      apiRequest.mock.calls.filter(
        ([path, options]) => path === '/api/v1/users/me' && !options?.method,
      ),
    ).toHaveLength(1)
    expect(wrapper.get('header .avatar').text()).toBe('А')
    await wrapper.get('.summary button').trigger('click')
    await wrapper.get('.summary input').setValue('Борис')
    await wrapper.get('.summary form').trigger('submit')
    await flushPromises()
    expect(wrapper.get('header .avatar').text()).toBe('Б')
    expect(wrapper.get('.summary h2').text()).toBe('Борис')
    expect(queryClient.getQueryData<CurrentUser>(currentUserKey('anna'))?.name).toBe('Борис')
    expect(auth.user.name).toBe('Анна')
  } finally {
    wrapper.unmount()
    queryClient.clear()
  }
})
