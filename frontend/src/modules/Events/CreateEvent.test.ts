import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, expect, it, vi } from 'vitest'
import { EventSchedule, emptySchedule, toScheduleTimestamp } from '@/modules/EventSchedule'
import CreateEvent from './CreateEvent.vue'
import { createPinia } from 'pinia'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { createMemoryHistory, createRouter } from 'vue-router'
import { useAuthStore } from '@/modules/Auth'

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

it('binds all three schedule values independently in the creation form', async () => {
  vi.useFakeTimers()
  vi.setSystemTime(new Date(2026, 10, 10, 12))
  const wrapper = mount(CreateEvent, {
    global: {
      plugins: [
        createPinia(),
        [VueQueryPlugin, { queryClient: new QueryClient() }],
        createRouter({
          history: createMemoryHistory(),
          routes: [{ path: '/', component: { template: '<div />' } }],
        }),
      ],
      stubs: { PlacePicker: true, RouterLink: true },
    },
  })
  try {
    const schedule = wrapper.findComponent(EventSchedule)
    expect(schedule.props('modelValue')).toEqual(emptySchedule())
    const fields = schedule.findAll('fieldset')
    for (let index = 0; index < 3; index++) {
      const field = fields[index]!
      await field.get('.calendar-toggle').trigger('click')
      await field.get('button[aria-label="2026-11-11"]').trigger('click')
      expect(wrapper.findAll('.schedule-error').every((error) => !error.text())).toBe(true)
      expect(wrapper.findAll('.form > .error')).toHaveLength(0)
      await field.get('.time-trigger').trigger('click')
      const timeParts = field.findAll('.time-number')
      await timeParts[0]!.setValue(String([18, 21, 15][index]))
      await timeParts[1]!.setValue('00')
      await field.get('.time-apply').trigger('click')
    }
    const timestamp = (time: string) => toScheduleTimestamp({ date: '2026-11-11', time })
    expect(schedule.props('modelValue')).toEqual({
      startsAt: timestamp('18:00'),
      endsAt: timestamp('21:00'),
      registrationClosesAt: timestamp('15:00'),
    })
    expect(schedule.emitted('validation')!.at(-1)).toEqual([{}])
  } finally {
    wrapper.unmount()
  }
})

it('при нажатии на пустую форму показывает обязательные поля и не отправляет POST', async () => {
  vi.useFakeTimers()
  const fetch = vi
    .fn()
    .mockImplementation((path: string) =>
      Promise.resolve(
        new Response(
          JSON.stringify(
            path.includes('/categories') ? [] : { items: [], total: 0, limit: 100, offset: 0 },
          ),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        ),
      ),
    )
  vi.stubGlobal('fetch', fetch)
  const pinia = createPinia()
  const auth = useAuthStore(pinia)
  auth.status = 'authenticated'
  auth.user = {
    id: 'anna',
    tgId: 1,
    tgLogin: 'anna',
    name: 'Анна',
    photoUrl: null,
    city: null,
    categories: [],
    createdEventsCount: 0,
    visitedEventsCount: 0,
  }
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = mount(CreateEvent, {
    global: {
      plugins: [
        pinia,
        [VueQueryPlugin, { queryClient }],
        createRouter({
          history: createMemoryHistory(),
          routes: [{ path: '/', component: { template: '<div />' } }],
        }),
      ],
      stubs: { PlacePicker: true, RouterLink: true },
    },
  })
  try {
    await flushPromises()
    const submit = wrapper.get('button[type="submit"]')
    expect((submit.element as HTMLButtonElement).disabled).toBe(false)
    await wrapper.get('input').setValue('Название!')
    expect(wrapper.text()).not.toContain('Название: буквы')
    await submit.trigger('click')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('Пожалуйста, заполните обязательные поля')
    expect(wrapper.text()).toContain('Название: буквы')
    expect(wrapper.findComponent(EventSchedule).props('showErrors')).toBe(true)
    expect(fetch.mock.calls.some(([, options]) => options?.method === 'POST')).toBe(false)
  } finally {
    wrapper.unmount()
    queryClient.clear()
  }
})
