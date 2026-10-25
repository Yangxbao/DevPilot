import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发环境代理 /api 到 Java 后端，避免跨域配置分散
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    include: ['tests/**/*.test.ts'],
  },
})
