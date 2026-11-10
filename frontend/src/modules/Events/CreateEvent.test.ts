import { mount } from '@vue/test-utils'
import { afterEach, expect, it, vi } from 'vitest'
import { EventSchedule, emptySchedule, toScheduleTimestamp } from '@/modules/EventSchedule'
import CreateEvent from './CreateEvent.vue'
import { createPinia } from 'pinia'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { createMemoryHistory, createRouter } from 'vue-router'

afterEach(() => vi.useRealTimers())

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
    const dates = schedule.findAll('input[type=date]')
    const times = schedule.findAll('input[type=time]')
    for (let index = 0; index < 3; index++) {
      await dates[index]!.setValue('2026-11-11')
      await times[index]!.setValue(['18:00', '21:00', '15:00'][index])
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
