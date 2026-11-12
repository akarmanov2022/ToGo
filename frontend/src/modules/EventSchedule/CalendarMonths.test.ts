import { enableAutoUnmount, mount } from '@vue/test-utils'
import { afterEach, expect, it } from 'vitest'
import { nextTick } from 'vue'
import CalendarPicker from './CalendarPicker.vue'

enableAutoUnmount(afterEach)

it('листает месяцы в пределах min/max и блокирует выход за границы', async () => {
  const wrapper = mount(CalendarPicker, {
    props: { modelValue: '', min: '2026-11-11', max: '2026-12-10' },
    attachTo: document.body,
  })
  await wrapper.get('.calendar-toggle').trigger('click')
  const [prev, next] = wrapper.findAll('.calendar-header button')
  expect(wrapper.get('.calendar-header strong').text()).toContain('ноябрь')
  expect(prev.attributes('disabled')).toBeDefined()
  expect(next.attributes('disabled')).toBeUndefined()
  await next.trigger('click')
  expect(wrapper.get('.calendar-header strong').text()).toContain('декабрь')
  expect(prev.attributes('disabled')).toBeUndefined()
  expect(next.attributes('disabled')).toBeDefined()
  await next.trigger('click')
  expect(wrapper.get('.calendar-header strong').text()).toContain('декабрь')
  expect(wrapper.get('[aria-label="2026-12-10"]').attributes('disabled')).toBeUndefined()
  expect(wrapper.get('[aria-label="2026-12-11"]').attributes('disabled')).toBeDefined()
  await prev.trigger('click')
  expect(wrapper.get('.calendar-header strong').text()).toContain('ноябрь')
  await prev.trigger('click')
  expect(wrapper.get('.calendar-header strong').text()).toContain('ноябрь')
  expect(prev.attributes('disabled')).toBeDefined()
})

it('не закрывает поповер, когда вебвью теряет фокус без relatedTarget при тапе по стрелке', async () => {
  const wrapper = mount(CalendarPicker, {
    props: { modelValue: '', min: '2026-11-11', max: '2026-12-10' },
    attachTo: document.body,
  })
  const toggle = wrapper.get('.calendar-toggle')
  await toggle.trigger('click')
  ;(toggle.element as HTMLElement).focus()
  const next = wrapper.findAll('.calendar-header button')[1]
  next.element.dispatchEvent(new FocusEvent('focusout', { bubbles: true, relatedTarget: null }))
  await nextTick()
  expect(wrapper.find('.calendar-panel').exists()).toBe(true)
  await next.trigger('click')
  expect(wrapper.get('.calendar-header strong').text()).toContain('декабрь')
})
