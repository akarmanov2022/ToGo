import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import EventSchedule from './EventSchedule.vue'
import CalendarPicker from './CalendarPicker.vue'
import TimePicker from './TimePicker.vue'
import { nextTick } from 'vue'
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
    expect(wrapper.find('input[type=date], input[type=time]').exists()).toBe(false)
    expect(
      wrapper
        .findAllComponents(CalendarPicker)
        .every((picker) => picker.props('modelValue') === ''),
    ).toBe(true)
    expect(
      wrapper.findAllComponents(TimePicker).every((picker) => picker.props('modelValue') === ''),
    ).toBe(true)
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
    const dates = wrapper.findAllComponents(CalendarPicker)
    expect(dates.map((picker) => picker.props('min'))).toEqual([
      '2026-02-01',
      '2026-02-01',
      '2026-01-31',
    ])
    expect(dates[0]!.props('max')).toBe('2026-02-28')
    expect(dates[1]!.props('max')).toBeUndefined()
    expect(wrapper.findAllComponents(TimePicker)).toHaveLength(3)
  })

  it('grays out calendar days that fail validation once the start date is chosen', async () => {
    const wrapper = render()
    const [start, end, registration] = wrapper.findAllComponents(CalendarPicker)
    start.vm.$emit('update:modelValue', '2026-02-05')
    await nextTick()
    expect(end.props('min')).toBe('2026-02-05')
    expect(registration.props('max')).toBe('2026-02-05')
  })

  it('preserves partial input and emits RFC3339 only once both parts are present', async () => {
    const wrapper = render()
    const date = wrapper.findAllComponents(CalendarPicker)[0]!
    const time = wrapper.findAllComponents(TimePicker)[0]!
    date.vm.$emit('update:modelValue', '2026-02-01')
    await nextTick()
    expect(date.props('modelValue')).toBe('2026-02-01')
    expect(wrapper.props('modelValue')!.startsAt).toBe('')
    time.vm.$emit('update:modelValue', '18:00')
    await nextTick()
    expect(wrapper.props('modelValue')).toEqual({
      ...emptySchedule(),
      startsAt: toScheduleTimestamp({ date: '2026-02-01', time: '18:00' }),
    })
    time.vm.$emit('update:modelValue', '')
    await nextTick()
    expect(wrapper.props('modelValue')!.startsAt).toBe('')
    expect(date.props('modelValue')).toBe('2026-02-01')
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
    expect(wrapper.findAllComponents(TimePicker)[0]!.props('modelValue')).toBe('18:00')
  })

  it('refreshes the current day while mounted and stops its clock on unmount', async () => {
    vi.useFakeTimers()
    let instant = new Date(2026, 0, 31, 23, 59, 59)
    const wrapper = render()
    await wrapper.setProps({ now: () => instant })
    instant = new Date(2026, 1, 1, 0, 0, 0)
    await vi.advanceTimersByTimeAsync(1000)
    expect(wrapper.findComponent(CalendarPicker).props('min')).toBe('2026-02-02')
    wrapper.unmount()
    expect(vi.getTimerCount()).toBe(0)
  })
})
