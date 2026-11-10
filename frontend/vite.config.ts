import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'
import { readFile } from 'node:fs/promises'

export default defineConfig({
  plugins: [
    vue(),
    {
      name: 'local-msw-worker',
      apply: 'serve',
      configureServer(server) {
        server.middlewares.use('/mockServiceWorker.js', async (_request, response, next) => {
          try {
            const content = await readFile(
              new URL('./node_modules/msw/lib/mockServiceWorker.js', import.meta.url),
            )
            response.setHeader('Content-Type', 'application/javascript')
            response.setHeader('Cache-Control', 'no-store')
            response.end(content)
          } catch (error) {
            next(error)
          }
        })
      },
    },
  ],
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  server: { proxy: { '/api': 'http://localhost:8080' } },
})
