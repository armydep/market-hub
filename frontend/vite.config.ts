/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      // The backend's servlet context-path is already /api, so this passes
      // straight through with no rewrite. The proxy is required rather than
      // merely convenient: the backend configures no CORS, so a direct
      // cross-origin call from the dev server would be blocked by the browser.
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: true,
  },
})
