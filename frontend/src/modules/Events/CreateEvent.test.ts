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
    const fields = schedule.findAll('fieldset')
    for (let index = 0; index < 3; index++) {
      const field = fields[index]!
      await field.get('.calendar-toggle').trigger('click')
      await field.get('button[aria-label="2026-11-11"]').trigger('click')
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
