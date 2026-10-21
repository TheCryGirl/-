import tailwindcss from '@tailwindcss/vite'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue(), tailwindcss()],
  server: {
    port: 5173,
    proxy: {
      // 开发期由 Vite 代理转发到网关（9000），由网关统一鉴权与路由，避免浏览器跨域
      '/api': {
        target: 'http://localhost:9000',
        changeOrigin: true,
      },
      '/images': {
        target: 'http://localhost:9000',
        changeOrigin: true,
      },
    },
  },
})
