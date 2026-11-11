// @vitest-environment node
import { fileURLToPath } from 'node:url'
import { ESLint } from 'eslint'
import { beforeAll, expect, it } from 'vitest'

const eslint = new ESLint({ cwd: fileURLToPath(new URL('../../', import.meta.url)) })

beforeAll(async () => {
  // Холодная загрузка Vue/TS-парсеров и графа проекта — отдельный setup, не время проверки правила.
  await eslint.lintText('export {}', { filePath: 'src/app/architecture-probe.ts' })
}, 30_000)

it.each([
  [
    'src/modules/Events/architecture-probe.ts',
    "export { getCurrentUser } from '@/modules/Auth/api'",
  ],
  ['src/common/composables/architecture-probe.ts', "export { useAuthStore } from '@/modules/Auth'"],
  ['src/modules/Events/architecture-probe.ts', "export { router } from '@/app/router'"],
])('FEOD запрещает нарушение границы: %s → %s', async (filePath, code) => {
  const results = await eslint.lintText(code, { filePath })
  expect(
    results
      .flatMap((result) => result.messages)
      .some((message) => message.ruleId === 'feod-structure/layer-imports'),
  ).toBe(true)
})

it('FEOD разрешает универсальный composable из common', async () => {
  const results = await eslint.lintText(
    "export { usePopover } from '@/common/composables/usePopover'",
    {
      filePath: 'src/modules/Events/architecture-probe.ts',
    },
  )
  expect(
    results
      .flatMap((result) => result.messages)
      .filter((message) => message.ruleId?.startsWith('feod-structure/')),
  ).toEqual([])
})
