import { enableAutoUnmount, mount } from '@vue/test-utils'
import { afterEach, expect, it } from 'vitest'
import { nextTick } from 'vue'
import CategorySelect from './CategorySelect.vue'
import type { Category } from './api'

enableAutoUnmount(afterEach)

const categories: Category[] = [
  { code: 'CAFE', name: 'Кофе' },
  { code: 'CINEMA', name: 'Кино' },
  { code: 'QUEST', name: 'Квесты' },
]

it('открывает список, выбирает категорию и показывает её на кнопке', async () => {
  const wrapper = mount(CategorySelect, {
    props: { modelValue: '', categories },
    attachTo: document.body,
  })
  expect(wrapper.get('.category-toggle').text()).toContain('Выберите категорию')
  await wrapper.get('.category-toggle').trigger('click')
  expect(wrapper.find('.category-panel').exists()).toBe(true)
  const options = wrapper.findAll('.category-panel button')
  expect(options).toHaveLength(3)
  await options[1]!.trigger('click')
  expect(wrapper.emitted('update:modelValue')).toEqual([['CINEMA']])
  expect(wrapper.find('.category-panel').exists()).toBe(false)
})

it('подсвечивает выбранную категорию при повторном открытии', async () => {
  const wrapper = mount(CategorySelect, {
    props: { modelValue: 'QUEST', categories },
    attachTo: document.body,
  })
  expect(wrapper.get('.category-toggle').text()).toContain('Квесты')
  await wrapper.get('.category-toggle').trigger('click')
  const chosen = wrapper.get('.category-panel button.chosen')
  expect(chosen.text()).toBe('Квесты')
  expect(chosen.attributes('aria-selected')).toBe('true')
})

it('руководит списком стрелками и выбирает клавишей Enter', async () => {
  const wrapper = mount(CategorySelect, {
    props: { modelValue: '', categories },
    attachTo: document.body,
  })
  const trigger = wrapper.get('.category-toggle')
  await trigger.trigger('keydown', { key: 'ArrowDown' })
  expect(wrapper.find('.category-panel').exists()).toBe(true)
  expect(wrapper.get('.category-panel button.active').text()).toBe('Кофе')
  await trigger.trigger('keydown', { key: 'ArrowDown' })
  expect(wrapper.get('.category-panel button.active').text()).toBe('Кино')
  await trigger.trigger('keydown', { key: 'Enter' })
  expect(wrapper.emitted('update:modelValue')).toEqual([['CINEMA']])
  expect(wrapper.find('.category-panel').exists()).toBe(false)
})

it('закрывается по Escape и по клику вне', async () => {
  const wrapper = mount(CategorySelect, {
    props: { modelValue: '', categories },
    attachTo: document.body,
  })
  await wrapper.get('.category-toggle').trigger('click')
  await wrapper.get('.category-toggle').trigger('keydown', { key: 'Escape' })
  expect(wrapper.find('.category-panel').exists()).toBe(false)
  await wrapper.get('.category-toggle').trigger('click')
  expect(wrapper.find('.category-panel').exists()).toBe(true)
  document.dispatchEvent(new Event('pointerdown'))
  await nextTick()
  expect(wrapper.find('.category-panel').exists()).toBe(false)
})

it('не открывает список в заблокированном состоянии', async () => {
  const wrapper = mount(CategorySelect, {
    props: { modelValue: '', categories, disabled: true },
  })
  await wrapper.get('.category-toggle').trigger('click')
  expect(wrapper.find('.category-panel').exists()).toBe(false)
})
