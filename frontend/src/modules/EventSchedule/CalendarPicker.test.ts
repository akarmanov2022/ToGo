import { enableAutoUnmount, mount } from '@vue/test-utils'
import { afterEach, expect, it } from 'vitest'
import { nextTick } from 'vue'
import CalendarPicker from './CalendarPicker.vue'

enableAutoUnmount(afterEach)

it('показывает выбранную дату в единственном кастомном поле и запрещает даты вне диапазона', async () => {
  const wrapper = mount(CalendarPicker, {
    props: { modelValue: '', min: '2026-02-01', max: '2026-02-28' },
  })
  expect(wrapper.get('.calendar-toggle').text()).toBe('Выбрать дату')
  expect(wrapper.find('input[type=date]').exists()).toBe(false)
  await wrapper.get('.calendar-toggle').trigger('click')
  expect(wrapper.get('[aria-label="2026-01-31"]').attributes('disabled')).toBeDefined()
  expect(wrapper.get('[aria-label="2026-03-01"]').attributes('disabled')).toBeDefined()
  await wrapper.get('[aria-label="2026-02-11"]').trigger('click')
  expect(wrapper.emitted('update:modelValue')).toEqual([['2026-02-11']])
  await wrapper.setProps({ modelValue: '2026-02-11' })
  expect(wrapper.get('.calendar-toggle').text()).toBe('11.02.2026')
  expect(wrapper.find('.calendar-panel').exists()).toBe(false)
})

it('позволяет очистить дату, не подставляя текущий день', async () => {
  const wrapper = mount(CalendarPicker, { props: { modelValue: '2026-02-11', min: '2026-02-01' } })
  await wrapper.get('.calendar-toggle').trigger('click')
  await wrapper.get('.calendar-clear').trigger('click')
  expect(wrapper.emitted('update:modelValue')).toEqual([['']])
})

it('раскрывает панель поверх вёрстки и закрывает её по клику вне поля', async () => {
  const wrapper = mount(CalendarPicker, { props: { modelValue: '', min: '2026-02-01' } })
  await wrapper.get('.calendar-toggle').trigger('click')
  expect(wrapper.find('.calendar-panel').exists()).toBe(true)
  document.dispatchEvent(new Event('pointerdown'))
  await nextTick()
  expect(wrapper.find('.calendar-panel').exists()).toBe(false)
})
