/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import type { ServerResponse } from 'node:http'
import { defineConfig, loadEnv } from 'vite'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const proxyTarget = env.STOCKGUARD_PROXY_TARGET || 'http://localhost:8181'

  return {
    plugins: [react()],
    server: {
      port: 5173,
      proxy: {
        '/api': {
          target: proxyTarget,
          changeOrigin: true,
          configure(proxy) {
            // Si Karaf no responde, se devuelve 502 para distinguir la falta de
            // conexión de un error 500 emitido por la propia API.
            proxy.on('error', (_error, _request, response) => {
              const res = response as ServerResponse
              if (res && 'writeHead' in res && !res.headersSent) {
                res.writeHead(502, { 'Content-Type': 'text/plain' })
                res.end('Bad Gateway')
              }
            })
          },
        },
      },
    },
    test: {
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
      css: false,
    },
  }
})
