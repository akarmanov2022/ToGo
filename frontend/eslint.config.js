import js from '@eslint/js'
import prettier from 'eslint-config-prettier'
import vue from 'eslint-plugin-vue'
import globals from 'globals'
import tseslint from 'typescript-eslint'
import { fileURLToPath } from 'node:url'
import { configFromProject } from '@o-feod/eslint-structure-plugin'

export default tseslint.config(
  {
    ignores: [
      'dist/**',
      'node_modules/**',
      'src/common/api/schema.ts',
      'public/mockServiceWorker.js',
    ],
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  ...vue.configs['flat/recommended'],
  {
    files: ['**/*.vue'],
    languageOptions: { parserOptions: { parser: tseslint.parser } },
  },
  { languageOptions: { globals: { ...globals.browser, ...globals.node } } },
  {
    ...configFromProject({
      rootDir: fileURLToPath(new URL('.', import.meta.url)),
      preset: 'strict',
    }),
    files: ['src/**/*.{ts,vue}'],
  },
  prettier,
)
