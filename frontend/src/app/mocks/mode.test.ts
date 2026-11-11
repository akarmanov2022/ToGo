import { describe, expect, it } from 'vitest'
import { resolveMockMode } from './mode'

describe('resolveMockMode', () => {
  it('в отдельной демо-сборке разрешает моковый вход без Telegram', () => {
    expect(
      resolveMockMode({ dev: false, demo: true, envFlag: undefined, hasInitData: false }),
    ).toBe(true)
  })
  it('в демо-сборке использует настоящий вход внутри Telegram', () => {
    expect(resolveMockMode({ dev: false, demo: true, envFlag: undefined, hasInitData: true })).toBe(
      false,
    )
  })
  it('в Telegram без initData не открывает тестовый профиль', () => {
    expect(
      resolveMockMode({
        dev: false,
        demo: true,
        envFlag: undefined,
        hasInitData: false,
        inTelegram: true,
      }),
    ).toBe(false)
  })
  it('в dev без Telegram автоматически включает моки — моковый вход без авторизации', () => {
    expect(resolveMockMode({ dev: true, envFlag: undefined, hasInitData: false })).toBe(true)
  })

  it('в dev внутри Telegram работает с настоящим initData', () => {
    expect(resolveMockMode({ dev: true, envFlag: undefined, hasInitData: true })).toBe(false)
  })

  it('уважает явные значения флага', () => {
    expect(resolveMockMode({ dev: true, envFlag: 'true', hasInitData: false })).toBe(true)
    expect(resolveMockMode({ dev: true, envFlag: 'false', hasInitData: false })).toBe(false)
  })

  it('в продакшене моков никогда нет', () => {
    expect(resolveMockMode({ dev: false, envFlag: 'true', hasInitData: false })).toBe(false)
  })

  it.each([true, false])('не подменяет настоящий Telegram-вход в demo (dev=%s)', (dev) => {
    expect(resolveMockMode({ dev, demo: true, envFlag: 'true', hasInitData: true })).toBe(false)
  })

  it('не предлагает демо в Telegram без initData', () => {
    expect(
      resolveMockMode({
        dev: true,
        demo: true,
        envFlag: 'true',
        hasInitData: false,
        inTelegram: true,
      }),
    ).toBe(false)
  })

  it('позволяет явно отключить моки в demo-сборке', () => {
    expect(resolveMockMode({ dev: false, demo: true, envFlag: 'false', hasInitData: false })).toBe(
      false,
    )
  })
})
