import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import AuthGate from './AuthGate.vue'
import { useAuthStore } from './session'

beforeEach(() => setActivePinia(createPinia()))
describe('Auth gate', () => {
  it('hides protected content outside Telegram and has no login form', () => {
    useAuthStore().status = 'outside'
    const wrapper = mount(AuthGate, {
      props: { mockMode: false },
      slots: { default: 'protected-content' },
    })
    expect(wrapper.text()).toContain('Откройте ToGo в Telegram')
    expect(wrapper.text()).not.toContain('protected-content')
    expect(wrapper.find('form').exists()).toBe(false)
  })
  it('shows protected content only after login with explicit mock banner', () => {
    useAuthStore().status = 'authenticated'
    const wrapper = mount(AuthGate, {
      props: { mockMode: true },
      slots: { default: 'protected-content' },
    })
    expect(wrapper.text()).toContain('protected-content')
    expect(wrapper.text()).toContain('Локальный MSW-режим')
  })
  it('offers reopen instead of retry on expired session', () => {
    useAuthStore().status = 'expired'
    const wrapper = mount(AuthGate, { props: { mockMode: false } })
    expect(wrapper.text()).toContain('откройте заново')
    expect(wrapper.find('button').exists()).toBe(false)
  })
})
