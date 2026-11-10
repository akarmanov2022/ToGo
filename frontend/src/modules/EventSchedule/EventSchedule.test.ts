import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import EventSchedule from './EventSchedule.vue'
import { emptySchedule, toScheduleTimestamp, type EventScheduleValue } from './dates'

const now = () => new Date(2026, 0, 31, 12, 30)
const wrappers: ReturnType<typeof mount>[] = []
afterEach(() => {
  wrappers.forEach((wrapper) => wrapper.unmount())
  wrappers.length = 0
  vi.useRealTimers()
})

function render(modelValue = emptySchedule(), showErrors = false) {
  const wrapper = mount(EventSchedule, {
    props: {
      modelValue,
      now,
      showErrors,
      'onUpdate:modelValue': (value: EventScheduleValue) => {
        void wrapper.setProps({ modelValue: value })
      },
    },
  })
  wrappers.push(wrapper)
  return wrapper
}

describe('EventSchedule', () => {
  it('starts empty and reports all missing values without filling registration', () => {
    const wrapper = render()
    const inputs = wrapper
      .findAll('input')
      .filter((input) => ['date', 'time'].includes(String(input.attributes('type'))))
    expect(inputs.every((input) => input.element.value === '')).toBe(true)
    expect(wrapper.emitted('validation')![0]![0]).toEqual({
      startsAt: 'Укажите дату и время',
      endsAt: 'Укажите дату и время',
      registrationClosesAt: 'Укажите дату и время',
    })
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(wrapper.text()).not.toContain('Укажите дату и время')
  })

  it('restricts past days and clamps the start calendar to the next month', () => {
    const wrapper = render()
    const dates = wrapper.findAll('input[type=date]')
    expect(dates.map((input) => input.attributes('min'))).toEqual([
      '2026-02-01',
      '2026-01-31',
      '2026-01-31',
    ])
    expect(dates[0]!.attributes('max')).toBe('2026-02-28')
    expect(dates[1]!.attributes('max')).toBeUndefined()
    expect(
      wrapper.findAll('input[type=time]').every((input) => input.attributes('step') === '60'),
    ).toBe(true)
  })

  it('preserves partial input and emits RFC3339 only once both parts are present', async () => {
    const wrapper = render()
    const date = wrapper.findAll<HTMLInputElement>('input[type=date]')[0]!
    const time = wrapper.findAll<HTMLInputElement>('input[type=time]')[0]!
    await date.setValue('2026-02-01')
    expect(date.element.value).toBe('2026-02-01')
    expect(wrapper.props('modelValue')!.startsAt).toBe('')
    await time.setValue('18:00')
    expect(wrapper.props('modelValue')).toEqual({
      ...emptySchedule(),
      startsAt: toScheduleTimestamp({ date: '2026-02-01', time: '18:00' }),
    })
    await time.setValue('')
    expect(wrapper.props('modelValue')!.startsAt).toBe('')
    expect(date.element.value).toBe('2026-02-01')
  })

  it('shows Russian field errors after submit and updates from parent values', async () => {
    const wrapper = render(emptySchedule(), true)
    expect(wrapper.findAll('[aria-invalid=true]')).toHaveLength(6)
    expect(wrapper.text()).toContain('Укажите дату и время')
    const timestamp = toScheduleTimestamp({ date: '2026-02-01', time: '18:00' })
    await wrapper.setProps({
      modelValue: { startsAt: timestamp, endsAt: timestamp, registrationClosesAt: timestamp },
    })
    expect(wrapper.text()).toContain('Окончание должно быть позже начала')
    expect(wrapper.findAll<HTMLInputElement>('input[type=time]')[0]!.element.value).toBe('18:00')
  })

  it('refreshes the current day while mounted and stops its clock on unmount', async () => {
    vi.useFakeTimers()
    let instant = new Date(2026, 0, 31, 23, 59, 59)
    const wrapper = render()
    await wrapper.setProps({ now: () => instant })
    instant = new Date(2026, 1, 1, 0, 0, 0)
    await vi.advanceTimersByTimeAsync(1000)
    expect(wrapper.find('input[type=date]').attributes('min')).toBe('2026-02-02')
    wrapper.unmount()
    expect(vi.getTimerCount()).toBe(0)
  })
})
