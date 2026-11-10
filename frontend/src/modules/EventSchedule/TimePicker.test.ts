import { enableAutoUnmount, mount } from '@vue/test-utils'
import { afterEach, expect, it } from 'vitest'
import TimePicker from './TimePicker.vue'

enableAutoUnmount(afterEach)

function render(value = '') {
  const wrapper = mount(TimePicker, {
    props: {
      modelValue: value,
      'onUpdate:modelValue': (modelValue: string) => {
        void wrapper.setProps({ modelValue })
      },
    },
  })
  return wrapper
}

it('открывает два кастомных переключателя без нативного пикера, ползунка и автозаполнения', async () => {
  const wrapper = render()
  expect(wrapper.get('.time-trigger').text()).toBe('Выбрать время')
  expect(wrapper.find('input').exists()).toBe(false)
  await wrapper.get('.time-trigger').trigger('click')
  const fields = wrapper.findAll('[role=spinbutton]')
  expect(fields).toHaveLength(2)
  expect(fields[0]!.attributes('aria-valuemax')).toBe('23')
  expect(fields[1]!.attributes('aria-valuemax')).toBe('55')
  expect(wrapper.find('input[type=time], input[type=range]').exists()).toBe(false)
  expect(wrapper.emitted('update:modelValue')).toBeUndefined()
})

it('позволяет ввести часы и минуты отдельно, сохраняя их только после явного выбора', async () => {
  const wrapper = render()
  await wrapper.get('.time-trigger').trigger('click')
  const fields = wrapper.findAll('.time-number')
  await fields[0]!.setValue('19')
  await fields[1]!.setValue('37')
  expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  await wrapper.get('.time-apply').trigger('click')
  expect(wrapper.props('modelValue')).toBe('19:35')
  expect(wrapper.get('.time-trigger').text()).toBe('19:35')
  expect(wrapper.find('input').exists()).toBe(false)
})

it('позволяет выбрать границы суток и очистить время', async () => {
  const wrapper = render()
  for (const [hours, minutes, value] of [
    ['00', '00', '00:00'],
    ['23', '55', '23:55'],
  ]) {
    await wrapper.get('.time-trigger').trigger('click')
    const fields = wrapper.findAll('.time-number')
    await fields[0]!.setValue(hours)
    await fields[1]!.setValue(minutes)
    await wrapper.get('.time-apply').trigger('click')
    expect(wrapper.props('modelValue')).toBe(value)
  }
  await wrapper.get('.time-trigger').trigger('click')
  await wrapper.get('.time-clear').trigger('click')
  expect(wrapper.props('modelValue')).toBe('')
})

it('поддерживает стрелки, клавиатуру и циклическое переключение часов и минут', async () => {
  const wrapper = render('23:55')
  await wrapper.get('.time-trigger').trigger('click')
  await wrapper.get('[aria-label="Увеличить часы"]').trigger('click')
  await wrapper.findAll('.time-number')[1]!.trigger('keydown', { key: 'ArrowUp' })
  await wrapper.get('.time-apply').trigger('click')
  expect(wrapper.props('modelValue')).toBe('00:00')
  await wrapper.get('.time-trigger').trigger('click')
  await wrapper.get('[aria-label="Уменьшить часы"]').trigger('click')
  await wrapper.findAll('.time-number')[1]!.trigger('keydown', { key: 'ArrowDown' })
  await wrapper.get('.time-apply').trigger('click')
  expect(wrapper.props('modelValue')).toBe('23:55')
})

it('закрывается по Escape без изменения и синхронизирует внешнее значение', async () => {
  const wrapper = render('15:30')
  await wrapper.get('.time-trigger').trigger('click')
  await wrapper.findAll('.time-number')[0]!.setValue('20')
  await wrapper.trigger('keydown', { key: 'Escape' })
  expect(wrapper.props('modelValue')).toBe('15:30')
  expect(wrapper.find('input').exists()).toBe(false)
  await wrapper.setProps({ modelValue: '09:10' })
  expect(wrapper.get('.time-trigger').text()).toBe('09:10')
  await wrapper.get('.time-trigger').trigger('click')
  expect(wrapper.findAll<HTMLInputElement>('.time-number')[0]!.element.value).toBe('09')
})
